package br.com.vendafacil.administration;

import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PlatformUserController {
  private final PlatformUserService service;
  private final TenantProvisioningService tenants;

  public PlatformUserController(PlatformUserService service, TenantProvisioningService tenants) {
    this.service = service;
    this.tenants = tenants;
  }

  @GetMapping("/admin/usuarios")
  public String users(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(required = false) UUID empresa,
      Model model) {
    model.addAttribute("userPage", service.list(q, page, empresa));
    model.addAttribute("companies", tenants.list());
    model.addAttribute("selectedCompany", empresa);
    model.addAttribute("query", q);
    return "admin/users";
  }

  @PostMapping("/admin/usuarios/{id}")
  public String update(
      @PathVariable UUID id,
      @RequestParam String name,
      @RequestParam(defaultValue = "false") boolean active,
      RedirectAttributes redirect) {
    service.update(id, name, active);
    redirect.addFlashAttribute("success", "Cadastro do usuário atualizado.");
    return "redirect:/admin/usuarios";
  }

  @PostMapping("/admin/usuarios/{id}/recuperacao")
  public String recover(
      @PathVariable UUID id,
      @RequestParam(required = false) UUID tenantId,
      RedirectAttributes redirect) {
    service.recover(id, tenantId);
    redirect.addFlashAttribute("success", "Recuperação solicitada para o e-mail cadastrado.");
    return "redirect:/admin/usuarios";
  }
}
