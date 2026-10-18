package br.com.vendafacil.administration;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.MembershipRepository;
import br.com.vendafacil.identity.UserRepository;
import br.com.vendafacil.identity.recovery.PasswordRecoveryService;
import br.com.vendafacil.identity.recovery.RecoveryScope;
import br.com.vendafacil.shared.audit.AuditService;
import br.com.vendafacil.shared.config.PortalUrls;
import br.com.vendafacil.tenant.Tenant;
import br.com.vendafacil.tenant.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUserService {
  public record CompanyLink(UUID id, String name, String url, boolean active) {}

  public record UserView(
      UUID id,
      String name,
      String email,
      boolean active,
      boolean platformAdmin,
      boolean self,
      List<CompanyLink> companies) {}

  private final AccessService access;
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final TenantRepository tenants;
  private final PortalUrls urls;
  private final AuditService audit;
  private final PasswordRecoveryService recovery;

  public PlatformUserService(
      AccessService access,
      UserRepository users,
      MembershipRepository memberships,
      TenantRepository tenants,
      PortalUrls urls,
      AuditService audit,
      PasswordRecoveryService recovery) {
    this.access = access;
    this.users = users;
    this.memberships = memberships;
    this.tenants = tenants;
    this.urls = urls;
    this.audit = audit;
    this.recovery = recovery;
  }

  @Transactional(readOnly = true)
  public Page<UserView> list(String query, int page, UUID tenantId) {
    access.requirePlatformAdmin();
    var search =
        "%"
            + query.trim().toLowerCase(java.util.Locale.ROOT).replace("%", "").replace("_", "")
            + "%";
    var pageable = PageRequest.of(Math.max(0, page), 20);
    var result =
        tenantId == null
            ? users.search(search, pageable)
            : users.searchByTenant(search, tenantId, pageable);
    return result.map(
        user ->
            new UserView(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.isActive(),
                user.isPlatformAdmin(),
                user.getId().equals(access.principal().userId()),
                memberships.findAllByUserId(user.getId()).stream()
                    .map(
                        membership -> {
                          var tenant = tenants.findById(membership.getTenantId()).orElseThrow();
                          return new CompanyLink(
                              tenant.getId(),
                              tenant.getName(),
                              urls.login(tenant.getSlug()),
                              membership.isActive() && tenant.isActive());
                        })
                    .toList()));
  }

  @Transactional
  public void update(UUID id, String name, boolean active) {
    access.requirePlatformAdmin();
    if (name == null || name.isBlank() || name.length() > 120) {
      throw new IllegalArgumentException("Informe um nome de até 120 caracteres.");
    }
    var user =
        users
            .findLockedById(id)
            .orElseThrow(() -> new IllegalArgumentException("Usuário indisponível."));
    if (!active
        && (id.equals(access.principal().userId())
            || (user.isPlatformAdmin()
                && user.isActive()
                && users.countByPlatformAdminTrueAndActiveTrue() <= 1))) {
      throw new IllegalArgumentException(
          "Mantenha sua conta e ao menos um administrador da plataforma ativos.");
    }
    user.update(name.trim(), active);
    audit.record(
        null,
        null,
        access.principal().userId(),
        active ? "PLATFORM_USER_UPDATED" : "PLATFORM_USER_BLOCKED",
        id);
  }

  @Transactional
  public void recover(UUID userId, UUID tenantId) {
    access.requirePlatformAdmin();
    var user =
        users
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Usuário indisponível."));
    RecoveryScope scope;
    if (tenantId == null) {
      if (!user.isPlatformAdmin()) {
        throw new AccessDeniedException("Selecione uma empresa vinculada ao usuário.");
      }
      scope = new RecoveryScope(null, true, "admin", "Administração da plataforma");
    } else {
      if (memberships.findByTenantIdAndUserIdAndActiveTrue(tenantId, userId).isEmpty()) {
        throw new AccessDeniedException("Vínculo não autorizado.");
      }
      var tenant =
          tenants
              .findById(tenantId)
              .filter(Tenant::isActive)
              .orElseThrow(() -> new IllegalArgumentException("Empresa indisponível."));
      scope = new RecoveryScope(tenantId, false, tenant.getSlug(), tenant.getName());
    }
    recovery.request(user.getEmail(), scope);
    audit.record(
        tenantId, null, access.principal().userId(), "PLATFORM_RECOVERY_REQUESTED", userId);
  }
}
