package br.com.vendafacil.commerce;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import br.com.vendafacil.inventory.InventoryService;
import br.com.vendafacil.shared.audit.AuditService;
import br.com.vendafacil.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CashService {
  public record Register(UUID id, String name) {}

  public record Session(
      UUID id,
      UUID registerId,
      String registerName,
      UUID operatorId,
      String operator,
      String status,
      BigDecimal initial,
      OffsetDateTime openedAt,
      OffsetDateTime closedAt) {}

  public record Count(BigDecimal counted, BigDecimal expected) {
    public BigDecimal difference() {
      return counted.subtract(expected);
    }
  }

  public record Event(
      String type,
      BigDecimal amount,
      String reason,
      String actor,
      String approver,
      String approvalReason,
      OffsetDateTime createdAt) {}

  public record Detail(Session session, Count count, BigDecimal expected, List<Event> events) {}

  public record Policy(
      boolean blind,
      boolean withdrawalApproval,
      BigDecimal differenceLimit,
      BigDecimal withdrawalLimit,
      BigDecimal maximumDiscount) {}

  public record Open(UUID operationId, UUID registerId, String initial) {}

  public record Move(
      UUID operationId,
      String type,
      String amount,
      String reason,
      ApprovalService.Credentials approval) {}

  public record Declare(String counted) {}

  public record Close(UUID operationId, String reason, ApprovalService.Credentials approval) {}

  public record PolicyInput(
      boolean blind,
      boolean withdrawalApproval,
      String differenceLimit,
      String withdrawalLimit,
      String maximumDiscount) {}

  private final JdbcTemplate jdbc;
  private final AccessService access;
  private final TenantContext tenant;
  private final AuditService audit;
  private final ApprovalService approvals;

  public CashService(
      JdbcTemplate jdbc,
      AccessService access,
      TenantContext tenant,
      AuditService audit,
      ApprovalService approvals) {
    this.jdbc = jdbc;
    this.access = access;
    this.tenant = tenant;
    this.audit = audit;
    this.approvals = approvals;
  }

  public Policy policy() {
    access.activeStore();
    return jdbc.queryForObject(
        "SELECT * FROM tenant_policies WHERE tenant_id=?",
        (rs, n) ->
            new Policy(
                rs.getBoolean("blind_cash_close"),
                rs.getBoolean("cash_withdraw_requires_approval"),
                rs.getBigDecimal("cash_difference_limit"),
                rs.getBigDecimal("cash_withdraw_limit"),
                rs.getBigDecimal("maximum_discount")),
        tenant.id());
  }

  @Transactional
  public void policy(PolicyInput input) {
    access.require(Permission.USER_MANAGE);
    var limit = nonnegative(input.differenceLimit());
    var withdrawalLimit = nonnegative(input.withdrawalLimit());
    if (withdrawalLimit.signum() == 0)
      throw new IllegalArgumentException("O limite de sangria deve ser maior que zero.");
    var discount = nonnegative(input.maximumDiscount());
    if (discount.compareTo(new BigDecimal("20")) > 0)
      throw new IllegalArgumentException("O teto de desconto desta etapa é de 20%.");
    jdbc.update(
        "UPDATE tenant_policies SET blind_cash_close=?,cash_withdraw_requires_approval=?,cash_difference_limit=?,cash_withdraw_limit=?,maximum_discount=? WHERE tenant_id=?",
        input.blind(),
        input.withdrawalApproval(),
        limit,
        withdrawalLimit,
        discount,
        tenant.id());
    audit.record(tenant.id(), store(), actor(), "CASH_POLICY_CHANGED", tenant.id());
  }

  public List<Register> registers() {
    access.require(Permission.CASH_OPEN);
    return jdbc.query(
        "SELECT id,name FROM cash_registers WHERE tenant_id=? AND store_id=? ORDER BY name",
        (rs, n) -> new Register(rs.getObject(1, UUID.class), rs.getString(2)),
        tenant.id(),
        store());
  }

  @Transactional
  public UUID createRegister(String name) {
    access.require(Permission.USER_MANAGE);
    var text = name == null ? "" : name.trim();
    if (text.isEmpty() || text.length() > 80 || text.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Informe o nome do caixa com até 80 caracteres.");
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO cash_registers(id,tenant_id,store_id,name) VALUES(?,?,?,?)",
        id,
        tenant.id(),
        store(),
        text);
    audit.record(tenant.id(), store(), actor(), "CASH_REGISTER_CREATED", id);
    return id;
  }

  public List<Session> sessions() {
    access.require(Permission.CASH_OPEN);
    boolean all = access.permissions().contains(Permission.REPORT_VIEW);
    return jdbc.query(
        SELECT
            + " WHERE s.tenant_id=? AND s.store_id=? AND (? OR s.operator_id=?) ORDER BY s.opened_at DESC LIMIT 30",
        this::map,
        tenant.id(),
        store(),
        all,
        actor());
  }

  public Session current() {
    access.require(Permission.CASH_OPEN);
    return jdbc
        .query(
            SELECT
                + " WHERE s.tenant_id=? AND s.store_id=? AND s.operator_id=? AND s.status<>'CLOSED'",
            this::map,
            tenant.id(),
            store(),
            actor())
        .stream()
        .findFirst()
        .orElse(null);
  }

  public Detail detail(UUID id) {
    var s = find(id, false);
    requireOwnerOrReport(s);
    var count = count(id);
    var expected =
        count == null && !policy().blind() ? expected(id) : count == null ? null : count.expected();
    // Even the API cannot expose movements that would reconstruct a blind balance.
    var events =
        expected == null
            ? List.<Event>of()
            : jdbc.query(
                """
        SELECT e.*,u.name AS actor,a.name AS approver FROM cash_events e JOIN users u ON u.id=e.actor_id
        LEFT JOIN users a ON a.id=e.approver_id WHERE e.tenant_id=? AND e.store_id=? AND e.session_id=? ORDER BY e.created_at,e.id
        """,
                (rs, n) ->
                    new Event(
                        rs.getString("type"),
                        rs.getBigDecimal("amount"),
                        rs.getString("reason"),
                        rs.getString("actor"),
                        rs.getString("approver"),
                        rs.getString("approval_reason"),
                        rs.getObject("created_at", OffsetDateTime.class)),
                tenant.id(),
                store(),
                id);
    return new Detail(s, count, expected, events);
  }

  @Transactional
  public UUID open(Open input) {
    access.require(Permission.CASH_OPEN);
    key(input.operationId());
    var amount = nonnegative(input.initial());
    if (input.registerId() == null) throw new IllegalArgumentException("Escolha um caixa.");
    if (jdbc.query(
            "SELECT id FROM cash_registers WHERE tenant_id=? AND store_id=? AND id=? FOR UPDATE",
            (rs, n) -> rs.getObject(1, UUID.class),
            tenant.id(),
            store(),
            input.registerId())
        .isEmpty()) throw new AccessDeniedException("Caixa indisponível.");
    var previous =
        jdbc.query(
            SELECT + " WHERE s.tenant_id=? AND s.store_id=? AND s.operation_id=?",
            this::map,
            tenant.id(),
            store(),
            input.operationId());
    if (!previous.isEmpty()) {
      var p = previous.getFirst();
      if (!p.operatorId().equals(actor())
          || !p.registerId().equals(input.registerId())
          || p.initial().compareTo(amount) != 0)
        throw new IllegalArgumentException("Chave de abertura já usada com outros dados.");
      return p.id();
    }
    if (current() != null
        || Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM cash_sessions WHERE tenant_id=? AND store_id=? AND register_id=? AND status<>'CLOSED')",
                Boolean.class,
                tenant.id(),
                store(),
                input.registerId())))
      throw new IllegalArgumentException(
          "Este caixa ou operador já tem uma sessão em andamento. Feche a sessão antes de abrir outra.");
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO cash_sessions(id,tenant_id,store_id,register_id,operator_id,operation_id,initial_amount,status) VALUES(?,?,?,?,?,?,?,'OPEN')",
        id,
        tenant.id(),
        store(),
        input.registerId(),
        actor(),
        input.operationId(),
        amount);
    event(
        id,
        input.operationId(),
        "OPEN",
        amount,
        "Fundo inicial",
        null,
        null,
        InventoryService.hash(input.registerId() + "|" + amount));
    audit.record(tenant.id(), store(), actor(), "CASH_OPENED", id);
    return id;
  }

  @Transactional
  public void move(UUID id, Move input) {
    boolean withdraw = "WITHDRAW".equals(input.type());
    if (!withdraw && !"SUPPLY".equals(input.type()))
      throw new IllegalArgumentException("Movimento inválido.");
    access.require(withdraw ? Permission.CASH_WITHDRAW : Permission.CASH_REINFORCE);
    key(input.operationId());
    var s = find(id, true);
    owner(s);
    var amount = nonnegative(input.amount());
    if (amount.signum() == 0)
      throw new IllegalArgumentException("Informe um valor maior que zero.");
    var reason = ApprovalService.reason(input.reason());
    var hash = InventoryService.hash(id + "|" + input.type() + "|" + amount + "|" + reason);
    if (replay(input.operationId(), hash)) return;
    open(s);
    if (withdraw && amount.compareTo(policy().withdrawalLimit()) > 0)
      throw new IllegalArgumentException(
          "O valor ultrapassa o limite de sangria por operação da empresa.");
    UUID approver =
        withdraw && policy().withdrawalApproval()
            ? approvals.require(Permission.CASH_WITHDRAW_APPROVE, input.approval(), null)
            : null;
    if (withdraw && expected(id).compareTo(amount) < 0)
      throw new IllegalArgumentException("Não há dinheiro suficiente para esta sangria.");
    event(
        id,
        input.operationId(),
        input.type(),
        withdraw ? amount.negate() : amount,
        reason,
        approver,
        approver == null ? null : input.approval().reason().trim(),
        hash);
    audit.record(tenant.id(), store(), actor(), withdraw ? "CASH_WITHDRAWN" : "CASH_SUPPLIED", id);
  }

  @Transactional
  public void declare(UUID id, Declare input) {
    access.require(Permission.CASH_CLOSE);
    var s = find(id, true);
    owner(s);
    var amount = nonnegative(input.counted());
    var previous = count(id);
    if (previous != null) {
      if (previous.counted().compareTo(amount) != 0)
        throw new IllegalArgumentException("A contagem já foi declarada e não pode ser alterada.");
      return;
    }
    open(s);
    jdbc.update(
        "INSERT INTO cash_counts(id,tenant_id,store_id,session_id,counted,expected,actor_id) VALUES(?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        tenant.id(),
        store(),
        id,
        amount,
        expected(id),
        actor());
    jdbc.update(
        "UPDATE cash_sessions SET status='COUNTED' WHERE tenant_id=? AND store_id=? AND id=?",
        tenant.id(),
        store(),
        id);
    audit.record(tenant.id(), store(), actor(), "CASH_COUNT_DECLARED", id);
  }

  @Transactional
  public void close(UUID id, Close input) {
    access.require(Permission.CASH_CLOSE);
    key(input.operationId());
    var s = find(id, true);
    owner(s);
    var reason = ApprovalService.reason(input.reason());
    var hash = InventoryService.hash(id + "|CLOSE|" + reason);
    if (replay(input.operationId(), hash)) return;
    if (!"COUNTED".equals(s.status()))
      throw new IllegalArgumentException("Declare a contagem antes de fechar o caixa.");
    var difference = count(id).difference().abs();
    UUID approver =
        difference.compareTo(policy().differenceLimit()) > 0
            ? approvals.require(Permission.CASH_DIFFERENCE_APPROVE, input.approval(), null)
            : null;
    event(
        id,
        input.operationId(),
        "CLOSE",
        BigDecimal.ZERO,
        reason,
        approver,
        approver == null ? null : input.approval().reason().trim(),
        hash);
    jdbc.update(
        "UPDATE cash_sessions SET status='CLOSED',closed_at=clock_timestamp() WHERE tenant_id=? AND store_id=? AND id=?",
        tenant.id(),
        store(),
        id);
    audit.record(tenant.id(), store(), actor(), "CASH_CLOSED", id);
  }

  Session checkoutSession(UUID id, boolean lock) {
    var s = find(id, lock);
    owner(s);
    return s;
  }

  static void open(Session s) {
    if (!"OPEN".equals(s.status()))
      throw new IllegalArgumentException("O caixa não está aberto para operações.");
  }

  void event(
      UUID session,
      UUID operation,
      String type,
      BigDecimal amount,
      String reason,
      UUID approver,
      String approvalReason,
      String hash) {
    jdbc.update(
        "INSERT INTO cash_events(id,tenant_id,store_id,session_id,operation_id,request_hash,type,amount,reason,actor_id,approver_id,approval_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        tenant.id(),
        store(),
        session,
        operation,
        hash,
        type,
        amount,
        reason,
        actor(),
        approver,
        approvalReason);
  }

  private boolean replay(UUID operation, String hash) {
    var rows =
        jdbc.queryForList(
            "SELECT request_hash,actor_id FROM cash_events WHERE tenant_id=? AND store_id=? AND operation_id=?",
            tenant.id(),
            store(),
            operation);
    if (rows.isEmpty()) return false;
    if (!hash.equals(rows.getFirst().get("request_hash"))
        || !actor().equals(rows.getFirst().get("actor_id")))
      throw new IllegalArgumentException("Esta chave de operação já foi usada com outros dados.");
    return true;
  }

  private Count count(UUID id) {
    return jdbc
        .query(
            "SELECT counted,expected FROM cash_counts WHERE tenant_id=? AND store_id=? AND session_id=?",
            (rs, n) -> new Count(rs.getBigDecimal(1), rs.getBigDecimal(2)),
            tenant.id(),
            store(),
            id)
        .stream()
        .findFirst()
        .orElse(null);
  }

  private BigDecimal expected(UUID id) {
    return jdbc.queryForObject(
        "SELECT coalesce(sum(amount),0) FROM cash_events WHERE tenant_id=? AND store_id=? AND session_id=?",
        BigDecimal.class,
        tenant.id(),
        store(),
        id);
  }

  private Session find(UUID id, boolean lock) {
    return jdbc
        .query(
            SELECT
                + " WHERE s.tenant_id=? AND s.store_id=? AND s.id=?"
                + (lock ? " FOR UPDATE OF s" : ""),
            this::map,
            tenant.id(),
            store(),
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new AccessDeniedException("Sessão indisponível."));
  }

  private void owner(Session s) {
    if (!s.operatorId().equals(actor()))
      throw new AccessDeniedException("Somente o operador desta sessão pode movimentar o caixa.");
  }

  private void requireOwnerOrReport(Session s) {
    if (!s.operatorId().equals(actor())) access.require(Permission.REPORT_VIEW);
    else access.require(Permission.CASH_OPEN);
  }

  private UUID store() {
    return access.activeStore().getId();
  }

  private UUID actor() {
    return access.principal().userId();
  }

  static void key(UUID value) {
    if (value == null)
      throw new IllegalArgumentException("Atualize a página para gerar uma chave de operação.");
  }

  static BigDecimal nonnegative(String value) {
    var amount = SaleMath.money(value);
    if (amount.signum() < 0 || amount.compareTo(new BigDecimal("999999999.99")) > 0)
      throw new IllegalArgumentException("Valor deve estar entre zero e R$ 999.999.999,99.");
    return amount;
  }

  private static final String SELECT =
      "SELECT s.*,r.name AS register_name,u.name AS operator_name FROM cash_sessions s JOIN cash_registers r ON r.tenant_id=s.tenant_id AND r.store_id=s.store_id AND r.id=s.register_id JOIN users u ON u.id=s.operator_id";

  private Session map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
    return new Session(
        rs.getObject("id", UUID.class),
        rs.getObject("register_id", UUID.class),
        rs.getString("register_name"),
        rs.getObject("operator_id", UUID.class),
        rs.getString("operator_name"),
        rs.getString("status"),
        rs.getBigDecimal("initial_amount"),
        rs.getObject("opened_at", OffsetDateTime.class),
        rs.getObject("closed_at", OffsetDateTime.class));
  }
}
