package br.com.vendafacil.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "stores")
public class Store {
  @Id private UUID id;
  private UUID tenantId;
  private String name;
  private String city;

  @Column(length = 2)
  @JdbcTypeCode(SqlTypes.CHAR)
  private String state;

  private boolean active;
  @Version private long version;

  protected Store() {}

  public Store(UUID tenantId, String name, String city, String state) {
    id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.name = name;
    this.city = city;
    this.state = state;
    active = true;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getName() {
    return name;
  }

  public String getCity() {
    return city;
  }

  public String getState() {
    return state;
  }

  public boolean isActive() {
    return active;
  }

  public void update(String name, String city, String state, boolean active) {
    this.name = name;
    this.city = city;
    this.state = state;
    this.active = active;
  }
}
