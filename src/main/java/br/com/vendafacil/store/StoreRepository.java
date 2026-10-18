package br.com.vendafacil.store;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, UUID> {
  Optional<Store> findByTenantIdAndId(UUID tenantId, UUID id);

  List<Store> findAllByTenantIdOrderByNameAsc(UUID tenantId);

  long countByTenantIdAndActiveTrue(UUID tenantId);
}
