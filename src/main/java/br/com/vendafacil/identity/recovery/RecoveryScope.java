package br.com.vendafacil.identity.recovery;

import br.com.vendafacil.tenant.TenantContext;
import java.util.UUID;

public record RecoveryScope(UUID tenantId, boolean platform, String slug, String name) {
  public static RecoveryScope from(TenantContext.Resolved tenant) {
    return new RecoveryScope(tenant.id(), tenant.platform(), tenant.slug(), tenant.name());
  }
}
