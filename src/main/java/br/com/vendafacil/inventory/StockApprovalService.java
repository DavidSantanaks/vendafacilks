package br.com.vendafacil.inventory;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.shared.security.LoginThrottle;
import br.com.vendafacil.shared.validation.FieldValidationException;
import br.com.vendafacil.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class StockApprovalService {
  private record Approver(UUID id, String hash) {}

  private final JdbcTemplate jdbc;
  private final PasswordEncoder encoder;
  private final AccessService access;
  private final TenantContext tenant;
  private final LoginThrottle throttle;
  private final HttpServletRequest request;
  private final String dummyHash;

  public StockApprovalService(
      JdbcTemplate jdbc,
      PasswordEncoder encoder,
      AccessService access,
      TenantContext tenant,
      LoginThrottle throttle,
      HttpServletRequest request) {
    this.jdbc = jdbc;
    this.encoder = encoder;
    this.access = access;
    this.tenant = tenant;
    this.throttle = throttle;
    this.request = request;
    this.dummyHash = encoder.encode(UUID.randomUUID().toString());
  }

  public UUID approve(StockForm form) {
    var key = "stock-approval:" + tenant.id() + ":" + request.getRemoteAddr();
    try {
      throttle.check(key);
    } catch (AuthenticationException exception) {
      throw invalid("Aguarde alguns minutos antes de tentar outra aprovação.");
    }
    var email =
        form.approverEmail() == null ? "" : form.approverEmail().trim().toLowerCase(Locale.ROOT);
    var candidates =
        jdbc.query(
            """
        SELECT u.id,u.password_hash FROM users u
        JOIN memberships m ON m.user_id=u.id AND m.tenant_id=? AND m.active
        WHERE u.email=? AND u.active AND EXISTS (
          SELECT 1 FROM membership_store_roles g
          JOIN role_permissions rp ON rp.role_id=g.role_id AND rp.permission_code='NEGATIVE_STOCK_APPROVE'
          WHERE g.tenant_id=m.tenant_id AND g.membership_id=m.id AND g.store_id=? AND g.active)
        """,
            (rs, n) -> new Approver(rs.getObject("id", UUID.class), rs.getString("password_hash")),
            tenant.id(),
            email,
            access.activeStore().getId());
    var candidate = candidates.stream().findFirst().orElse(null);
    var password = form.approverPassword() == null ? "" : form.approverPassword();
    boolean matches =
        encoder.matches(
            password.length() <= 64 ? password : "",
            candidate == null ? dummyHash : candidate.hash());
    if (candidate == null || !matches || candidate.id().equals(access.principal().userId())) {
      throttle.failure(key);
      throw invalid(
          "Informe credenciais de outra pessoa com autorização de estoque negativo nesta filial.");
    }
    if (form.approvalReason() == null
        || form.approvalReason().trim().length() < 5
        || form.approvalReason().length() > 240) {
      throw new FieldValidationException(
          "approvalReason", "Justifique a exceção com 5 a 240 caracteres.");
    }
    return candidate.id();
  }

  private FieldValidationException invalid(String message) {
    return new FieldValidationException("approverEmail", message);
  }
}
