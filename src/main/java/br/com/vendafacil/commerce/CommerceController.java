package br.com.vendafacil.commerce;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class CommerceController {
  private final SaleService sales;
  private final CashService cash;
  private final AccessService access;
  private final SalesReportService reports;

  public CommerceController(
      SaleService sales, CashService cash, AccessService access, SalesReportService reports) {
    this.sales = sales;
    this.cash = cash;
    this.access = access;
    this.reports = reports;
  }

  @GetMapping("/pdv")
  public String pos(Model model) {
    access.require(Permission.SALE_CREATE);
    model.addAttribute("salesEnabled", sales.demoSalesEnabled());
    model.addAttribute("cashSession", cash.current());
    return "commerce/pos";
  }

  @GetMapping({"/caixa", "/caixa/{id}"})
  public String cash(@PathVariable(required = false) UUID id, Model model) {
    access.require(Permission.CASH_OPEN);
    model.addAttribute("registers", cash.registers());
    model.addAttribute("sessions", cash.sessions());
    model.addAttribute("ownSession", cash.current());
    var detail = id == null ? null : cash.detail(id);
    model.addAttribute("cashDetail", detail);
    model.addAttribute("cashPolicy", cash.policy());
    model.addAttribute(
        "ownDetail",
        detail != null && detail.session().operatorId().equals(access.principal().userId()));
    return "commerce/cash";
  }

  @GetMapping("/vendas/{id}/recibo")
  public String receipt(@PathVariable UUID id, Model model) {
    model.addAttribute("sale", sales.receipt(id));
    return "commerce/receipt";
  }

  @GetMapping("/relatorios/vendas")
  public String report(
      @RequestParam(required = false) LocalDate from,
      @RequestParam(required = false) LocalDate to,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    if (from == null) from = LocalDate.now(SalesReportService.ZONE);
    if (to == null) to = from;
    model.addAttribute("report", reports.report(from, to, page));
    model.addAttribute("from", from);
    model.addAttribute("to", to);
    return "commerce/report";
  }

  @GetMapping("/relatorios/vendas.csv")
  @ResponseBody
  public ResponseEntity<String> csv(@RequestParam LocalDate from, @RequestParam LocalDate to) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header("Content-Disposition", "attachment; filename=\"vendas.csv\"")
        .header("Content-Type", "text/csv; charset=UTF-8")
        .body(reports.csv(from, to));
  }

  @GetMapping("/api/pdv/produtos")
  @ResponseBody
  public List<SaleService.Search> search(@RequestParam(defaultValue = "") String q) {
    return sales.search(q);
  }

  @PostMapping("/api/pdv/previsao")
  @ResponseBody
  public SaleService.Quote quote(@RequestBody SaleService.Checkout input) {
    return sales.quote(input);
  }

  @PostMapping("/api/pdv/vendas")
  @ResponseBody
  public Map<String, UUID> checkout(@RequestBody SaleService.Checkout input) {
    return Map.of("id", sales.checkout(input));
  }

  @GetMapping("/api/caixa/{id}")
  @ResponseBody
  public CashService.Detail detail(@PathVariable UUID id) {
    return cash.detail(id);
  }

  @PostMapping("/api/caixa/registros")
  @ResponseBody
  public Map<String, UUID> register(@RequestBody Map<String, String> input) {
    return Map.of("id", cash.createRegister(input.get("name")));
  }

  @PostMapping("/api/caixa/sessoes")
  @ResponseBody
  public Map<String, UUID> open(@RequestBody CashService.Open input) {
    return Map.of("id", cash.open(input));
  }

  @PostMapping("/api/caixa/{id}/movimentos")
  @ResponseBody
  public Map<String, Boolean> move(@PathVariable UUID id, @RequestBody CashService.Move input) {
    cash.move(id, input);
    return Map.of("ok", true);
  }

  @PostMapping("/api/caixa/{id}/contagem")
  @ResponseBody
  public Map<String, Boolean> declare(
      @PathVariable UUID id, @RequestBody CashService.Declare input) {
    cash.declare(id, input);
    return Map.of("ok", true);
  }

  @PostMapping("/api/caixa/{id}/fechamento")
  @ResponseBody
  public Map<String, Boolean> close(@PathVariable UUID id, @RequestBody CashService.Close input) {
    cash.close(id, input);
    return Map.of("ok", true);
  }

  @PostMapping("/api/caixa/politicas")
  @ResponseBody
  public Map<String, Boolean> policy(@RequestBody CashService.PolicyInput input) {
    cash.policy(input);
    return Map.of("ok", true);
  }
}
