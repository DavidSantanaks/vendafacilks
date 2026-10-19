package br.com.vendafacil.commerce;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import br.com.vendafacil.shared.validation.CsvFiles;
import br.com.vendafacil.shared.validation.DataPage;
import br.com.vendafacil.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SalesReportService {
  public record Row(
      UUID id,
      String operator,
      String customer,
      BigDecimal total,
      BigDecimal discount,
      OffsetDateTime createdAt) {}

  public record Method(String method, BigDecimal amount) {}

  public record Unit(String unit, BigDecimal quantity) {}

  public record Report(
      DataPage<Row> sales,
      BigDecimal revenue,
      BigDecimal discounts,
      List<Method> methods,
      List<Unit> quantities) {}

  public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
  private final JdbcTemplate jdbc;
  private final AccessService access;
  private final TenantContext tenant;

  public SalesReportService(JdbcTemplate jdbc, AccessService access, TenantContext tenant) {
    this.jdbc = jdbc;
    this.access = access;
    this.tenant = tenant;
  }

  private Object[] args(LocalDate from, LocalDate to) {
    access.require(Permission.REPORT_VIEW);
    if (from == null || to == null || to.isBefore(from) || to.isAfter(from.plusDays(92)))
      throw new IllegalArgumentException("Selecione um período de até 93 dias.");
    return new Object[] {
      tenant.id(),
      access.activeStore().getId(),
      from.atStartOfDay(ZONE).toOffsetDateTime(),
      to.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime()
    };
  }

  private static final String WHERE =
      " s.tenant_id=? AND s.store_id=? AND s.created_at>=? AND s.created_at<?";

  public Report report(LocalDate from, LocalDate to, int page) {
    var args = args(from, to);
    var count = jdbc.queryForObject("SELECT count(*) FROM sales s WHERE" + WHERE, Long.class, args);
    int number = Math.max(0, Math.min(page, (int) Math.max(0, (count - 1) / 20)));
    var rows = rows(args, 20, number * 20);
    var total =
        jdbc.queryForObject(
            "SELECT coalesce(sum(total),0) FROM sales s WHERE" + WHERE, BigDecimal.class, args);
    var discounts =
        jdbc.queryForObject(
            "SELECT coalesce(sum(discount),0) FROM sales s WHERE" + WHERE, BigDecimal.class, args);
    var methods =
        jdbc.query(
            "SELECT p.method,sum(p.amount-CASE WHEN p.method='CASH' THEN s.change ELSE 0 END) AS amount FROM sales s JOIN sale_payments p ON p.tenant_id=s.tenant_id AND p.store_id=s.store_id AND p.sale_id=s.id WHERE"
                + WHERE
                + " GROUP BY p.method ORDER BY p.method",
            (rs, n) -> new Method(rs.getString(1), rs.getBigDecimal(2)),
            args);
    var quantities =
        jdbc.query(
            "SELECT i.unit,sum(i.quantity) FROM sales s JOIN sale_items i ON i.tenant_id=s.tenant_id AND i.store_id=s.store_id AND i.sale_id=s.id WHERE"
                + WHERE
                + " GROUP BY i.unit ORDER BY i.unit",
            (rs, n) -> new Unit(rs.getString(1), rs.getBigDecimal(2)),
            args);
    return new Report(
        new DataPage<>(rows, number, count, 20), total, discounts, methods, quantities);
  }

  public String csv(LocalDate from, LocalDate to) {
    var args = args(from, to);
    var count = jdbc.queryForObject("SELECT count(*) FROM sales s WHERE" + WHERE, Long.class, args);
    if (count > 10000)
      throw new IllegalArgumentException(
          "A exportação aceita até 10.000 vendas. Reduza o período.");
    var out = new StringBuilder("\uFEFF");
    out.append(
        CsvFiles.line(
            List.of(
                "Venda",
                "Data (São Paulo)",
                "Operador",
                "Cliente",
                "Total BRL",
                "Desconto BRL",
                "Fiscal",
                "Ambiente")));
    for (var row : rows(args, 10000, 0))
      out.append(
          CsvFiles.line(
              List.of(
                  row.id().toString(),
                  row.createdAt().atZoneSameInstant(ZONE).toString(),
                  row.operator(),
                  row.customer(),
                  row.total().toPlainString(),
                  row.discount().toPlainString(),
                  "NÃO EMITIDO",
                  "DEMONSTRAÇÃO")));
    return out.toString();
  }

  private List<Row> rows(Object[] args, int limit, int offset) {
    var params = new java.util.ArrayList<Object>(List.of(args));
    params.add(limit);
    params.add(offset);
    return jdbc.query(
        "SELECT s.*,u.name AS operator_name FROM sales s JOIN users u ON u.id=s.actor_id WHERE"
            + WHERE
            + " ORDER BY s.created_at DESC,s.id LIMIT ? OFFSET ?",
        (rs, n) ->
            new Row(
                rs.getObject("id", UUID.class),
                rs.getString("operator_name"),
                rs.getString("customer"),
                rs.getBigDecimal("total"),
                rs.getBigDecimal("discount"),
                rs.getObject("created_at", OffsetDateTime.class)),
        params.toArray());
  }
}
