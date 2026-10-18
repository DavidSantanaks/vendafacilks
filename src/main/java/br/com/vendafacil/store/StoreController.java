package br.com.vendafacil.store;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StoreController {
  private final StoreService service;
  private final AccessService access;

  public StoreController(StoreService service, AccessService access) {
    this.service = service;
    this.access = access;
  }

  @GetMapping("/filiais")
  public String choose(Model model) {
    model.addAttribute("stores", service.available());
    return "stores/select";
  }

  @PostMapping("/filiais/ativa")
  public String select(@RequestParam UUID storeId, HttpSession session) {
    service.select(storeId);
    session.setAttribute(AccessService.ACTIVE_STORE, storeId);
    return "redirect:/";
  }

  @GetMapping("/configuracoes/filiais")
  public String manage(Model model) {
    access.require(Permission.USER_MANAGE);
    model.addAttribute("stores", service.manageable());
    if (!model.containsAttribute("storeForm")) {
      model.addAttribute("storeForm", new StoreForm("", "", "SP", true));
    }
    return "stores/manage";
  }

  @PostMapping("/configuracoes/filiais")
  public String create(
      @Valid StoreForm storeForm, BindingResult errors, Model model, RedirectAttributes redirect) {
    access.require(Permission.USER_MANAGE);
    if (errors.hasErrors()) {
      return manage(model);
    }
    service.create(storeForm);
    redirect.addFlashAttribute(
        "success", "Filial criada. Seus acessos foram preservados na nova unidade.");
    return "redirect:/configuracoes/filiais";
  }

  @PostMapping("/configuracoes/filiais/{id}")
  public String update(
      @PathVariable UUID id,
      @Valid StoreForm form,
      BindingResult errors,
      RedirectAttributes redirect) {
    if (errors.hasErrors()) {
      throw new IllegalArgumentException("Confira nome, cidade e UF da filial.");
    }
    service.update(id, form);
    redirect.addFlashAttribute("success", "Filial atualizada.");
    return "redirect:/filiais";
  }

  public record StoreView(UUID id, String name, String city, String state) {}

  @GetMapping("/api/v1/filiais")
  @ResponseBody
  public List<StoreView> list() {
    return service.available().stream()
        .map(
            store ->
                new StoreView(store.getId(), store.getName(), store.getCity(), store.getState()))
        .toList();
  }

  @GetMapping("/api/v1/filiais/{id}")
  @ResponseBody
  public StoreView detail(@PathVariable UUID id) {
    var store = access.requireStore(id);
    return new StoreView(store.getId(), store.getName(), store.getCity(), store.getState());
  }

  @GetMapping("/api/v1/filiais/exportacao")
  public ResponseEntity<String> download() {
    access.require(Permission.USER_MANAGE);
    var csv = new StringBuilder("\uFEFFNome;Cidade;UF\r\n");
    for (var store : service.available()) {
      csv.append(csvCell(store.getName()))
          .append(';')
          .append(csvCell(store.getCity()))
          .append(';')
          .append(store.getState())
          .append("\r\n");
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=filiais.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(csv.toString());
  }

  private String csvCell(String value) {
    var safe = value.matches("^[=+@\\-\\t\\r].*") ? "'" + value : value;
    return "\"" + safe.replace("\"", "\"\"") + "\"";
  }
}
