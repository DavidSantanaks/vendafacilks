package br.com.vendafacil.commerce;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import br.com.vendafacil.shared.security.LoginThrottle;
import br.com.vendafacil.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ApprovalService {
  public record Credentials(String email, String password, String reason) {
    @Override
    public String toString() {
      return "Credentials[redacted]";
    }
  }

  private record Candidate(UUID id, String hash, BigDecimal limit) {}

  private final JdbcTemplate jdbc;
  private final AccessService access;
  private final TenantContext tenant;
  private final PasswordEncoder encoder;
  private final LoginThrottle throttle;
  private final HttpServletRequest request;
  private final String dummyHash;

  public ApprovalService(
      JdbcTemplate jdbc,
      AccessService access,
      TenantContext tenant,
      PasswordEncoder encoder,
      LoginThrottle throttle,
      HttpServletRequest request) {
    this.jdbc = jdbc;
    this.access = access;
    this.tenant = tenant;
    this.encoder = encoder;
    this.throttle = throttle;
    this.request = request;
    dummyHash = encoder.encode(UUID.randomUUID().toString());
  }

  public UUID require(Permission permission, Credentials credentials, BigDecimal minimumLimit) {
    var key = "commerce-approval:" + tenant.id() + ":" + request.getRemoteAddr();
    try {
      throttle.check(key);
    } catch (AuthenticationException exception) {
      throw new IllegalArgumentException("Aguarde alguns minutos antes de tentar outra aprovação.");
    }
    var c = credentials == null ? new Credentials("", "", "") : credentials;
    var candidates =
        jdbc.query(
            """
        SELECT u.id,u.password_hash,max(r.discount_limit) AS limit_value FROM users u
        JOIN memberships m ON m.user_id=u.id AND m.tenant_id=? AND m.active
        JOIN membership_store_roles g ON g.tenant_id=m.tenant_id AND g.membership_id=m.id AND g.store_id=? AND g.active
        JOIN roles r ON r.tenant_id=g.tenant_id AND r.id=g.role_id
        JOIN role_permissions rp ON rp.role_id=r.id AND rp.permission_code=?
        WHERE u.email=? AND u.active GROUP BY u.id,u.password_hash
        """,
            (rs, n) ->
                new Candidate(rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3)),
            tenant.id(),
            access.activeStore().getId(),
            permission.name(),
            c.email() == null ? "" : c.email().trim().toLowerCase(Locale.ROOT));
    var candidate = candidates.stream().findFirst().orElse(null);
    var password = c.password() == null ? "" : c.password();
    boolean matches =
        encoder.matches(
            password.length() <= 64 ? password : "",
            candidate == null ? dummyHash : candidate.hash());
    if (candidate == null
        || !matches
        || candidate.id().equals(access.principal().userId())
        || (minimumLimit != null && candidate.limit().compareTo(minimumLimit) < 0)) {
      throttle.failure(key);
      throw new IllegalArgumentException(
          "Aprovação necessária: informe outra pessoa autorizada nesta filial, com limite suficiente.");
    }
    reason(c.reason());
    return candidate.id();
  }

  public static String reason(String value) {
    var text = value == null ? "" : value.trim();
    if (text.length() < 5 || text.length() > 240 || text.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Informe um motivo com 5 a 240 caracteres.");
    return text;
  }
}
