package br.com.vendafacil.shared.security;

import br.com.vendafacil.shared.config.AppProperties;
import br.com.vendafacil.tenant.HostResolver;
import br.com.vendafacil.tenant.TenantContext;
import br.com.vendafacil.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class TenantHostFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(TenantHostFilter.class);
  private final HostResolver resolver;
  private final TenantRepository tenants;
  private final AppProperties properties;

  public TenantHostFilter(
      HostResolver resolver, TenantRepository tenants, AppProperties properties) {
    this.resolver = resolver;
    this.tenants = tenants;
    this.properties = properties;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var reference = UUID.randomUUID().toString();
    var start = System.nanoTime();
    MDC.put("correlationId", reference);
    request.setAttribute("correlationId", reference);
    response.setHeader("X-Correlation-ID", reference);
    try {
      // Health é uma sonda sem dados internos; as demais rotas exigem host oficial.
      if ("/actuator/health".equals(request.getRequestURI())) {
        chain.doFilter(request, response);
        return;
      }
      if (properties.requireHttps() && !request.isSecure()) {
        reject(response, 400, "Conexão segura obrigatória.", reference);
        return;
      }
      var slug = resolver.resolve(request.getHeader("Host"));
      if (slug.isEmpty()) {
        reject(
            response, 400, "Endereço não autorizado. Use o subdomínio da sua empresa.", reference);
        return;
      }
      TenantContext.Resolved context;
      if ("admin".equals(slug.get())) {
        context = new TenantContext.Resolved(null, "admin", "Administração da plataforma", true);
      } else {
        var tenant = tenants.findBySlug(slug.get()).filter(t -> t.isActive());
        if (tenant.isEmpty()) {
          reject(
              response,
              404,
              "Empresa indisponível. Confira o endereço ou fale com o responsável.",
              reference);
          return;
        }
        context =
            new TenantContext.Resolved(
                tenant.get().getId(), tenant.get().getSlug(), tenant.get().getName(), false);
        MDC.put("tenantId", context.id().toString());
      }
      request.setAttribute(TenantContext.ATTRIBUTE, context);
      chain.doFilter(request, response);
    } finally {
      LOG.info(
          "request method={} status={} durationMs={}",
          request.getMethod(),
          response.getStatus(),
          (System.nanoTime() - start) / 1_000_000);
      MDC.remove("correlationId");
      MDC.remove("tenantId");
      MDC.remove("storeId");
    }
  }

  private void reject(HttpServletResponse response, int status, String message, String reference)
      throws IOException {
    response.setStatus(status);
    response.setContentType("text/html;charset=UTF-8");
    response
        .getWriter()
        .write(
            "<!doctype html><html lang=\"pt-BR\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Acesso indisponível | vendaFacil</title><body><main><h1>Acesso indisponível</h1><p>"
                + message
                + "</p><p>Referência: "
                + reference
                + "</p></main></body></html>");
  }
}
