package br.com.vendafacil.tenant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
  Optional<Tenant> findBySlug(String slug);

  boolean existsBySlug(String slug);

  List<Tenant> findAllByOrderByNameAsc();
}
