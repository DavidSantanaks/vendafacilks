package br.com.vendafacil.identity;

import br.com.vendafacil.shared.security.TenantPrincipal;
import br.com.vendafacil.store.Store;
import br.com.vendafacil.store.StoreRepository;
import br.com.vendafacil.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AccessService {
  public static final String ACTIVE_STORE = "activeStoreId";
  private final TenantContext tenant;
  private final MembershipRepository memberships;
  private final StoreRoleRepository grants;
  private final RoleRepository roles;
  private final StoreRepository stores;
  private final HttpServletRequest request;

  public AccessService(
      TenantContext tenant,
      MembershipRepository memberships,
      StoreRoleRepository grants,
      RoleRepository roles,
      StoreRepository stores,
      HttpServletRequest request) {
    this.tenant = tenant;
    this.memberships = memberships;
    this.grants = grants;
    this.roles = roles;
    this.stores = stores;
    this.request = request;
  }

  public TenantPrincipal principal() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
      throw new AccessDeniedException("Autenticação necessária.");
    }
    var context = tenant.current();
    if (!java.util.Objects.equals(principal.tenantId(), context.id())
        || principal.platformAdmin() != context.platform()) {
      throw new AccessDeniedException("Acesso não autorizado.");
    }
    return principal;
  }

  public Membership membership() {
    return memberships
        .findByTenantIdAndUserIdAndActiveTrue(tenant.id(), principal().userId())
        .orElseThrow(() -> new AccessDeniedException("Acesso não autorizado."));
  }

  public List<Store> authorizedStores() {
    return assignedStores().stream().filter(Store::isActive).toList();
  }

  public List<Store> assignedStores() {
    var storeIds =
        grants.findAllByTenantIdAndMembershipId(tenant.id(), membership().getId()).stream()
            .filter(StoreRoleGrant::isActive)
            .map(StoreRoleGrant::getStoreId)
            .collect(Collectors.toSet());
    return stores.findAllByTenantIdOrderByNameAsc(tenant.id()).stream()
        .filter(store -> storeIds.contains(store.getId()))
        .toList();
  }

  public Store requireStore(UUID storeId) {
    return authorizedStores().stream()
        .filter(store -> store.getId().equals(storeId))
        .findFirst()
        .orElseThrow(() -> new AccessDeniedException("Filial não autorizada ou indisponível."));
  }

  public Store activeStore() {
    var session = request.getSession(false);
    if (session == null || !(session.getAttribute(ACTIVE_STORE) instanceof UUID storeId)) {
      throw new StoreSelectionRequired();
    }
    try {
      var store = requireStore(storeId);
      org.slf4j.MDC.put("storeId", store.getId().toString());
      return store;
    } catch (AccessDeniedException exception) {
      session.removeAttribute(ACTIVE_STORE);
      throw new StoreSelectionRequired();
    }
  }

  public List<Role> activeRoles() {
    return rolesAt(activeStore().getId());
  }

  public List<Role> rolesAt(UUID storeId) {
    if (assignedStores().stream().noneMatch(store -> store.getId().equals(storeId))) {
      throw new AccessDeniedException("Filial não autorizada.");
    }
    return grants
        .findAllByTenantIdAndMembershipIdAndStoreId(tenant.id(), membership().getId(), storeId)
        .stream()
        .filter(StoreRoleGrant::isActive)
        .map(grant -> roles.findByTenantIdAndId(tenant.id(), grant.getRoleId()).orElseThrow())
        .toList();
  }

  public Set<Permission> permissions() {
    return activeRoles().stream()
        .flatMap(role -> role.getPermissions().stream())
        .collect(Collectors.toUnmodifiableSet());
  }

  public void require(Permission permission) {
    if (!permissions().contains(permission)) {
      throw new AccessDeniedException("Você não tem permissão para esta operação.");
    }
  }

  public void requireAt(UUID storeId, Permission permission) {
    if (rolesAt(storeId).stream().noneMatch(role -> role.getPermissions().contains(permission))) {
      throw new AccessDeniedException("Você não tem permissão para esta operação.");
    }
  }

  public void requirePlatformAdmin() {
    if (!tenant.current().platform() || !principal().platformAdmin()) {
      throw new AccessDeniedException("Acesso restrito à administração da plataforma.");
    }
  }
}
