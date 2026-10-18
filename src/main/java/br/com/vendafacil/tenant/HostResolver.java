package br.com.vendafacil.tenant;

import br.com.vendafacil.shared.config.AppProperties;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class HostResolver {
  private final String baseDomain;

  public HostResolver(AppProperties properties) {
    baseDomain = properties.baseDomain().toLowerCase(Locale.ROOT);
    if (!baseDomain.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*")) {
      throw new IllegalArgumentException("APP_BASE_DOMAIN inválido.");
    }
  }

  public Optional<String> resolve(String hostHeader) {
    if (hostHeader == null || !hostHeader.matches("(?i)[a-z0-9.-]+(?::[0-9]{1,5})?")) {
      return Optional.empty();
    }
    var host = hostHeader.toLowerCase(Locale.ROOT).split(":", 2)[0];
    if (!host.endsWith("." + baseDomain)) {
      return Optional.empty();
    }
    var slug = host.substring(0, host.length() - baseDomain.length() - 1);
    return slug.matches("[a-z][a-z0-9-]{1,61}[a-z0-9]") ? Optional.of(slug) : Optional.empty();
  }
}
