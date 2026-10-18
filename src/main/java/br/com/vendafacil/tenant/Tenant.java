package br.com.vendafacil.tenant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class Tenant {
  @Id private UUID id;
  private String slug;
  private String name;
  private String status;
  @Version private long version;

  protected Tenant() {}

  public Tenant(String slug, String name) {
    this.id = UUID.randomUUID();
    this.slug = slug;
    this.name = name;
    this.status = "ACTIVE";
  }

  public UUID getId() {
    return id;
  }

  public String getSlug() {
    return slug;
  }

  public String getName() {
    return name;
  }

  public String getStatus() {
    return status;
  }

  public boolean isActive() {
    return "ACTIVE".equals(status);
  }

  public void setActive(boolean active) {
    status = active ? "ACTIVE" : "SUSPENDED";
  }

  public void rename(String name) {
    this.name = name;
  }
}
