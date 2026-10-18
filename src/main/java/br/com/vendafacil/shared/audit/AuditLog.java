package br.com.vendafacil.shared.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
  @Id private UUID id;
  private UUID tenantId;
  private UUID storeId;
  private UUID actorId;
  private String action;
  private UUID targetId;
  private String correlationId;
  private Instant createdAt;

  protected AuditLog() {}

  public AuditLog(
      UUID tenantId,
      UUID storeId,
      UUID actorId,
      String action,
      UUID targetId,
      String correlationId) {
    id = UUID.randomUUID();
    this.tenantId = tenantId;
    this.storeId = storeId;
    this.actorId = actorId;
    this.action = action;
    this.targetId = targetId;
    this.correlationId = correlationId;
    createdAt = Instant.now();
  }

  public String getAction() {
    return action;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
