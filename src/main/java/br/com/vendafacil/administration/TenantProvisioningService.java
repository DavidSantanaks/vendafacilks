package br.com.vendafacil.administration;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.DefaultRoles;
import br.com.vendafacil.identity.Membership;
import br.com.vendafacil.identity.MembershipRepository;
import br.com.vendafacil.identity.StoreRoleGrant;
import br.com.vendafacil.identity.StoreRoleRepository;
import br.com.vendafacil.identity.UserAccount;
import br.com.vendafacil.identity.UserRepository;
import br.com.vendafacil.shared.audit.AuditService;
import br.com.vendafacil.shared.config.PortalUrls;
import br.com.vendafacil.shared.security.PasswordPolicy;
import br.com.vendafacil.store.Store;
import br.com.vendafacil.store.StoreRepository;
import br.com.vendafacil.tenant.Tenant;
import br.com.vendafacil.tenant.TenantRepository;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantProvisioningService {
  public static final Set<String> RESERVED =
      Set.of("admin", "www", "api", "mail", "static", "support", "status");
  private final TenantRepository tenants;
  private final StoreRepository stores;
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final StoreRoleRepository grants;
  private final DefaultRoles defaultRoles;
  private final PasswordEncoder encoder;
  private final AccessService access;
  private final AuditService audit;
  private final PortalUrls urls;

  public record CompanyView(
      UUID id,
      String name,
      String slug,
      boolean active,
      long storeCount,
      long userCount,
      String portalUrl) {}

  public TenantProvisioningService(
      TenantRepository tenants,
      StoreRepository stores,
      UserRepository users,
      MembershipRepository memberships,
      StoreRoleRepository grants,
      DefaultRoles defaultRoles,
      PasswordEncoder encoder,
      AccessService access,
      AuditService audit,
      PortalUrls urls) {
    this.tenants = tenants;
    this.stores = stores;
    this.users = users;
    this.memberships = memberships;
    this.grants = grants;
    this.defaultRoles = defaultRoles;
    this.encoder = encoder;
    this.access = access;
    this.audit = audit;
    this.urls = urls;
  }

  @Transactional
  public void create(TenantRegistration form) {
    access.requirePlatformAdmin();
    PasswordPolicy.validate(form.password());
    var email = form.email().trim().toLowerCase(Locale.ROOT);
    if (RESERVED.contains(form.slug()) || tenants.existsBySlug(form.slug())) {
      throw new IllegalArgumentException("Este subdomínio está reservado ou já está em uso.");
    }
    if (users.existsByEmail(email)) {
      throw new IllegalArgumentException(
          "Use um novo e-mail para o administrador inicial. Vínculos existentes exigem um convite verificado.");
    }
    var tenant = tenants.saveAndFlush(new Tenant(form.slug(), form.name().trim()));
    var store = stores.saveAndFlush(new Store(tenant.getId(), "Matriz", "A definir", "SP"));
    var role = defaultRoles.create(tenant.getId()).getFirst();
    var user =
        users.saveAndFlush(
            new UserAccount(
                email, form.adminName().trim(), encoder.encode(form.password()), false));
    var membership = memberships.saveAndFlush(new Membership(tenant.getId(), user.getId()));
    grants.save(
        new StoreRoleGrant(tenant.getId(), membership.getId(), store.getId(), role.getId()));
    audit.record(
        tenant.getId(), null, access.principal().userId(), "TENANT_CREATED", tenant.getId());
  }

  @Transactional(readOnly = true)
  public java.util.List<CompanyView> list() {
    access.requirePlatformAdmin();
    return tenants.findAllByOrderByNameAsc().stream()
        .map(
            tenant ->
                new CompanyView(
                    tenant.getId(),
                    tenant.getName(),
                    tenant.getSlug(),
                    tenant.isActive(),
                    stores.countByTenantIdAndActiveTrue(tenant.getId()),
                    memberships.countByTenantIdAndActiveTrue(tenant.getId()),
                    urls.login(tenant.getSlug())))
        .toList();
  }

  @Transactional
  public void changeStatus(UUID id, boolean active) {
    access.requirePlatformAdmin();
    var tenant =
        tenants
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Empresa indisponível."));
    tenant.setActive(active);
    audit.record(
        id,
        null,
        access.principal().userId(),
        active ? "TENANT_ACTIVATED" : "TENANT_SUSPENDED",
        id);
  }

  @Transactional
  public void rename(UUID id, String name) {
    access.requirePlatformAdmin();
    if (name == null || name.isBlank() || name.length() > 120) {
      throw new IllegalArgumentException("Informe um nome de até 120 caracteres.");
    }
    var tenant =
        tenants
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Empresa indisponível."));
    tenant.rename(name.trim());
    audit.record(id, null, access.principal().userId(), "TENANT_UPDATED", id);
  }
}
