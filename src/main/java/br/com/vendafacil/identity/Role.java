package br.com.vendafacil.identity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class Role {
  @Id private UUID id;
  private UUID tenantId;
  private String name;
  private BigDecimal discountLimit;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
  @Column(name = "permission_code")
  @Enumerated(EnumType.STRING)
  private Set<Permission> permissions = new HashSet<>();

  protected Role() {}

  public Role(UUID tenantId, String name, String discountLimit, Set<Permission> permissions) {
    id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.name = name;
    this.discountLimit = new BigDecimal(discountLimit);
    this.permissions = new HashSet<>(permissions);
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public BigDecimal getDiscountLimit() {
    return discountLimit;
  }

  public Set<Permission> getPermissions() {
    return Set.copyOf(permissions);
  }
}
