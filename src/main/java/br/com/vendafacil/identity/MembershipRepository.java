package br.com.vendafacil.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {
  Optional<Membership> findByTenantIdAndUserIdAndActiveTrue(UUID tenantId, UUID userId);

  List<Membership> findAllByTenantIdAndActiveTrue(UUID tenantId);

  long countByTenantIdAndActiveTrue(UUID tenantId);

  Optional<Membership> findByTenantIdAndId(UUID tenantId, UUID id);

  List<Membership> findAllByTenantId(UUID tenantId);

  List<Membership> findAllByUserId(UUID userId);
}
