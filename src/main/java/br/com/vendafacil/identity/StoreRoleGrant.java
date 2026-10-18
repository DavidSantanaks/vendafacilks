package br.com.vendafacil.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "membership_store_roles")
public class StoreRoleGrant {
  @Id private UUID id;
  private UUID tenantId;
  private UUID membershipId;
  private UUID storeId;
  private UUID roleId;
  private boolean active = true;

  protected StoreRoleGrant() {}

  public StoreRoleGrant(UUID tenantId, UUID membershipId, UUID storeId, UUID roleId) {
    id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.membershipId = membershipId;
    this.storeId = storeId;
    this.roleId = roleId;
  }

  public UUID getStoreId() {
    return storeId;
  }

  public UUID getRoleId() {
    return roleId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getMembershipId() {
    return membershipId;
  }

  public boolean isActive() {
    return active;
  }

  public void update(UUID roleId, boolean active) {
    this.roleId = roleId;
    this.active = active;
  }
}
