package br.com.vendafacil.tenant;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class TenantContext {
  public static final String ATTRIBUTE = TenantContext.class.getName();

  public record Resolved(UUID id, String slug, String name, boolean platform) {}

  private final HttpServletRequest request;

  public TenantContext(HttpServletRequest request) {
    this.request = request;
  }

  public Resolved current() {
    var context = (Resolved) request.getAttribute(ATTRIBUTE);
    if (context == null) {
      throw new AccessDeniedException("Contexto de empresa indisponível.");
    }
    return context;
  }

  public UUID id() {
    var context = current();
    if (context.platform()) {
      throw new AccessDeniedException("Acesso empresarial indisponível nesta área.");
    }
    return context.id();
  }
}
