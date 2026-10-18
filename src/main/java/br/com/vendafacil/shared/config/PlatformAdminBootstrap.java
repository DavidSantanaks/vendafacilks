package br.com.vendafacil.shared.config;

import br.com.vendafacil.identity.UserAccount;
import br.com.vendafacil.identity.UserRepository;
import br.com.vendafacil.shared.audit.AuditService;
import br.com.vendafacil.shared.security.PasswordPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.bootstrap-admin.enabled", havingValue = "true")
public class PlatformAdminBootstrap implements ApplicationRunner {
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final AuditService audit;
  private final String email;
  private final String password;

  public PlatformAdminBootstrap(
      UserRepository users,
      PasswordEncoder encoder,
      AuditService audit,
      @Value("${BOOTSTRAP_ADMIN_EMAIL}") String email,
      @Value("${BOOTSTRAP_ADMIN_PASSWORD}") String password) {
    this.users = users;
    this.encoder = encoder;
    this.audit = audit;
    this.email = email;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    PasswordPolicy.validate(password);
    if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length() > 254) {
      throw new IllegalArgumentException("BOOTSTRAP_ADMIN_EMAIL inválido.");
    }
    if (users.countByPlatformAdminTrue() > 0) {
      return;
    }
    if (users.existsByEmail(email.toLowerCase(java.util.Locale.ROOT))) {
      throw new IllegalArgumentException("Use um novo e-mail para a administração da plataforma.");
    }
    var user =
        users.saveAndFlush(
            new UserAccount(email, "Administração da plataforma", encoder.encode(password), true));
    audit.record(null, null, user.getId(), "PLATFORM_ADMIN_BOOTSTRAPPED", user.getId());
  }
}
