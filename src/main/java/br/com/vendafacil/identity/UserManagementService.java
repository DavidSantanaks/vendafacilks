package br.com.vendafacil.identity;

import br.com.vendafacil.identity.recovery.PasswordRecoveryService;
import br.com.vendafacil.identity.recovery.RecoveryScope;
import br.com.vendafacil.shared.audit.AuditService;
import br.com.vendafacil.shared.security.PasswordPolicy;
import br.com.vendafacil.tenant.TenantContext;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {
  public record UserView(
      UUID grantId,
      UUID userId,
      String name,
      String email,
      String role,
      UUID roleId,
      boolean active,
      boolean editable) {}

  private final AccessService access;
  private final TenantContext tenant;
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final RoleRepository roles;
  private final StoreRoleRepository grants;
  private final PasswordEncoder encoder;
  private final AuditService audit;
  private final PasswordRecoveryService recovery;

  public UserManagementService(
      AccessService access,
      TenantContext tenant,
      UserRepository users,
      MembershipRepository memberships,
      RoleRepository roles,
      StoreRoleRepository grants,
      PasswordEncoder encoder,
      AuditService audit,
      PasswordRecoveryService recovery) {
    this.access = access;
    this.tenant = tenant;
    this.users = users;
    this.memberships = memberships;
    this.roles = roles;
    this.grants = grants;
    this.encoder = encoder;
    this.audit = audit;
    this.recovery = recovery;
  }

  @Transactional(readOnly = true)
  public List<Role> assignableRoles() {
    access.require(Permission.USER_MANAGE);
    var permissions = access.permissions();
    var limit =
        access.activeRoles().stream()
            .map(Role::getDiscountLimit)
            .max(java.math.BigDecimal::compareTo)
            .orElseThrow();
    return roles.findAllByTenantIdOrderByNameAsc(tenant.id()).stream()
        .filter(
            role ->
                permissions.containsAll(role.getPermissions())
                    && role.getDiscountLimit().compareTo(limit) <= 0)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<UserView> list() {
    access.require(Permission.USER_MANAGE);
    var storeId = access.activeStore().getId();
    var assignable = assignableRoles().stream().map(Role::getId).toList();
    return memberships.findAllByTenantIdAndActiveTrue(tenant.id()).stream()
        .flatMap(
            membership ->
                grants
                    .findAllByTenantIdAndMembershipIdAndStoreId(
                        tenant.id(), membership.getId(), storeId)
                    .stream()
                    .map(
                        grant -> {
                          var user = users.findById(membership.getUserId()).orElseThrow();
                          var role =
                              roles
                                  .findByTenantIdAndId(tenant.id(), grant.getRoleId())
                                  .orElseThrow();
                          return new UserView(
                              grant.getId(),
                              user.getId(),
                              user.getName(),
                              user.getEmail(),
                              role.getName(),
                              role.getId(),
                              grant.isActive() && user.isActive(),
                              !user.getId().equals(access.principal().userId())
                                  && assignable.contains(role.getId()));
                        }))
        .toList();
  }

  @Transactional
  public void create(UserForm form) {
    PasswordPolicy.validate(form.password());
    var role =
        assignableRoles().stream()
            .filter(candidate -> candidate.getId().equals(form.roleId()))
            .findFirst()
            .orElseThrow(() -> new AccessDeniedException("Perfil não autorizado."));
    var email = form.email().trim().toLowerCase(Locale.ROOT);
    if (users.existsByEmail(email)) {
      throw new IllegalArgumentException(
          "E-mail indisponível. Use um novo e-mail para este cadastro.");
    }
    var user =
        users.saveAndFlush(
            new UserAccount(email, form.name().trim(), encoder.encode(form.password()), false));
    var membership = memberships.saveAndFlush(new Membership(tenant.id(), user.getId()));
    grants.save(
        new StoreRoleGrant(
            tenant.id(), membership.getId(), access.activeStore().getId(), role.getId()));
    audit.record(
        tenant.id(),
        access.activeStore().getId(),
        access.principal().userId(),
        "USER_CREATED",
        user.getId());
  }

  @Transactional
  public void update(UUID grantId, UUID roleId, boolean active) {
    access.require(Permission.USER_MANAGE);
    var grant = manageableGrant(grantId, false);
    var allowedRoles = assignableRoles();
    if (allowedRoles.stream().noneMatch(role -> role.getId().equals(roleId))
        || allowedRoles.stream().noneMatch(role -> role.getId().equals(grant.getRoleId()))) {
      throw new AccessDeniedException("Perfil não autorizado.");
    }
    grant.update(roleId, active);
    audit.record(
        tenant.id(),
        access.activeStore().getId(),
        access.principal().userId(),
        active ? "USER_ACCESS_UPDATED" : "USER_ACCESS_BLOCKED",
        grantId);
  }

  @Transactional
  public void recover(UUID grantId) {
    var grant = manageableGrant(grantId, true);
    var membership =
        memberships.findByTenantIdAndId(tenant.id(), grant.getMembershipId()).orElseThrow();
    var user = users.findById(membership.getUserId()).orElseThrow();
    recovery.request(user.getEmail(), RecoveryScope.from(tenant.current()));
    audit.record(
        tenant.id(),
        access.activeStore().getId(),
        access.principal().userId(),
        "ADMIN_RECOVERY_REQUESTED",
        user.getId());
  }

  private StoreRoleGrant manageableGrant(UUID id, boolean allowSelf) {
    access.require(Permission.USER_MANAGE);
    var grant =
        grants
            .findByTenantIdAndStoreIdAndId(tenant.id(), access.activeStore().getId(), id)
            .orElseThrow(() -> new AccessDeniedException("Acesso não autorizado."));
    var membership =
        memberships.findByTenantIdAndId(tenant.id(), grant.getMembershipId()).orElseThrow();
    if ((!allowSelf && membership.getUserId().equals(access.principal().userId()))
        || assignableRoles().stream().noneMatch(role -> role.getId().equals(grant.getRoleId()))) {
      throw new AccessDeniedException(
          "Peça a outro administrador autorizado para alterar este acesso.");
    }
    return grant;
  }
}
