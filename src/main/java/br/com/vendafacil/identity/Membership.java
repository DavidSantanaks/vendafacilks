package br.com.vendafacil.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "memberships")
public class Membership {
  @Id private UUID id;
  private UUID tenantId;
  private UUID userId;
  private boolean active;

  protected Membership() {}

  public Membership(UUID tenantId, UUID userId) {
    id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.userId = userId;
    active = true;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
