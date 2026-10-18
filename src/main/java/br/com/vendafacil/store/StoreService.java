package br.com.vendafacil.store;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import br.com.vendafacil.identity.StoreRoleGrant;
import br.com.vendafacil.identity.StoreRoleRepository;
import br.com.vendafacil.shared.audit.AuditService;
import br.com.vendafacil.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreService {
  private final AccessService access;
  private final TenantContext tenant;
  private final StoreRepository stores;
  private final StoreRoleRepository grants;
  private final AuditService audit;

  public StoreService(
      AccessService access,
      TenantContext tenant,
      StoreRepository stores,
      StoreRoleRepository grants,
      AuditService audit) {
    this.access = access;
    this.tenant = tenant;
    this.stores = stores;
    this.grants = grants;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<Store> available() {
    return access.authorizedStores();
  }

  @Transactional(readOnly = true)
  public List<Store> manageable() {
    access.require(Permission.USER_MANAGE);
    return access.assignedStores().stream()
        .filter(
            store ->
                access.rolesAt(store.getId()).stream()
                    .anyMatch(role -> role.getPermissions().contains(Permission.USER_MANAGE)))
        .toList();
  }

  @Transactional
  public void select(UUID id) {
    var store = access.requireStore(id);
    audit.record(
        tenant.id(), store.getId(), access.principal().userId(), "STORE_SELECTED", store.getId());
  }

  @Transactional
  public void create(StoreForm form) {
    access.require(Permission.USER_MANAGE);
    var currentRoles = access.activeRoles();
    var membership = access.membership();
    var store =
        stores.saveAndFlush(
            new Store(tenant.id(), form.name().trim(), form.city().trim(), form.state()));
    for (var role : currentRoles) {
      grants.save(new StoreRoleGrant(tenant.id(), membership.getId(), store.getId(), role.getId()));
    }
    audit.record(
        tenant.id(), store.getId(), access.principal().userId(), "STORE_CREATED", store.getId());
  }

  @Transactional
  public void update(UUID id, StoreForm form) {
    access.requireAt(id, Permission.USER_MANAGE);
    var store = stores.findByTenantIdAndId(tenant.id(), id).orElseThrow();
    if (!Boolean.TRUE.equals(form.active())
        && stores.countByTenantIdAndActiveTrue(tenant.id()) <= 1) {
      throw new IllegalArgumentException("Mantenha ao menos uma filial ativa.");
    }
    store.update(
        form.name().trim(), form.city().trim(), form.state(), Boolean.TRUE.equals(form.active()));
    audit.record(
        tenant.id(), store.getId(), access.principal().userId(), "STORE_UPDATED", store.getId());
  }
}
