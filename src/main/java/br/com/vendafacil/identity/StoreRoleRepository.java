package br.com.vendafacil.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRoleRepository extends JpaRepository<StoreRoleGrant, UUID> {
  List<StoreRoleGrant> findAllByTenantIdAndMembershipId(UUID tenantId, UUID membershipId);

  List<StoreRoleGrant> findAllByTenantIdAndMembershipIdAndStoreId(
      UUID tenantId, UUID membershipId, UUID storeId);

  Optional<StoreRoleGrant> findByTenantIdAndStoreIdAndId(UUID tenantId, UUID storeId, UUID id);
}
