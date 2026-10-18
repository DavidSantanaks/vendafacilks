package br.com.vendafacil.identity.recovery;

import br.com.vendafacil.shared.security.LoginThrottle;
import br.com.vendafacil.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordRecoveryController {
  public record RecoveryForm(
      @NotBlank @Email(message = "Informe um e-mail válido.") @Size(max = 254) String email) {}

  public record ResetForm(
      @NotBlank String token,
      @NotBlank @Size(min = 12, max = 64, message = "Use de 12 a 64 caracteres.") String password,
      @NotBlank String confirmation) {}

  private static final Logger LOG = LoggerFactory.getLogger(PasswordRecoveryController.class);
  private final TenantContext tenant;
  private final RecoveryDispatcher dispatcher;
  private final PasswordRecoveryService service;
  private final LoginThrottle throttle;

  public PasswordRecoveryController(
      TenantContext tenant,
      RecoveryDispatcher dispatcher,
      PasswordRecoveryService service,
      LoginThrottle throttle) {
    this.tenant = tenant;
    this.dispatcher = dispatcher;
    this.service = service;
    this.throttle = throttle;
  }

  @GetMapping("/recuperar-senha")
  public String forgot(Model model) {
    if (!model.containsAttribute("recoveryForm")) {
      model.addAttribute("recoveryForm", new RecoveryForm(""));
    }
    return "auth/forgot-password";
  }

  @PostMapping("/recuperar-senha")
  public String request(
      @Valid RecoveryForm recoveryForm,
      BindingResult errors,
      Model model,
      HttpServletRequest request,
      RedirectAttributes redirect) {
    if (errors.hasErrors()) {
      return forgot(model);
    }
    var email = recoveryForm.email().trim().toLowerCase(Locale.ROOT);
    var ipKey = "recovery-ip:" + tenant.current().slug() + ":" + request.getRemoteAddr();
    var accountKey = "recovery-account:" + PasswordRecoveryService.hash(email);
    try {
      throttle.check(ipKey);
      throttle.check(accountKey);
      throttle.failure(ipKey);
      throttle.failure(accountKey);
      dispatcher.request(email, RecoveryScope.from(tenant.current()));
    } catch (LockedException | TaskRejectedException exception) {
      LOG.warn(
          "Solicitação de recuperação limitada: category={}", exception.getClass().getSimpleName());
    }
    redirect.addFlashAttribute("recoveryRequested", true);
    return "redirect:/recuperar-senha";
  }

  @GetMapping("/redefinir-senha")
  public String resetPage(
      @RequestParam(defaultValue = "") String token, Model model, HttpServletResponse response) {
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("Cache-Control", "no-store");
    model.addAttribute("tokenValid", service.valid(token, RecoveryScope.from(tenant.current())));
    if (!model.containsAttribute("resetForm")) {
      model.addAttribute("resetForm", new ResetForm(token, "", ""));
    }
    return "auth/reset-password";
  }

  @PostMapping("/redefinir-senha")
  public String reset(
      @Valid ResetForm resetForm,
      BindingResult errors,
      Model model,
      HttpServletRequest request,
      HttpServletResponse response) {
    var key = "reset:" + tenant.current().slug() + ":" + request.getRemoteAddr();
    try {
      throttle.check(key);
      throttle.failure(key);
    } catch (LockedException exception) {
      errors.reject("limit", "Muitas tentativas. Aguarde 15 minutos antes de continuar.");
    }
    if (errors.hasErrors()) {
      return resetPage(resetForm.token(), model, response);
    }
    try {
      service.reset(
          resetForm.token(),
          resetForm.password(),
          resetForm.confirmation(),
          RecoveryScope.from(tenant.current()));
    } catch (IllegalArgumentException exception) {
      errors.reject("reset", exception.getMessage());
      return resetPage(resetForm.token(), model, response);
    }
    if (request.getSession(false) != null) {
      request.getSession(false).invalidate();
    }
    return "redirect:/login?senhaAlterada";
  }
}
