package br.com.vendafacil.catalog.importing;

import br.com.vendafacil.catalog.CatalogRepository;
import br.com.vendafacil.catalog.CatalogService;
import br.com.vendafacil.catalog.PriceForm;
import br.com.vendafacil.catalog.PricingService;
import br.com.vendafacil.catalog.ProductForm;
import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import br.com.vendafacil.inventory.InventoryService;
import br.com.vendafacil.inventory.StockForm;
import br.com.vendafacil.shared.audit.AuditService;
import br.com.vendafacil.shared.validation.CsvFiles;
import br.com.vendafacil.shared.validation.DataPage;
import br.com.vendafacil.shared.validation.DecimalValues;
import br.com.vendafacil.tenant.TenantContext;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImportService {
  public record Job(
      UUID id,
      String filename,
      String status,
      int rowCount,
      int errorCount,
      Instant createdAt,
      Instant expiresAt) {
    public boolean canConfirm() {
      return "PREVIEW".equals(status) && errorCount == 0 && expiresAt.isAfter(Instant.now());
    }
  }

  public record RowView(
      int number,
      String sku,
      String name,
      String unit,
      String price,
      String quantity,
      String barcode,
      String category,
      String minimum,
      String ncm,
      String cest,
      List<String> errors) {}

  public record Preview(Job job, DataPage<RowView> rows) {}

  private final JdbcTemplate jdbc;
  private final ImportFileReader reader;
  private final AccessService access;
  private final TenantContext tenant;
  private final CatalogRepository products;
  private final CatalogService catalog;
  private final PricingService prices;
  private final InventoryService inventory;
  private final AuditService audit;
  private final Validator validator;

  public ProductImportService(
      JdbcTemplate jdbc,
      ImportFileReader reader,
      AccessService access,
      TenantContext tenant,
      CatalogRepository products,
      CatalogService catalog,
      PricingService prices,
      InventoryService inventory,
      AuditService audit,
      Validator validator) {
    this.jdbc = jdbc;
    this.reader = reader;
    this.access = access;
    this.tenant = tenant;
    this.products = products;
    this.catalog = catalog;
    this.prices = prices;
    this.inventory = inventory;
    this.audit = audit;
    this.validator = validator;
  }

  private void require() {
    access.require(Permission.PRODUCT_IMPORT);
    access.require(Permission.PRODUCT_WRITE);
  }

  @Transactional
  public UUID preview(MultipartFile file) {
    require();
    var storeId = access.activeStore().getId();
    products.lockTenant(tenant.id());
    var daily =
        jdbc.queryForObject(
            "SELECT count(*) FROM import_jobs WHERE tenant_id=? AND actor_id=? AND created_at>CURRENT_TIMESTAMP-INTERVAL '24 hours'",
            Long.class,
            tenant.id(),
            access.principal().userId());
    if (daily >= 20) {
      throw new IllegalArgumentException(
          "Limite de 20 prévias por pessoa em 24 horas. Tente novamente mais tarde.");
    }
    var source = reader.read(file);
    var id = UUID.randomUUID();
    String hash;
    try {
      hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.bytes()));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
    jdbc.update(
        "INSERT INTO import_jobs(id,tenant_id,store_id,actor_id,filename,file_hash,status,row_count,expires_at) VALUES(?,?,?,?,?,?,'PREVIEW',?,?)",
        id,
        tenant.id(),
        storeId,
        access.principal().userId(),
        source.name(),
        hash,
        source.rows().size(),
        java.sql.Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS)));
    for (var row : source.rows()) {
      var v = row.values();
      jdbc.update(
          "INSERT INTO import_rows(id,tenant_id,store_id,job_id,row_number,template_version,sku,name,barcode,category,unit,price,initial_quantity,minimum_quantity,ncm,cest) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          UUID.randomUUID(),
          tenant.id(),
          storeId,
          id,
          row.number(),
          v.get(0),
          v.get(1),
          v.get(2),
          v.get(3),
          v.get(4),
          v.get(5),
          v.get(6),
          v.get(7),
          v.get(8),
          v.get(9),
          v.get(10));
    }
    validateRows(id, source.rows());
    audit.record(tenant.id(), storeId, access.principal().userId(), "PRODUCT_IMPORT_PREVIEWED", id);
    return id;
  }

  @Transactional
  public boolean confirm(UUID id) {
    require();
    products.lockTenant(tenant.id());
    var job = job(id, true);
    if ("COMPLETED".equals(job.status())) {
      return true;
    }
    if (!job.canConfirm()) {
      throw new IllegalArgumentException(
          "Esta prévia contém erros ou expirou. Corrija o arquivo e envie uma nova prévia.");
    }
    var rows = rawRows(id);
    if (validateRows(id, rows) > 0) {
      return false;
    }
    for (var row : rows) {
      var v = row.values();
      UUID categoryId = null;
      if (!v.get(4).isBlank()) {
        var category =
            products.categories(tenant.id()).stream()
                .filter(c -> c.name().equalsIgnoreCase(v.get(4)))
                .findFirst();
        categoryId =
            category.isPresent()
                ? category.get().id()
                : catalog.saveCategory(null, v.get(4), true, 0);
      }
      var productId = catalog.create(form(v, categoryId));
      if (!v.get(6).isBlank()) {
        prices.save(productId, new PriceForm(v.get(6), true, -1));
      }
      if (!v.get(7).isBlank()
          && DecimalValues.quantity("quantity", v.get(7), v.get(5)).signum() > 0) {
        var operationId =
            UUID.nameUUIDFromBytes((id + ":" + row.number()).getBytes(StandardCharsets.UTF_8));
        inventory.move(
            productId,
            new StockForm(
                operationId, "ENTRY", v.get(7), "Estoque inicial da importação " + id, "", "", ""));
      }
      if (!v.get(8).isBlank()) {
        var current = products.find(tenant.id(), access.activeStore().getId(), productId, true);
        inventory.minimum(productId, v.get(8), current.stockVersion());
      }
      jdbc.update(
          "UPDATE import_rows SET product_id=? WHERE tenant_id=? AND store_id=? AND job_id=? AND row_number=?",
          productId,
          tenant.id(),
          access.activeStore().getId(),
          id,
          row.number());
    }
    jdbc.update(
        "UPDATE import_jobs SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND store_id=? AND id=?",
        tenant.id(),
        access.activeStore().getId(),
        id);
    audit.record(
        tenant.id(),
        access.activeStore().getId(),
        access.principal().userId(),
        "PRODUCT_IMPORT_COMPLETED",
        id);
    return true;
  }

  @Transactional(readOnly = true)
  public List<Job> recent() {
    require();
    return jdbc.query(
        "SELECT * FROM import_jobs WHERE tenant_id=? AND store_id=? AND actor_id=? ORDER BY created_at DESC,id LIMIT 10",
        this::mapJob,
        tenant.id(),
        access.activeStore().getId(),
        access.principal().userId());
  }

  @Transactional(readOnly = true)
  public Preview detail(UUID id, int page) {
    require();
    var job = job(id, false);
    var number = Math.max(0, Math.min(page, (job.rowCount() - 1) / 50));
    var errors = errors(id);
    var rows =
        rawRows(id).stream()
            .skip((long) number * 50)
            .limit(50)
            .map(
                row ->
                    new RowView(
                        row.number(),
                        row.values().get(1),
                        row.values().get(2),
                        row.values().get(5),
                        row.values().get(6),
                        row.values().get(7),
                        row.values().get(3),
                        row.values().get(4),
                        row.values().get(8),
                        row.values().get(9),
                        row.values().get(10),
                        errors.getOrDefault(row.number(), List.of())))
            .toList();
    return new Preview(job, new DataPage<>(rows, number, job.rowCount(), 50));
  }

  @Transactional(readOnly = true)
  public String report(UUID id) {
    require();
    job(id, false);
    var issues = errors(id);
    var csv = new StringBuilder("\uFEFFlinha;sku;nome;situacao;erros\r\n");
    for (var row : rawRows(id)) {
      var errors = issues.getOrDefault(row.number(), List.of());
      csv.append(
          CsvFiles.line(
              List.of(
                  Integer.toString(row.number()),
                  row.values().get(1),
                  row.values().get(2),
                  errors.isEmpty() ? "OK" : "ERRO",
                  String.join(" | ", errors))));
    }
    return csv.toString();
  }

  private int validateRows(UUID id, List<ImportFileReader.Row> rows) {
    var skus = new HashSet<String>();
    var codes = new HashSet<String>();
    var permissions = access.permissions();
    var categories = products.categories(tenant.id());
    int count = 0;
    for (var row : rows) {
      var v = row.values();
      var issues = new ArrayList<>(row.errors());
      if (!"1".equals(v.get(0))) {
        issues.add("A coluna versao deve conter 1.");
      }
      for (var violation : validator.validate(form(v, null))) {
        issues.add(violation.getPropertyPath() + ": " + violation.getMessage());
      }
      var sku = v.get(1).toUpperCase(Locale.ROOT);
      if (!skus.add(sku)) {
        issues.add("SKU repetido neste arquivo.");
      }
      if (products.duplicate(tenant.id(), "sku", sku, null)) {
        issues.add("SKU já cadastrado. Esta versão importa somente produtos novos.");
      }
      if (!v.get(3).isBlank()
          && (!codes.add(v.get(3)) || products.duplicate(tenant.id(), "barcode", v.get(3), null))) {
        issues.add("Código de barras repetido no arquivo ou já cadastrado.");
      }
      if (v.get(4).length() > 80) {
        issues.add("Categoria: máximo de 80 caracteres.");
      }
      if (categories.stream().anyMatch(c -> c.name().equalsIgnoreCase(v.get(4)) && !c.active())) {
        issues.add("A categoria informada está inativa.");
      }
      if (!v.get(6).isBlank()) {
        if (!permissions.contains(Permission.PRICE_CHANGE)) {
          issues.add("Sem permissão para definir preço nesta filial; deixe preco vazio.");
        }
        try {
          if (DecimalValues.parse("price", v.get(6), 2).signum() <= 0) {
            issues.add("O preço deve ser maior que zero.");
          }
        } catch (IllegalArgumentException exception) {
          issues.add("Preço: " + exception.getMessage());
        }
      }
      for (int column : new int[] {7, 8}) {
        if (!v.get(column).isBlank()) {
          if (!permissions.contains(Permission.STOCK_ADJUST)) {
            issues.add(
                "Sem permissão para definir estoque nesta filial; deixe as colunas de estoque vazias.");
          }
          try {
            if (DecimalValues.quantity("quantity", v.get(column), v.get(5)).signum() < 0) {
              issues.add("Estoque inicial/mínimo não pode ser negativo.");
            }
          } catch (IllegalArgumentException exception) {
            issues.add("Estoque: " + exception.getMessage());
          }
        }
      }
      for (var issue : issues.stream().distinct().toList()) {
        jdbc.update(
            "INSERT INTO import_row_errors(id,tenant_id,store_id,job_id,row_number,message) VALUES(?,?,?,?,?,?)",
            UUID.randomUUID(),
            tenant.id(),
            access.activeStore().getId(),
            id,
            row.number(),
            issue.substring(0, Math.min(240, issue.length())));
        count++;
      }
    }
    jdbc.update(
        "UPDATE import_jobs SET status=?,error_count=? WHERE tenant_id=? AND store_id=? AND id=?",
        count == 0 ? "PREVIEW" : "REJECTED",
        count,
        tenant.id(),
        access.activeStore().getId(),
        id);
    return count;
  }

  private ProductForm form(List<String> values, UUID categoryId) {
    return new ProductForm(
        values.get(1),
        values.get(2),
        values.get(3),
        categoryId,
        values.get(5),
        true,
        values.get(9),
        values.get(10),
        0);
  }

  private List<ImportFileReader.Row> rawRows(UUID id) {
    return jdbc.query(
        "SELECT * FROM import_rows WHERE tenant_id=? AND store_id=? AND job_id=? ORDER BY row_number",
        (rs, n) ->
            new ImportFileReader.Row(
                rs.getInt("row_number"),
                List.of(
                    rs.getString("template_version"),
                    rs.getString("sku"),
                    rs.getString("name"),
                    rs.getString("barcode"),
                    rs.getString("category"),
                    rs.getString("unit"),
                    rs.getString("price"),
                    rs.getString("initial_quantity"),
                    rs.getString("minimum_quantity"),
                    rs.getString("ncm"),
                    rs.getString("cest")),
                List.of()),
        tenant.id(),
        access.activeStore().getId(),
        id);
  }

  private HashMap<Integer, List<String>> errors(UUID id) {
    var result = new HashMap<Integer, List<String>>();
    jdbc.query(
        "SELECT row_number,message FROM import_row_errors WHERE tenant_id=? AND store_id=? AND job_id=? ORDER BY row_number,message",
        rs -> {
          result
              .computeIfAbsent(rs.getInt("row_number"), key -> new ArrayList<>())
              .add(rs.getString("message"));
        },
        tenant.id(),
        access.activeStore().getId(),
        id);
    return result;
  }

  private Job job(UUID id, boolean lock) {
    return jdbc
        .query(
            "SELECT * FROM import_jobs WHERE tenant_id=? AND store_id=? AND actor_id=? AND id=?"
                + (lock ? " FOR UPDATE" : ""),
            this::mapJob,
            tenant.id(),
            access.activeStore().getId(),
            access.principal().userId(),
            id)
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new AccessDeniedException("Importação indisponível nesta filial e conta."));
  }

  private Job mapJob(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
    return new Job(
        rs.getObject("id", UUID.class),
        rs.getString("filename"),
        rs.getString("status"),
        rs.getInt("row_count"),
        rs.getInt("error_count"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("expires_at").toInstant());
  }
}
