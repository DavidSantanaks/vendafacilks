package br.com.vendafacil.shared.config;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import br.com.vendafacil.tenant.TenantContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
  private final TenantContext tenant;
  private final AccessService access;
  private final DashboardService dashboard;

  public PageController(TenantContext tenant, AccessService access, DashboardService dashboard) {
    this.tenant = tenant;
    this.access = access;
    this.dashboard = dashboard;
  }

  @GetMapping("/login")
  public String login() {
    return "auth/login";
  }

  @GetMapping("/")
  public String home(Model model) {
    if (tenant.current().platform()) {
      return "redirect:/admin/empresas";
    }
    var overview = dashboard.overview();
    model.addAttribute("storeCount", overview.storeCount());
    model.addAttribute("roleNames", overview.roleNames());
    model.addAttribute("permissionCount", overview.permissionCount());
    model.addAttribute("events", overview.events());
    return "dashboard";
  }

  @GetMapping("/configuracoes")
  public String settings() {
    access.require(Permission.USER_MANAGE);
    return "settings";
  }
}
