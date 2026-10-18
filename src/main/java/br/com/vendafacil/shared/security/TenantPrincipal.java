package br.com.vendafacil.shared.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record TenantPrincipal(
    UUID userId, UUID tenantId, String name, boolean platformAdmin, long credentialVersion)
    implements Serializable {
  public Collection<? extends GrantedAuthority> authorities() {
    return List.of(
        new SimpleGrantedAuthority(platformAdmin ? "ROLE_PLATFORM_ADMIN" : "ROLE_MEMBER"));
  }
}
