package br.com.vendafacil.identity.recovery;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PasswordResetRepository extends JpaRepository<PasswordResetToken, UUID> {
  Optional<PasswordResetToken> findByTokenHash(String hash);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update PasswordResetToken t set t.usedAt = :now where t.userId = :userId and t.usedAt is null")
  void invalidateAll(UUID userId, Instant now);
}
