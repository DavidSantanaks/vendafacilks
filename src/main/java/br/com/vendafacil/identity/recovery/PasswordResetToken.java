package br.com.vendafacil.identity.recovery;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {
  @Id private UUID id;
  private UUID userId;
  private UUID tenantId;
  private boolean platform;
  private String tokenHash;
  private long credentialVersion;
  private Instant expiresAt;
  private Instant usedAt;

  protected PasswordResetToken() {}

  public PasswordResetToken(
      UUID userId, RecoveryScope scope, String hash, long credentialVersion, Instant expiresAt) {
    id = UUID.randomUUID();
    this.userId = userId;
    tenantId = scope.tenantId();
    platform = scope.platform();
    tokenHash = hash;
    this.credentialVersion = credentialVersion;
    this.expiresAt = expiresAt;
  }

  public UUID getUserId() {
    return userId;
  }

  public boolean valid(RecoveryScope scope, long version) {
    return usedAt == null
        && expiresAt.isAfter(Instant.now())
        && credentialVersion == version
        && platform == scope.platform()
        && Objects.equals(tenantId, scope.tenantId());
  }
}
