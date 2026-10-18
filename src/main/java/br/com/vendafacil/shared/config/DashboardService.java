package br.com.vendafacil.shared.config;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import br.com.vendafacil.shared.audit.AuditLog;
import br.com.vendafacil.shared.audit.AuditRepository;
import br.com.vendafacil.tenant.TenantContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
  public record Overview(
      int storeCount, String roleNames, int permissionCount, List<AuditLog> events) {}

  private final AccessService access;
  private final TenantContext tenant;
  private final AuditRepository audit;

  public DashboardService(AccessService access, TenantContext tenant, AuditRepository audit) {
    this.access = access;
    this.tenant = tenant;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public Overview overview() {
    var store = access.activeStore();
    return new Overview(
        access.authorizedStores().size(),
        String.join(", ", access.activeRoles().stream().map(role -> role.getName()).toList()),
        access.permissions().size(),
        access.permissions().contains(Permission.USER_MANAGE)
            ? audit.findTop8ByTenantIdAndStoreIdOrderByCreatedAtDesc(tenant.id(), store.getId())
            : List.of());
  }
}
