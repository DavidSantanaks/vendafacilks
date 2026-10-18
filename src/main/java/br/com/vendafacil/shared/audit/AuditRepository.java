package br.com.vendafacil.shared.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditLog, UUID> {
  List<AuditLog> findTop8ByTenantIdAndStoreIdOrderByCreatedAtDesc(UUID tenantId, UUID storeId);
}
