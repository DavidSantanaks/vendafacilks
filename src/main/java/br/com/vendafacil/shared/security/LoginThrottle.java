package br.com.vendafacil.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Component;

@Component
public class LoginThrottle {
  private record Window(long start, int attempts) {}

  private final Map<String, Window> windows = new LinkedHashMap<>();
  private final Clock clock;
  private static final long WINDOW_MS = Duration.ofMinutes(15).toMillis();

  public LoginThrottle() {
    this(Clock.systemUTC());
  }

  LoginThrottle(Clock clock) {
    this.clock = clock;
  }

  public synchronized void check(String key) {
    var now = clock.millis();
    windows.entrySet().removeIf(entry -> now - entry.getValue().start() >= WINDOW_MS);
    var window = windows.get(key);
    if (window != null && window.attempts() >= 10) {
      throw new LockedException("Aguarde alguns minutos antes de tentar novamente.");
    }
    // Limite de memória; negar novas chaves em vez de descartar bloqueios ativos.
    if (window == null && windows.size() >= 10000) {
      throw new LockedException("Aguarde alguns minutos antes de tentar novamente.");
    }
  }

  public synchronized void failure(String key) {
    windows.compute(
        key,
        (_, prior) ->
            prior == null
                ? new Window(clock.millis(), 1)
                : new Window(prior.start(), prior.attempts() + 1));
  }
}
