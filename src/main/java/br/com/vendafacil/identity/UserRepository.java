package br.com.vendafacil.identity;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {
  Optional<UserAccount> findByEmail(String email);

  boolean existsByEmail(String email);

  long countByPlatformAdminTrue();

  long countByPlatformAdminTrueAndActiveTrue();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserAccount u where u.id = :id")
  Optional<UserAccount> findLockedById(UUID id);

  @Query(
      "select u from UserAccount u where lower(u.name) like :search or lower(u.email) like :search order by u.name")
  Page<UserAccount> search(String search, Pageable pageable);

  @Query(
      "select u from UserAccount u where (lower(u.name) like :search or lower(u.email) like :search) and exists (select m.id from Membership m where m.userId = u.id and m.tenantId = :tenantId) order by u.name")
  Page<UserAccount> searchByTenant(String search, UUID tenantId, Pageable pageable);
}
