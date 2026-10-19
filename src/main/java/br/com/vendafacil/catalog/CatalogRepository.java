package br.com.vendafacil.catalog;

import br.com.vendafacil.shared.validation.DataPage;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
  public record Category(UUID id, String name, boolean active, long version) {}

  public record Product(
      UUID id,
      String sku,
      String name,
      String barcode,
      UUID categoryId,
      String category,
      String unit,
      boolean active,
      String ncm,
      String cest,
      long version,
      boolean image,
      BigDecimal price,
      boolean priceActive,
      long priceVersion,
      BigDecimal quantity,
      BigDecimal minimum,
      long stockVersion) {}

  private final JdbcTemplate jdbc;

  public CatalogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static final String SELECT =
      """
      SELECT p.*, c.name AS category_name,
        EXISTS(SELECT 1 FROM product_images i WHERE i.tenant_id=p.tenant_id AND i.product_id=p.id) AS image,
        sp.amount, coalesce(sp.active,false) AS price_active, coalesce(sp.version,-1) AS price_version,
        coalesce(sb.quantity,0) AS quantity, sb.minimum_quantity, coalesce(sb.version,0) AS stock_version
      FROM products p
      LEFT JOIN categories c ON c.tenant_id=p.tenant_id AND c.id=p.category_id
      LEFT JOIN store_prices sp ON sp.tenant_id=p.tenant_id AND sp.product_id=p.id AND sp.store_id=?
      LEFT JOIN stock_balances sb ON sb.tenant_id=p.tenant_id AND sb.product_id=p.id AND sb.store_id=?
      """;

  public void lockTenant(UUID tenantId) {
    jdbc.queryForObject("SELECT id FROM tenants WHERE id=? FOR UPDATE", UUID.class, tenantId);
  }

  public List<Category> categories(UUID tenantId) {
    return jdbc.query(
        "SELECT * FROM categories WHERE tenant_id=? ORDER BY lower(name),id",
        (rs, n) ->
            new Category(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getBoolean("active"),
                rs.getLong("version")),
        tenantId);
  }

  public Category category(UUID tenantId, UUID id) {
    return categories(tenantId).stream()
        .filter(c -> c.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new AccessDeniedException("Categoria indisponível."));
  }

  public DataPage<Product> list(
      UUID tenantId,
      UUID storeId,
      String query,
      String status,
      UUID categoryId,
      boolean low,
      int page,
      boolean stockVisible) {
    var where = new StringBuilder(" WHERE p.tenant_id=?");
    var args = new ArrayList<Object>(List.of(storeId, storeId, tenantId));
    var search = query == null ? "" : query.trim();
    if (search.length() > 100) {
      search = search.substring(0, 100);
    }
    if (!search.isEmpty()) {
      where.append(
          " AND (strpos(lower(p.name),lower(?))>0 OR strpos(lower(p.sku),lower(?))>0 OR strpos(lower(coalesce(p.barcode,'')),lower(?))>0)");
      args.addAll(List.of(search, search, search));
    }
    if ("active".equals(status)) {
      where.append(" AND p.active");
    } else if ("inactive".equals(status)) {
      where.append(" AND NOT p.active");
    } else if (!"all".equals(status)) {
      throw new IllegalArgumentException("Filtro de situação inválido.");
    }
    if (categoryId != null) {
      where.append(" AND p.category_id=?");
      args.add(categoryId);
    }
    if (low) {
      where.append(
          " AND sb.minimum_quantity IS NOT NULL AND coalesce(sb.quantity,0)<=sb.minimum_quantity");
    }
    var sql = SELECT + where;
    long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM (" + sql + ") counted", Long.class, args.toArray());
    int number = Math.max(0, Math.min(page, (int) Math.max(0, (count - 1) / 20)));
    args.add(20);
    args.add(number * 20);
    var items =
        jdbc.query(
            sql + " ORDER BY lower(p.name),p.id LIMIT ? OFFSET ?",
            (rs, n) -> map(rs, stockVisible),
            args.toArray());
    return new DataPage<>(items, number, count, 20);
  }

  public Product find(UUID tenantId, UUID storeId, UUID id, boolean stockVisible) {
    return jdbc
        .query(
            SELECT + " WHERE p.tenant_id=? AND p.id=?",
            (rs, n) -> map(rs, stockVisible),
            storeId,
            storeId,
            tenantId,
            id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new AccessDeniedException("Produto indisponível."));
  }

  public void lockProduct(UUID tenantId, UUID id) {
    if (jdbc.query(
            "SELECT id FROM products WHERE tenant_id=? AND id=? FOR UPDATE",
            (rs, n) -> rs.getObject(1, UUID.class),
            tenantId,
            id)
        .isEmpty()) {
      throw new AccessDeniedException("Produto indisponível.");
    }
  }

  public List<Product> saleSearch(UUID tenantId, UUID storeId, String query, boolean stockVisible) {
    var search = query == null ? "" : query.trim();
    if (search.length() > 100) {
      search = search.substring(0, 100);
    }
    return jdbc.query(
        SELECT
            + " WHERE p.tenant_id=? AND p.active AND sp.active AND (strpos(lower(p.name),lower(?))>0 OR strpos(lower(p.sku),lower(?))>0 OR strpos(lower(coalesce(p.barcode,'')),lower(?))>0) ORDER BY lower(p.name),p.id LIMIT 20",
        (rs, n) -> map(rs, stockVisible),
        storeId,
        storeId,
        tenantId,
        search,
        search,
        search);
  }

  public boolean duplicate(UUID tenantId, String field, String value, UUID exclude) {
    if (!List.of("sku", "barcode").contains(field)) {
      throw new IllegalArgumentException("Campo inválido.");
    }
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM products WHERE tenant_id=? AND "
                + field
                + "=? AND (?::uuid IS NULL OR id<>?))",
            Boolean.class,
            tenantId,
            value,
            exclude,
            exclude));
  }

  private Product map(ResultSet rs, boolean stockVisible) throws SQLException {
    return new Product(
        rs.getObject("id", UUID.class),
        rs.getString("sku"),
        rs.getString("name"),
        rs.getString("barcode"),
        rs.getObject("category_id", UUID.class),
        rs.getString("category_name"),
        rs.getString("unit"),
        rs.getBoolean("active"),
        rs.getString("ncm"),
        rs.getString("cest"),
        rs.getLong("version"),
        rs.getBoolean("image"),
        rs.getBigDecimal("amount"),
        rs.getBoolean("price_active"),
        rs.getLong("price_version"),
        stockVisible ? rs.getBigDecimal("quantity") : null,
        stockVisible ? rs.getBigDecimal("minimum_quantity") : null,
        rs.getLong("stock_version"));
  }
}
