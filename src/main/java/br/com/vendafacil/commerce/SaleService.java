package br.com.vendafacil.commerce;

import br.com.vendafacil.catalog.CatalogRepository;
import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import br.com.vendafacil.inventory.InventoryService;
import br.com.vendafacil.inventory.StockApprovalService;
import br.com.vendafacil.inventory.StockForm;
import br.com.vendafacil.shared.audit.AuditService;
import br.com.vendafacil.shared.config.AppProperties;
import br.com.vendafacil.shared.validation.DecimalValues;
import br.com.vendafacil.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SaleService {
  public record Item(
      UUID productId,
      String quantity,
      String unitPrice,
      long productVersion,
      SaleMath.Discount discount) {}

  public record Payment(String method, String amount) {}

  public record Checkout(
      UUID operationId,
      UUID sessionId,
      String customer,
      List<Item> items,
      SaleMath.Discount discount,
      List<Payment> payments,
      ApprovalService.Credentials discountApproval,
      ApprovalService.Credentials stockApproval) {}

  public record Search(
      UUID id,
      String sku,
      String name,
      String barcode,
      String unit,
      BigDecimal price,
      long version) {}

  public record Receipt(
      UUID id,
      String customer,
      String operator,
      String store,
      BigDecimal subtotal,
      BigDecimal discount,
      BigDecimal total,
      BigDecimal change,
      String commercialStatus,
      String fiscalStatus,
      String mode,
      OffsetDateTime createdAt,
      List<ReceiptItem> items,
      List<ReceiptPayment> payments) {}

  public record ReceiptItem(
      String sku,
      String name,
      String unit,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal subtotal,
      BigDecimal discount,
      BigDecimal total) {}

  public record ReceiptPayment(String method, BigDecimal amount) {}

  public record Quote(
      SaleMath.Totals totals,
      BigDecimal change,
      BigDecimal cashNet,
      boolean discountApprovalRequired,
      boolean negativeStockApprovalRequired,
      List<QuoteItem> items) {}

  public record QuoteItem(
      UUID productId, BigDecimal subtotal, BigDecimal discount, BigDecimal total) {}

  private record Prepared(
      List<Item> requests,
      List<CatalogRepository.Product> products,
      List<BigDecimal> quantities,
      SaleMath.Totals totals,
      List<ReceiptPayment> payments,
      BigDecimal change,
      BigDecimal cashNet,
      boolean discountApproval,
      boolean negative) {}

  private final JdbcTemplate jdbc;
  private final AccessService access;
  private final TenantContext tenant;
  private final CatalogRepository catalog;
  private final CashService cash;
  private final ApprovalService approvals;
  private final StockApprovalService stockApprovals;
  private final AuditService audit;
  private final AppProperties properties;
  private final Environment environment;

  public SaleService(
      JdbcTemplate jdbc,
      AccessService access,
      TenantContext tenant,
      CatalogRepository catalog,
      CashService cash,
      ApprovalService approvals,
      StockApprovalService stockApprovals,
      AuditService audit,
      AppProperties properties,
      Environment environment) {
    this.jdbc = jdbc;
    this.access = access;
    this.tenant = tenant;
    this.catalog = catalog;
    this.cash = cash;
    this.approvals = approvals;
    this.stockApprovals = stockApprovals;
    this.audit = audit;
    this.properties = properties;
    this.environment = environment;
  }

  public boolean demoSalesEnabled() {
    return properties.demoEnabled()
        && environment.acceptsProfiles(Profiles.of("local", "test"))
        && !environment.acceptsProfiles(Profiles.of("production", "staging"));
  }

  private void requireSale() {
    access.require(Permission.SALE_CREATE);
    if (!demoSalesEnabled())
      throw new IllegalArgumentException(
          "O PDV desta etapa está disponível apenas para demonstração. A operação em produção depende da integração fiscal.");
  }

  public List<Search> search(String q) {
    requireSale();
    return catalog.saleSearch(tenant.id(), store(), q, false).stream()
        .map(
            p ->
                new Search(
                    p.id(), p.sku(), p.name(), p.barcode(), p.unit(), p.price(), p.version()))
        .toList();
  }

  public Quote quote(Checkout input) {
    requireSale();
    validate(input);
    var s = cash.checkoutSession(input.sessionId(), false);
    CashService.open(s);
    var p = prepare(input, false);
    var items = new ArrayList<QuoteItem>();
    for (int i = 0; i < p.products().size(); i++) {
      var line = p.totals().lines().get(i);
      items.add(
          new QuoteItem(p.products().get(i).id(), line.gross(), line.discount(), line.total()));
    }
    return new Quote(
        p.totals(),
        p.change(),
        p.cashNet(),
        p.discountApproval(),
        p.negative(),
        List.copyOf(items));
  }

  @Transactional
  public UUID checkout(Checkout input) {
    requireSale();
    validate(input);
    CashService.key(input.operationId());
    var session = cash.checkoutSession(input.sessionId(), true);
    var hash = fingerprint(input);
    var prior =
        jdbc.queryForList(
            "SELECT id,request_hash,actor_id FROM sales WHERE tenant_id=? AND store_id=? AND operation_id=?",
            tenant.id(),
            store(),
            input.operationId());
    if (!prior.isEmpty()) {
      var row = prior.getFirst();
      if (!hash.equals(row.get("request_hash")) || !actor().equals(row.get("actor_id")))
        throw new IllegalArgumentException("A chave desta venda já foi usada com outros dados.");
      return (UUID) row.get("id");
    }
    CashService.open(session);
    if (input.payments().isEmpty())
      throw new IllegalArgumentException("Informe os pagamentos recebidos.");
    var p = prepare(input, true);
    UUID discountApprover =
        p.discountApproval()
            ? approvals.require(
                Permission.DISCOUNT_APPROVE, input.discountApproval(), p.totals().maximumPercent())
            : null;
    UUID stockApprover = null;
    if (p.negative()) {
      var c =
          input.stockApproval() == null
              ? new ApprovalService.Credentials("", "", "")
              : input.stockApproval();
      stockApprover =
          stockApprovals.approve(
              new StockForm(
                  input.operationId(),
                  "ADJUSTMENT",
                  "-1",
                  "Venda no PDV",
                  c.email(),
                  c.password(),
                  c.reason()));
    }
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO sales(id,tenant_id,store_id,session_id,actor_id,operation_id,request_hash,customer,subtotal,discount,total,change) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        tenant.id(),
        store(),
        input.sessionId(),
        actor(),
        input.operationId(),
        hash,
        customer(input.customer()),
        p.totals().subtotal(),
        p.totals().discount(),
        p.totals().total(),
        p.change());
    for (int i = 0; i < p.products().size(); i++) {
      var product = p.products().get(i);
      var quantity = p.quantities().get(i);
      var line = p.totals().lines().get(i);
      jdbc.update(
          "INSERT INTO sale_items(id,tenant_id,store_id,sale_id,product_id,sku,name,unit,quantity,unit_price,subtotal,discount,total,ncm,cest) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          UUID.randomUUID(),
          tenant.id(),
          store(),
          id,
          product.id(),
          product.sku(),
          product.name(),
          product.unit(),
          quantity,
          product.price(),
          line.gross(),
          line.discount(),
          line.total(),
          product.ncm(),
          product.cest());
      jdbc.update(
          "INSERT INTO stock_balances(tenant_id,store_id,product_id) VALUES(?,?,?) ON CONFLICT DO NOTHING",
          tenant.id(),
          store(),
          product.id());
      var balance =
          jdbc.queryForObject(
              "SELECT quantity FROM stock_balances WHERE tenant_id=? AND store_id=? AND product_id=? FOR UPDATE",
              BigDecimal.class,
              tenant.id(),
              store(),
              product.id());
      var after = balance.subtract(quantity);
      jdbc.update(
          "UPDATE stock_balances SET quantity=?,version=version+1 WHERE tenant_id=? AND store_id=? AND product_id=?",
          after,
          tenant.id(),
          store(),
          product.id());
      jdbc.update(
          "INSERT INTO stock_movements(id,tenant_id,store_id,product_id,operation_id,request_hash,type,quantity,balance_after,reason,actor_id,approver_id,approval_reason,sale_id,created_at) VALUES(?,?,?,?,?,?,'SALE',?,?,?,?,?,?,?,clock_timestamp())",
          UUID.randomUUID(),
          tenant.id(),
          store(),
          product.id(),
          UUID.randomUUID(),
          hash,
          quantity.negate(),
          after,
          "Venda " + id,
          actor(),
          after.signum() < 0 ? stockApprover : null,
          after.signum() < 0 ? input.stockApproval().reason().trim() : null,
          id);
    }
    for (var payment : p.payments())
      jdbc.update(
          "INSERT INTO sale_payments(id,tenant_id,store_id,sale_id,method,amount) VALUES(?,?,?,?,?,?)",
          UUID.randomUUID(),
          tenant.id(),
          store(),
          id,
          payment.method(),
          payment.amount());
    if (p.cashNet().signum() > 0)
      cash.event(
          input.sessionId(),
          input.operationId(),
          "SALE",
          p.cashNet(),
          "Venda " + id,
          null,
          null,
          hash);
    if (discountApprover != null)
      approval(id, "DISCOUNT", discountApprover, input.discountApproval().reason());
    if (stockApprover != null)
      approval(id, "NEGATIVE_STOCK", stockApprover, input.stockApproval().reason());
    audit.record(tenant.id(), store(), actor(), "SALE_COMPLETED", id);
    return id;
  }

  private Prepared prepare(Checkout input, boolean lock) {
    var requests = input.items().stream().sorted(Comparator.comparing(Item::productId)).toList();
    if (lock) for (var request : requests) catalog.lockProduct(tenant.id(), request.productId());
    var products = new ArrayList<CatalogRepository.Product>();
    var quantities = new ArrayList<BigDecimal>();
    var calculations = new ArrayList<SaleMath.Input>();
    boolean negative = false;
    for (var request : requests) {
      var product = catalog.find(tenant.id(), store(), request.productId(), true);
      if (!product.active() || !product.priceActive() || product.price() == null)
        throw new IllegalArgumentException(
            "Um produto está inativo ou sem preço ativo nesta filial. Atualize o carrinho.");
      if (product.price().compareTo(SaleMath.money(request.unitPrice())) != 0
          || product.version() != request.productVersion())
        throw new IllegalArgumentException(
            "O cadastro ou preço de "
                + product.name()
                + " mudou. Remova o item e busque novamente.");
      var quantity = DecimalValues.quantity("quantity", request.quantity(), product.unit());
      if (quantity.signum() <= 0 || quantity.compareTo(new BigDecimal("999999.999")) > 0)
        throw new IllegalArgumentException("Quantidade deve ser maior que zero e até 999.999,999.");
      products.add(product);
      quantities.add(quantity);
      calculations.add(new SaleMath.Input(product.price(), quantity, request.discount()));
      negative |= product.quantity().subtract(quantity).signum() < 0;
    }
    var totals = SaleMath.calculate(calculations, input.discount());
    if (totals.total().compareTo(new BigDecimal("999999999.99")) > 0)
      throw new IllegalArgumentException("O total da venda excede o limite permitido.");
    if (totals.maximumPercent().compareTo(cash.policy().maximumDiscount()) > 0)
      throw new IllegalArgumentException(
          "O desconto acumulado de um item ultrapassa o teto da empresa. Reduza os descontos.");
    var ownLimit =
        access.activeRoles().stream()
            .filter(r -> r.getPermissions().contains(Permission.DISCOUNT_APPLY))
            .map(r -> r.getDiscountLimit())
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    var payments = new ArrayList<ReceiptPayment>();
    var methods = new HashSet<String>();
    var digital = BigDecimal.ZERO;
    var cashAmount = BigDecimal.ZERO;
    for (var payment : input.payments()) {
      if (payment == null
          || !List.of("CASH", "PIX", "DEBIT", "CREDIT").contains(payment.method())
          || !methods.add(payment.method()))
        throw new IllegalArgumentException("Forma de pagamento inválida ou repetida.");
      var amount = CashService.nonnegative(payment.amount());
      if (amount.signum() <= 0)
        throw new IllegalArgumentException("Cada pagamento deve ser maior que zero.");
      payments.add(new ReceiptPayment(payment.method(), amount));
      if ("CASH".equals(payment.method())) cashAmount = cashAmount.add(amount);
      else digital = digital.add(amount);
    }
    if (digital.compareTo(totals.total()) > 0)
      throw new IllegalArgumentException(
          "Pagamentos digitais não podem exceder o total. Troco só pode sair do dinheiro recebido.");
    var cashNet = totals.total().subtract(digital);
    if (lock && cashAmount.compareTo(cashNet) < 0)
      throw new IllegalArgumentException("Os pagamentos não cobrem o total da venda.");
    return new Prepared(
        requests,
        products,
        quantities,
        totals,
        payments,
        cashAmount.subtract(cashNet).max(BigDecimal.ZERO),
        cashNet,
        totals.maximumPercent().compareTo(ownLimit) > 0,
        negative);
  }

  public Receipt receipt(UUID id) {
    var rows =
        jdbc.queryForList(
            "SELECT s.*,u.name AS operator_name,t.name AS store_name FROM sales s JOIN users u ON u.id=s.actor_id JOIN stores t ON t.tenant_id=s.tenant_id AND t.id=s.store_id WHERE s.tenant_id=? AND s.store_id=? AND s.id=?",
            tenant.id(),
            store(),
            id);
    if (rows.isEmpty()) throw new AccessDeniedException("Venda indisponível.");
    var row = rows.getFirst();
    if (!actor().equals(row.get("actor_id"))) access.require(Permission.REPORT_VIEW);
    else access.require(Permission.SALE_CREATE);
    var items =
        jdbc.query(
            "SELECT * FROM sale_items WHERE tenant_id=? AND store_id=? AND sale_id=? ORDER BY sku",
            (rs, n) ->
                new ReceiptItem(
                    rs.getString("sku"),
                    rs.getString("name"),
                    rs.getString("unit"),
                    rs.getBigDecimal("quantity"),
                    rs.getBigDecimal("unit_price"),
                    rs.getBigDecimal("subtotal"),
                    rs.getBigDecimal("discount"),
                    rs.getBigDecimal("total")),
            tenant.id(),
            store(),
            id);
    var payments =
        jdbc.query(
            "SELECT method,amount FROM sale_payments WHERE tenant_id=? AND store_id=? AND sale_id=? ORDER BY method",
            (rs, n) -> new ReceiptPayment(rs.getString(1), rs.getBigDecimal(2)),
            tenant.id(),
            store(),
            id);
    return new Receipt(
        id,
        (String) row.get("customer"),
        (String) row.get("operator_name"),
        (String) row.get("store_name"),
        (BigDecimal) row.get("subtotal"),
        (BigDecimal) row.get("discount"),
        (BigDecimal) row.get("total"),
        (BigDecimal) row.get("change"),
        (String) row.get("commercial_status"),
        (String) row.get("fiscal_status"),
        (String) row.get("mode"),
        jdbc.queryForObject(
            "SELECT created_at FROM sales WHERE tenant_id=? AND store_id=? AND id=?",
            OffsetDateTime.class,
            tenant.id(),
            store(),
            id),
        items,
        payments);
  }

  private void approval(UUID sale, String type, UUID approver, String reason) {
    jdbc.update(
        "INSERT INTO sale_approvals(id,tenant_id,store_id,sale_id,type,actor_id,approver_id,reason) VALUES(?,?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        tenant.id(),
        store(),
        sale,
        type,
        actor(),
        approver,
        reason.trim());
    audit.record(
        tenant.id(),
        store(),
        actor(),
        type.equals("DISCOUNT") ? "SALE_DISCOUNT_APPROVED" : "NEGATIVE_STOCK_APPROVED",
        sale);
  }

  private static void validate(Checkout input) {
    if (input == null
        || input.sessionId() == null
        || input.items() == null
        || input.items().isEmpty()
        || input.items().size() > 100
        || input.payments() == null
        || input.payments().size() > 4)
      throw new IllegalArgumentException(
          "Informe um caixa, de 1 a 100 produtos e até 4 pagamentos.");
    var unique = new HashSet<UUID>();
    for (var item : input.items())
      if (item == null || item.productId() == null || !unique.add(item.productId()))
        throw new IllegalArgumentException("Produto inválido ou repetido no carrinho.");
    customer(input.customer());
    var methods = new HashSet<String>();
    for (var payment : input.payments()) {
      if (payment == null
          || payment.method() == null
          || !List.of("CASH", "PIX", "DEBIT", "CREDIT").contains(payment.method())
          || !methods.add(payment.method()))
        throw new IllegalArgumentException("Forma de pagamento inválida ou repetida.");
    }
  }

  private static String customer(String value) {
    var text = value == null ? "" : value.trim();
    if (text.length() > 120 || text.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Nome do cliente inválido (até 120 caracteres).");
    return text;
  }

  private static String fingerprint(Checkout input) {
    var canonical =
        new StringBuilder(
            input.sessionId()
                + "|"
                + customer(input.customer()).length()
                + ":"
                + customer(input.customer())
                + "|"
                + discountKey(input.discount()));
    input.items().stream()
        .sorted(Comparator.comparing(Item::productId))
        .forEach(
            i ->
                canonical
                    .append("|")
                    .append(i.productId())
                    .append(":")
                    .append(DecimalValues.parse("quantity", i.quantity(), 3))
                    .append(":")
                    .append(SaleMath.money(i.unitPrice()))
                    .append(":")
                    .append(i.productVersion())
                    .append(":")
                    .append(discountKey(i.discount())));
    input.payments().stream()
        .sorted(Comparator.comparing(Payment::method))
        .forEach(
            p ->
                canonical
                    .append("|")
                    .append(p.method())
                    .append(":")
                    .append(SaleMath.money(p.amount())));
    return InventoryService.hash(canonical.toString());
  }

  private static String discountKey(SaleMath.Discount d) {
    return d == null ? "PERCENT:0.00" : d.type() + ":" + SaleMath.money(d.value());
  }

  private UUID store() {
    return access.activeStore().getId();
  }

  private UUID actor() {
    return access.principal().userId();
  }
}
