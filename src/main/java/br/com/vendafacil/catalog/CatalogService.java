package br.com.vendafacil.catalog;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import br.com.vendafacil.shared.audit.AuditService;
import br.com.vendafacil.shared.validation.DataPage;
import br.com.vendafacil.shared.validation.FieldValidationException;
import br.com.vendafacil.shared.validation.FormChecks;
import br.com.vendafacil.tenant.TenantContext;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
  public record SearchProduct(
      UUID id,
      String sku,
      String name,
      String barcode,
      String unit,
      java.math.BigDecimal price,
      java.math.BigDecimal quantity) {}

  private final CatalogRepository repository;
  private final JdbcTemplate jdbc;
  private final AccessService access;
  private final TenantContext tenant;
  private final AuditService audit;
  private final Validator validator;

  public CatalogService(
      CatalogRepository repository,
      JdbcTemplate jdbc,
      AccessService access,
      TenantContext tenant,
      AuditService audit,
      Validator validator) {
    this.repository = repository;
    this.jdbc = jdbc;
    this.access = access;
    this.tenant = tenant;
    this.audit = audit;
    this.validator = validator;
  }

  @Transactional(readOnly = true)
  public DataPage<CatalogRepository.Product> list(
      String q, String status, UUID category, int page) {
    access.require(Permission.PRODUCT_READ);
    return repository.list(
        tenant.id(),
        access.activeStore().getId(),
        q,
        status,
        category,
        false,
        page,
        access.permissions().contains(Permission.STOCK_READ));
  }

  @Transactional(readOnly = true)
  public CatalogRepository.Product detail(UUID id) {
    access.require(Permission.PRODUCT_READ);
    return repository.find(
        tenant.id(),
        access.activeStore().getId(),
        id,
        access.permissions().contains(Permission.STOCK_READ));
  }

  @Transactional(readOnly = true)
  public List<CatalogRepository.Category> categories() {
    access.require(Permission.PRODUCT_READ);
    return repository.categories(tenant.id());
  }

  @Transactional(readOnly = true)
  public List<SearchProduct> search(String query) {
    var permissions = access.permissions();
    if (!permissions.contains(Permission.PRODUCT_READ)
        && !permissions.contains(Permission.SALE_CREATE)) {
      access.require(Permission.PRODUCT_READ);
    }
    return repository
        .saleSearch(
            tenant.id(),
            access.activeStore().getId(),
            query,
            permissions.contains(Permission.STOCK_READ))
        .stream()
        .map(
            p ->
                new SearchProduct(
                    p.id(), p.sku(), p.name(), p.barcode(), p.unit(), p.price(), p.quantity()))
        .toList();
  }

  @Transactional
  public UUID saveCategory(UUID id, String name, boolean active, long version) {
    access.require(Permission.PRODUCT_WRITE);
    repository.lockTenant(tenant.id());
    var clean = name == null ? "" : name.trim();
    if (clean.isBlank() || clean.length() > 80 || clean.chars().anyMatch(Character::isISOControl)) {
      throw new FieldValidationException(
          "name", "Informe um nome de categoria de até 80 caracteres.");
    }
    var originalId = id;
    if (repository.categories(tenant.id()).stream()
        .anyMatch(c -> c.name().equalsIgnoreCase(clean) && !c.id().equals(originalId))) {
      throw new FieldValidationException("name", "Já existe uma categoria com esse nome.");
    }
    if (id == null) {
      id = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO categories(id,tenant_id,name,active) VALUES(?,?,?,?)",
          id,
          tenant.id(),
          clean,
          active);
    } else {
      var current = repository.category(tenant.id(), id);
      if (current.version() != version) {
        throw new IllegalArgumentException("A categoria foi alterada. Atualize a página.");
      }
      jdbc.update(
          "UPDATE categories SET name=?,active=?,version=version+1 WHERE tenant_id=? AND id=?",
          clean,
          active,
          tenant.id(),
          id);
    }
    audit.record(
        tenant.id(),
        access.activeStore().getId(),
        access.principal().userId(),
        "CATEGORY_SAVED",
        id);
    return id;
  }

  @Transactional
  public UUID create(ProductForm form) {
    access.require(Permission.PRODUCT_WRITE);
    repository.lockTenant(tenant.id());
    validate(form, null);
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO products(id,tenant_id,sku,name,barcode,category_id,unit,active,ncm,cest) VALUES(?,?,?,?,?,?,?,?,?,?)",
        id,
        tenant.id(),
        form.sku().toUpperCase(Locale.ROOT),
        form.name().trim(),
        blank(form.barcode()),
        form.categoryId(),
        form.unit(),
        Boolean.TRUE.equals(form.active()),
        blank(form.ncm()),
        blank(form.cest()));
    audit.record(
        tenant.id(),
        access.activeStore().getId(),
        access.principal().userId(),
        "PRODUCT_CREATED",
        id);
    return id;
  }

  @Transactional
  public void update(UUID id, ProductForm form) {
    access.require(Permission.PRODUCT_WRITE);
    repository.lockTenant(tenant.id());
    repository.lockProduct(tenant.id(), id);
    var current = repository.find(tenant.id(), access.activeStore().getId(), id, true);
    validate(form, id);
    if (current.version() != form.version()) {
      throw new FieldValidationException(
          "name", "O produto foi alterado por outra pessoa. Reabra o cadastro.");
    }
    if (!current.unit().equals(form.unit())
        && Boolean.TRUE.equals(
            jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM stock_movements WHERE tenant_id=? AND product_id=?) OR EXISTS(SELECT 1 FROM stock_balances WHERE tenant_id=? AND product_id=? AND minimum_quantity IS NOT NULL)",
                Boolean.class,
                tenant.id(),
                id,
                tenant.id(),
                id))) {
      throw new FieldValidationException(
          "unit", "A unidade não pode mudar depois de movimentar estoque ou definir um mínimo.");
    }
    jdbc.update(
        "UPDATE products SET sku=?,name=?,barcode=?,category_id=?,unit=?,active=?,ncm=?,cest=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?",
        form.sku().toUpperCase(Locale.ROOT),
        form.name().trim(),
        blank(form.barcode()),
        form.categoryId(),
        form.unit(),
        Boolean.TRUE.equals(form.active()),
        blank(form.ncm()),
        blank(form.cest()),
        tenant.id(),
        id);
    audit.record(
        tenant.id(),
        access.activeStore().getId(),
        access.principal().userId(),
        "PRODUCT_UPDATED",
        id);
  }

  private void validate(ProductForm form, UUID id) {
    FormChecks.validate(validator, form);
    if (form.name().chars().anyMatch(Character::isISOControl)) {
      throw new FieldValidationException("name", "Não use caracteres de controle no nome.");
    }
    if (repository.duplicate(tenant.id(), "sku", form.sku().toUpperCase(Locale.ROOT), id)) {
      throw new FieldValidationException("sku", "Já existe um produto com esse SKU nesta empresa.");
    }
    if (blank(form.barcode()) != null
        && repository.duplicate(tenant.id(), "barcode", form.barcode(), id)) {
      throw new FieldValidationException(
          "barcode", "Esse código de barras já está cadastrado nesta empresa.");
    }
    if (form.categoryId() != null) {
      var category = repository.category(tenant.id(), form.categoryId());
      if (!category.active()) {
        throw new FieldValidationException("categoryId", "Selecione uma categoria ativa.");
      }
    }
  }

  public static String blank(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
