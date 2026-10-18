package br.com.vendafacil.identity.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class RecoveryDispatcher {
  private static final Logger LOG = LoggerFactory.getLogger(RecoveryDispatcher.class);
  private final PasswordRecoveryService service;

  public RecoveryDispatcher(PasswordRecoveryService service) {
    this.service = service;
  }

  @Async("recoveryExecutor")
  public void request(String email, RecoveryScope scope) {
    try {
      service.request(email, scope);
    } catch (RuntimeException exception) {
      LOG.error(
          "Falha ao preparar recuperação de senha: category={}",
          exception.getClass().getSimpleName());
    }
  }
}
