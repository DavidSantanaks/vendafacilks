package br.com.vendafacil.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {
  Optional<Role> findByTenantIdAndId(UUID tenantId, UUID id);

  List<Role> findAllByTenantIdOrderByNameAsc(UUID tenantId);
}
