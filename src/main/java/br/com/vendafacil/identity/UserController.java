package br.com.vendafacil.identity;

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
public class UserController {
  private final UserManagementService service;

  public UserController(UserManagementService service) {
    this.service = service;
  }

  @GetMapping("/usuarios")
  public String list(Model model) {
    model.addAttribute("users", service.list());
    model.addAttribute("roles", service.assignableRoles());
    if (!model.containsAttribute("userForm")) {
      model.addAttribute("userForm", new UserForm("", "", "", null));
    }
    return "identity/users";
  }

  @PostMapping("/usuarios")
  public String create(
      @Valid UserForm userForm, BindingResult errors, Model model, RedirectAttributes redirect) {
    if (errors.hasErrors()) {
      return list(model);
    }
    try {
      service.create(userForm);
    } catch (IllegalArgumentException exception) {
      errors.reject("user", exception.getMessage());
      return list(model);
    }
    redirect.addFlashAttribute("success", "Usuário criado com acesso à filial ativa.");
    return "redirect:/usuarios";
  }

  @PostMapping("/usuarios/acessos/{id}")
  public String update(
      @PathVariable UUID id,
      @RequestParam UUID roleId,
      @RequestParam(defaultValue = "false") boolean active,
      RedirectAttributes redirect) {
    service.update(id, roleId, active);
    redirect.addFlashAttribute("success", "Acesso atualizado nesta filial.");
    return "redirect:/usuarios";
  }

  @PostMapping("/usuarios/acessos/{id}/recuperacao")
  public String recovery(@PathVariable UUID id, RedirectAttributes redirect) {
    service.recover(id);
    redirect.addFlashAttribute(
        "success",
        "Recuperação solicitada para o e-mail cadastrado. A senha será escolhida pela própria pessoa.");
    return "redirect:/usuarios";
  }
}
