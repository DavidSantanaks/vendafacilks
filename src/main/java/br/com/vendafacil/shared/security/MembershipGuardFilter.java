package br.com.vendafacil.shared.security;

import br.com.vendafacil.identity.MembershipRepository;
import br.com.vendafacil.identity.UserRepository;
import br.com.vendafacil.tenant.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class MembershipGuardFilter extends OncePerRequestFilter {
  private final MembershipRepository memberships;
  private final UserRepository users;

  public MembershipGuardFilter(MembershipRepository memberships, UserRepository users) {
    this.memberships = memberships;
    this.users = users;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    var context = (TenantContext.Resolved) request.getAttribute(TenantContext.ATTRIBUTE);
    if (authentication != null
        && authentication.getPrincipal() instanceof TenantPrincipal principal) {
      var user = users.findById(principal.userId()).filter(u -> u.isActive());
      var valid =
          context != null
              && user.isPresent()
              && user.get().getCredentialVersion() == principal.credentialVersion()
              && Objects.equals(principal.tenantId(), context.id())
              && principal.platformAdmin() == context.platform();
      if (valid) {
        valid =
            context.platform()
                ? user.get().isPlatformAdmin()
                : memberships
                    .findByTenantIdAndUserIdAndActiveTrue(context.id(), principal.userId())
                    .isPresent();
      }
      if (!valid) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
          request.getSession(false).invalidate();
        }
        SecurityResponses.error(request, response, 403, "Acesso não autorizado neste endereço.");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
