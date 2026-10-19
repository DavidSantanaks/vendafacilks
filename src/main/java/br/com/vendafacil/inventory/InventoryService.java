package br.com.vendafacil.inventory;

import br.com.vendafacil.catalog.CatalogRepository;
import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import br.com.vendafacil.shared.audit.AuditService;
import br.com.vendafacil.shared.validation.DataPage;
import br.com.vendafacil.shared.validation.DecimalValues;
import br.com.vendafacil.shared.validation.FieldValidationException;
import br.com.vendafacil.tenant.TenantContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
  public record Movement(
      UUID id,
      UUID productId,
      String product,
      String unit,
      String type,
      BigDecimal quantity,
      BigDecimal balanceAfter,
      String reason,
      String actor,
      String approver,
      String approvalReason,
      Instant createdAt) {}

  private final JdbcTemplate jdbc;
  private final CatalogRepository products;
  private final AccessService access;
  private final TenantContext tenant;
  private final AuditService audit;
  private final StockApprovalService approvals;

  public InventoryService(
      JdbcTemplate jdbc,
      CatalogRepository products,
      AccessService access,
      TenantContext tenant,
      AuditService audit,
      StockApprovalService approvals) {
    this.jdbc = jdbc;
    this.products = products;
    this.access = access;
    this.tenant = tenant;
    this.audit = audit;
    this.approvals = approvals;
  }

  @Transactional(readOnly = true)
  public DataPage<CatalogRepository.Product> balances(String q, boolean low, int page) {
    access.require(Permission.STOCK_READ);
    return products.list(
        tenant.id(), access.activeStore().getId(), q, "all", null, low, page, true);
  }

  @Transactional(readOnly = true)
  public CatalogRepository.Product product(UUID id) {
    access.require(Permission.STOCK_READ);
    return products.find(tenant.id(), access.activeStore().getId(), id, true);
  }

  @Transactional
  public UUID move(UUID productId, StockForm form) {
    access.require(Permission.STOCK_ADJUST);
    var storeId = access.activeStore().getId();
    if (form.operationId() == null) {
      throw new FieldValidationException(
          "quantity", "Reabra o formulário para iniciar uma operação válida.");
    }
    if (!List.of("ENTRY", "ADJUSTMENT").contains(form.type() == null ? "" : form.type())) {
      throw new FieldValidationException("type", "Selecione entrada ou ajuste.");
    }
    var reason = form.reason() == null ? "" : form.reason().trim();
    if (reason.length() < 5
        || reason.length() > 240
        || reason.chars().anyMatch(Character::isISOControl)) {
      throw new FieldValidationException(
          "reason", "Informe um motivo com 5 a 240 caracteres, sem quebras de linha.");
    }
    products.lockProduct(tenant.id(), productId);
    var product = products.find(tenant.id(), storeId, productId, true);
    var delta = DecimalValues.quantity("quantity", form.quantity(), product.unit());
    if (delta.signum() == 0 || ("ENTRY".equals(form.type()) && delta.signum() < 0)) {
      throw new FieldValidationException(
          "quantity", "Use quantidade positiva na entrada e diferente de zero no ajuste.");
    }
    var hash = hash(productId + "|" + form.type() + "|" + delta.toPlainString() + "|" + reason);
    var previous =
        jdbc.queryForList(
            "SELECT id,request_hash,actor_id FROM stock_movements WHERE tenant_id=? AND store_id=? AND operation_id=?",
            tenant.id(),
            storeId,
            form.operationId());
    if (!previous.isEmpty()) {
      var row = previous.getFirst();
      if (!hash.equals(row.get("request_hash"))
          || !access.principal().userId().equals(row.get("actor_id"))) {
        throw new FieldValidationException(
            "quantity",
            "A identificação desta operação já foi usada com outros dados. Reabra o formulário.");
      }
      return (UUID) row.get("id");
    }
    if (!product.active()) {
      throw new FieldValidationException(
          "quantity", "Reative o produto antes de movimentar seu estoque.");
    }
    ensureBalance(storeId, productId);
    var current =
        jdbc.queryForObject(
            "SELECT quantity FROM stock_balances WHERE tenant_id=? AND store_id=? AND product_id=? FOR UPDATE",
            BigDecimal.class,
            tenant.id(),
            storeId,
            productId);
    var after = current.add(delta);
    if (after.abs().compareTo(new BigDecimal("9999999999999999.999")) > 0) {
      throw new FieldValidationException("quantity", "O saldo excede o limite permitido.");
    }
    UUID approver = after.signum() < 0 ? approvals.approve(form) : null;
    var id = UUID.randomUUID();
    jdbc.update(
        "UPDATE stock_balances SET quantity=?,version=version+1 WHERE tenant_id=? AND store_id=? AND product_id=?",
        after,
        tenant.id(),
        storeId,
        productId);
    jdbc.update(
        "INSERT INTO stock_movements(id,tenant_id,store_id,product_id,operation_id,request_hash,type,quantity,balance_after,reason,actor_id,approver_id,approval_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        tenant.id(),
        storeId,
        productId,
        form.operationId(),
        hash,
        form.type(),
        delta,
        after,
        reason,
        access.principal().userId(),
        approver,
        approver == null ? null : form.approvalReason().trim());
    audit.record(
        tenant.id(),
        storeId,
        access.principal().userId(),
        approver == null ? "STOCK_MOVED" : "NEGATIVE_STOCK_APPROVED",
        id);
    return id;
  }

  @Transactional
  public void minimum(UUID productId, String text, long version) {
    access.require(Permission.STOCK_ADJUST);
    var storeId = access.activeStore().getId();
    products.lockProduct(tenant.id(), productId);
    var product = products.find(tenant.id(), storeId, productId, true);
    BigDecimal value = null;
    if (text != null && !text.isBlank()) {
      value = DecimalValues.quantity("minimum", text, product.unit());
      if (value.signum() < 0) {
        throw new FieldValidationException("minimum", "O estoque mínimo não pode ser negativo.");
      }
    }
    ensureBalance(storeId, productId);
    if (product.stockVersion() != version) {
      throw new FieldValidationException(
          "minimum", "O estoque mudou. Atualize a página antes de salvar o mínimo.");
    }
    jdbc.update(
        "UPDATE stock_balances SET minimum_quantity=?,version=version+1 WHERE tenant_id=? AND store_id=? AND product_id=?",
        value,
        tenant.id(),
        storeId,
        productId);
    audit.record(
        tenant.id(), storeId, access.principal().userId(), "STOCK_MINIMUM_CHANGED", productId);
  }

  @Transactional(readOnly = true)
  public DataPage<Movement> movements(UUID productId, int page) {
    access.require(Permission.STOCK_READ);
    var storeId = access.activeStore().getId();
    if (productId != null) {
      products.find(tenant.id(), storeId, productId, true);
    }
    var condition =
        " WHERE m.tenant_id=? AND m.store_id=?" + (productId == null ? "" : " AND m.product_id=?");
    var params = new java.util.ArrayList<Object>(List.of(tenant.id(), storeId));
    if (productId != null) {
      params.add(productId);
    }
    var total =
        jdbc.queryForObject(
            "SELECT count(*) FROM stock_movements m" + condition, Long.class, params.toArray());
    int number = Math.max(0, Math.min(page, (int) Math.max(0, (total - 1) / 20)));
    params.add(20);
    params.add(number * 20);
    var items =
        jdbc.query(
            "SELECT m.*,p.name AS product_name,p.unit,u.name AS actor_name,a.name AS approver_name FROM stock_movements m JOIN products p ON p.tenant_id=m.tenant_id AND p.id=m.product_id JOIN users u ON u.id=m.actor_id LEFT JOIN users a ON a.id=m.approver_id"
                + condition
                + " ORDER BY m.created_at DESC,m.id LIMIT ? OFFSET ?",
            (rs, n) ->
                new Movement(
                    rs.getObject("id", UUID.class),
                    rs.getObject("product_id", UUID.class),
                    rs.getString("product_name"),
                    rs.getString("unit"),
                    rs.getString("type"),
                    rs.getBigDecimal("quantity"),
                    rs.getBigDecimal("balance_after"),
                    rs.getString("reason"),
                    rs.getString("actor_name"),
                    rs.getString("approver_name"),
                    rs.getString("approval_reason"),
                    rs.getTimestamp("created_at").toInstant()),
            params.toArray());
    return new DataPage<>(items, number, total, 20);
  }

  private void ensureBalance(UUID storeId, UUID productId) {
    jdbc.update(
        "INSERT INTO stock_balances(tenant_id,store_id,product_id) VALUES(?,?,?) ON CONFLICT DO NOTHING",
        tenant.id(),
        storeId,
        productId);
  }

  public static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
