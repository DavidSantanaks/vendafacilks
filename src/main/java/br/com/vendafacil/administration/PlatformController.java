package br.com.vendafacil.administration;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PlatformController {
  private final TenantProvisioningService service;

  public PlatformController(TenantProvisioningService service) {
    this.service = service;
  }

  @GetMapping("/admin/empresas")
  public String list(Model model) {
    model.addAttribute("companies", service.list());
    if (!model.containsAttribute("tenantRegistration")) {
      model.addAttribute("tenantRegistration", new TenantRegistration("", "", "", "", ""));
    }
    return "admin/tenants";
  }

  @PostMapping("/admin/empresas")
  public String create(
      @Valid TenantRegistration tenantRegistration,
      BindingResult errors,
      Model model,
      RedirectAttributes redirect) {
    if (errors.hasErrors()) {
      return list(model);
    }
    try {
      service.create(tenantRegistration);
    } catch (IllegalArgumentException exception) {
      errors.reject("registration", exception.getMessage());
      return list(model);
    }
    redirect.addFlashAttribute("success", "Empresa e administrador inicial criados.");
    return "redirect:/admin/empresas";
  }

  @PostMapping("/admin/empresas/{id}/status")
  public String status(
      @PathVariable UUID id, @RequestParam boolean active, RedirectAttributes redirect) {
    service.changeStatus(id, active);
    redirect.addFlashAttribute(
        "success", active ? "Empresa ativada." : "Empresa suspensa. O histórico foi preservado.");
    return "redirect:/admin/empresas";
  }

  @PostMapping("/admin/empresas/{id}")
  public String rename(
      @PathVariable UUID id, @RequestParam String name, RedirectAttributes redirect) {
    service.rename(id, name);
    redirect.addFlashAttribute(
        "success", "Dados da empresa atualizados. O endereço foi preservado.");
    return "redirect:/admin/empresas";
  }
}
