package br.com.vendafacil.shared.config;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import br.com.vendafacil.identity.StoreSelectionRequired;
import br.com.vendafacil.shared.security.TenantPrincipal;
import br.com.vendafacil.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class ViewContext {
  private final AccessService access;
  private final AppProperties properties;

  public ViewContext(AccessService access, AppProperties properties) {
    this.access = access;
    this.properties = properties;
  }

  @ModelAttribute
  public void enrich(Model model, HttpServletRequest request) {
    model.addAttribute("tenant", request.getAttribute(TenantContext.ATTRIBUTE));
    model.addAttribute("baseDomain", properties.baseDomain());
    model.addAttribute("demoEnabled", properties.demoEnabled());
    model.addAttribute("currentPath", request.getRequestURI());
    model.addAttribute("correlationId", request.getAttribute("correlationId"));
    model.addAttribute("canManage", false);
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
      return;
    }
    model.addAttribute("currentUser", principal);
    if (principal.platformAdmin()) {
      return;
    }
    try {
      model.addAttribute("activeStore", access.activeStore());
      model.addAttribute("canManage", access.permissions().contains(Permission.USER_MANAGE));
    } catch (StoreSelectionRequired ignored) {
      // A seleção de filial e as páginas de erro também usam o layout comum.
    }
  }
}
