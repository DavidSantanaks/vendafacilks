package br.com.vendafacil.shared.security;

import br.com.vendafacil.identity.MembershipRepository;
import br.com.vendafacil.identity.UserRepository;
import br.com.vendafacil.shared.audit.AuditService;
import br.com.vendafacil.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class TenantAuthenticationProvider implements AuthenticationProvider {
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final TenantContext tenant;
  private final PasswordEncoder encoder;
  private final LoginThrottle throttle;
  private final HttpServletRequest request;
  private final AuditService audit;
  private final String dummyHash;

  public TenantAuthenticationProvider(
      UserRepository users,
      MembershipRepository memberships,
      TenantContext tenant,
      PasswordEncoder encoder,
      LoginThrottle throttle,
      HttpServletRequest request,
      AuditService audit) {
    this.users = users;
    this.memberships = memberships;
    this.tenant = tenant;
    this.encoder = encoder;
    this.throttle = throttle;
    this.request = request;
    this.audit = audit;
    dummyHash = encoder.encode("unavailable-account-timing-placeholder");
  }

  @Override
  public Authentication authenticate(Authentication authentication) {
    var context = tenant.current();
    var key = context.slug() + ":" + request.getRemoteAddr();
    throttle.check(key);
    var email = authentication.getName().trim().toLowerCase(Locale.ROOT);
    var user = users.findByEmail(email).orElse(null);
    var suppliedPassword = String.valueOf(authentication.getCredentials());
    if (suppliedPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
      throttle.failure(key);
      throw new BadCredentialsException("E-mail, senha ou acesso à empresa inválido.");
    }
    var validPassword =
        encoder.matches(suppliedPassword, user == null ? dummyHash : user.getPasswordHash());
    var validAccess =
        user != null
            && user.isActive()
            && (context.platform()
                ? user.isPlatformAdmin()
                : memberships
                    .findByTenantIdAndUserIdAndActiveTrue(context.id(), user.getId())
                    .isPresent());
    if (!validPassword || !validAccess) {
      throttle.failure(key);
      throw new BadCredentialsException("E-mail, senha ou acesso à empresa inválido.");
    }
    var principal =
        new TenantPrincipal(
            user.getId(),
            context.id(),
            user.getName(),
            context.platform(),
            user.getCredentialVersion());
    audit.record(context.id(), null, user.getId(), "LOGIN_SUCCEEDED", user.getId());
    return UsernamePasswordAuthenticationToken.authenticated(
        principal, null, principal.authorities());
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
