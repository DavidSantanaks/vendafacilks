package br.com.vendafacil.identity.recovery;

import br.com.vendafacil.identity.MembershipRepository;
import br.com.vendafacil.identity.UserAccount;
import br.com.vendafacil.identity.UserRepository;
import br.com.vendafacil.shared.audit.AuditService;
import br.com.vendafacil.shared.config.PortalUrls;
import br.com.vendafacil.shared.security.PasswordPolicy;
import br.com.vendafacil.tenant.TenantRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordRecoveryService {
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final TenantRepository tenants;
  private final PasswordResetRepository tokens;
  private final PasswordEncoder encoder;
  private final PortalUrls urls;
  private final AuditService audit;
  private final ApplicationEventPublisher events;
  private final SecureRandom random = new SecureRandom();

  public PasswordRecoveryService(
      UserRepository users,
      MembershipRepository memberships,
      TenantRepository tenants,
      PasswordResetRepository tokens,
      PasswordEncoder encoder,
      PortalUrls urls,
      AuditService audit,
      ApplicationEventPublisher events) {
    this.users = users;
    this.memberships = memberships;
    this.tenants = tenants;
    this.tokens = tokens;
    this.encoder = encoder;
    this.urls = urls;
    this.audit = audit;
    this.events = events;
  }

  @Transactional
  public void request(String email, RecoveryScope scope) {
    var candidate = users.findByEmail(email.trim().toLowerCase(Locale.ROOT));
    if (candidate.isEmpty()) {
      return;
    }
    var user = users.findLockedById(candidate.get().getId()).orElseThrow();
    if (!eligible(user, scope)) {
      return;
    }
    var bytes = new byte[32];
    random.nextBytes(bytes);
    var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    tokens.invalidateAll(user.getId(), Instant.now());
    tokens.save(
        new PasswordResetToken(
            user.getId(),
            scope,
            hash(token),
            user.getCredentialVersion(),
            Instant.now().plus(Duration.ofMinutes(30))));
    audit.record(scope.tenantId(), null, user.getId(), "PASSWORD_RECOVERY_REQUESTED", user.getId());
    events.publishEvent(
        new RecoveryMail(
            user.getEmail(),
            scope.name(),
            urls.origin(scope.slug()) + "/redefinir-senha?token=" + token,
            false));
  }

  @Transactional(readOnly = true)
  public boolean valid(String token, RecoveryScope scope) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
      return false;
    }
    return tokens
        .findByTokenHash(hash(token))
        .filter(
            reset ->
                users
                    .findById(reset.getUserId())
                    .filter(
                        user ->
                            eligible(user, scope)
                                && reset.valid(scope, user.getCredentialVersion()))
                    .isPresent())
        .isPresent();
  }

  @Transactional
  public void reset(String token, String password, String confirmation, RecoveryScope scope) {
    PasswordPolicy.validate(password);
    if (!password.equals(confirmation)) {
      throw new IllegalArgumentException("As senhas precisam ser iguais.");
    }
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
      throw invalid();
    }
    var reset = tokens.findByTokenHash(hash(token)).orElseThrow(this::invalid);
    var user = users.findLockedById(reset.getUserId()).orElseThrow(this::invalid);
    // O lock na identidade serializa emissão e uso; o UPDATE confirma uso único no banco.
    if (!eligible(user, scope) || !reset.valid(scope, user.getCredentialVersion())) {
      throw invalid();
    }
    user.changePassword(encoder.encode(password));
    tokens.invalidateAll(user.getId(), Instant.now());
    audit.record(scope.tenantId(), null, user.getId(), "PASSWORD_RESET_COMPLETED", user.getId());
    events.publishEvent(
        new RecoveryMail(user.getEmail(), scope.name(), urls.login(scope.slug()), true));
  }

  private boolean eligible(UserAccount user, RecoveryScope scope) {
    if (!user.isActive()) {
      return false;
    }
    if (scope.platform()) {
      return scope.tenantId() == null && "admin".equals(scope.slug()) && user.isPlatformAdmin();
    }
    return scope.tenantId() != null
        && tenants
            .findById(scope.tenantId())
            .filter(t -> t.isActive() && t.getSlug().equals(scope.slug()))
            .isPresent()
        && memberships
            .findByTenantIdAndUserIdAndActiveTrue(scope.tenantId(), user.getId())
            .isPresent();
  }

  private IllegalArgumentException invalid() {
    return new IllegalArgumentException(
        "Este link é inválido, expirou ou já foi utilizado. Solicite uma nova recuperação.");
  }

  public static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponível.", exception);
    }
  }
}
