package br.com.vendafacil.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.vendafacil.shared.config.AppProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HostResolverTest {
  private final HostResolver resolver =
      new HostResolver(new AppProperties("localhost", false, "", false));

  @ParameterizedTest
  @ValueSource(strings = {"boa-praca.localhost", "BOA-PRACA.LOCALHOST:8080"})
  void acceptsOnlyDirectTenantSubdomains(String host) {
    assertThat(resolver.resolve(host)).contains("boa-praca");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "localhost",
        "evil.localhost.attacker.com",
        "nested.boa-praca.localhost",
        "boa-praca.localhost.",
        "boa-praca.localhost@evil.com",
        "boa-praca.localhost:abc",
        "-bad.localhost",
        "a.localhost",
        "boa-praca.localhost,evil.com",
        "boa-praca.localhost\r\nX-Test: injected"
      })
  void rejectsUntrustedOrAmbiguousHosts(String host) {
    assertThat(resolver.resolve(host)).isEmpty();
  }
}
