package br.com.vendafacil.catalog;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import br.com.vendafacil.shared.audit.AuditService;
import br.com.vendafacil.shared.validation.DecimalValues;
import br.com.vendafacil.shared.validation.FieldValidationException;
import br.com.vendafacil.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PricingService {
  private final JdbcTemplate jdbc;
  private final CatalogRepository products;
  private final AccessService access;
  private final TenantContext tenant;
  private final AuditService audit;

  public PricingService(
      JdbcTemplate jdbc,
      CatalogRepository products,
      AccessService access,
      TenantContext tenant,
      AuditService audit) {
    this.jdbc = jdbc;
    this.products = products;
    this.access = access;
    this.tenant = tenant;
    this.audit = audit;
  }

  @Transactional
  public void save(UUID productId, PriceForm form) {
    access.require(Permission.PRICE_CHANGE);
    var storeId = access.activeStore().getId();
    var amount = DecimalValues.parse("amount", form.amount(), 2);
    if (amount.signum() <= 0) {
      throw new FieldValidationException("amount", "O preço deve ser maior que zero.");
    }
    products.lockProduct(tenant.id(), productId);
    var product = products.find(tenant.id(), storeId, productId, false);
    if (product.priceVersion() != form.version()) {
      throw new FieldValidationException(
          "amount", "O preço mudou. Atualize a página antes de salvar.");
    }
    jdbc.update(
        """
        INSERT INTO store_prices(tenant_id,store_id,product_id,amount,active) VALUES(?,?,?,?,?)
        ON CONFLICT(tenant_id,store_id,product_id) DO UPDATE SET
          amount=EXCLUDED.amount,active=EXCLUDED.active,version=store_prices.version+1,updated_at=CURRENT_TIMESTAMP
        """,
        tenant.id(),
        storeId,
        productId,
        amount,
        Boolean.TRUE.equals(form.active()));
    jdbc.update(
        "INSERT INTO store_price_history(id,tenant_id,store_id,product_id,previous_amount,amount,active,actor_id) VALUES(?,?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        tenant.id(),
        storeId,
        productId,
        product.price(),
        amount,
        Boolean.TRUE.equals(form.active()),
        access.principal().userId());
    audit.record(tenant.id(), storeId, access.principal().userId(), "PRICE_CHANGED", productId);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> history(UUID productId) {
    access.require(Permission.PRODUCT_READ);
    products.find(tenant.id(), access.activeStore().getId(), productId, false);
    return jdbc.queryForList(
        "SELECT h.amount,h.active,h.created_at,u.name AS actor FROM store_price_history h JOIN users u ON u.id=h.actor_id WHERE h.tenant_id=? AND h.store_id=? AND h.product_id=? ORDER BY h.created_at DESC,h.id LIMIT 10",
        tenant.id(),
        access.activeStore().getId(),
        productId);
  }
}
