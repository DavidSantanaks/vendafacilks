package br.com.vendafacil.identity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserAccount {
  @Id private UUID id;
  private String email;
  private String name;
  private String passwordHash;
  private boolean active;
  private boolean platformAdmin;
  private long credentialVersion;

  protected UserAccount() {}

  public UserAccount(String email, String name, String passwordHash, boolean platformAdmin) {
    id = UUID.randomUUID();
    this.email = email.trim().toLowerCase(Locale.ROOT);
    this.name = name;
    this.passwordHash = passwordHash;
    this.platformAdmin = platformAdmin;
    active = true;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isPlatformAdmin() {
    return platformAdmin;
  }

  public long getCredentialVersion() {
    return credentialVersion;
  }

  public void changePassword(String hash) {
    passwordHash = hash;
    credentialVersion++;
  }

  public void update(String name, boolean active) {
    this.name = name;
    if (this.active != active) {
      credentialVersion++;
    }
    this.active = active;
  }
}
