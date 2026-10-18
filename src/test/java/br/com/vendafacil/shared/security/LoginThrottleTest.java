package br.com.vendafacil.shared.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.LockedException;

class LoginThrottleTest {
  @Test
  void blocksRepeatedFailuresWithoutBlockingOtherTenants() {
    var throttle =
        new LoginThrottle(Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC));
    for (int attempt = 0; attempt < 10; attempt++) {
      throttle.check("boa-praca:127.0.0.1");
      throttle.failure("boa-praca:127.0.0.1");
    }
    assertThatThrownBy(() -> throttle.check("boa-praca:127.0.0.1"))
        .isInstanceOf(LockedException.class);
    assertThatCode(() -> throttle.check("vila-verde:127.0.0.1")).doesNotThrowAnyException();
  }
}
