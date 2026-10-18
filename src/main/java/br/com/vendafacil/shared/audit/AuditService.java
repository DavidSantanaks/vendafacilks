package br.com.vendafacil.shared.audit;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
  private final AuditRepository repository;

  public AuditService(AuditRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public void record(UUID tenantId, UUID storeId, UUID actorId, String action, UUID targetId) {
    var reference = MDC.get("correlationId");
    repository.save(
        new AuditLog(
            tenantId,
            storeId,
            actorId,
            action,
            targetId,
            reference == null ? UUID.randomUUID().toString() : reference));
  }
}
