package br.com.vendafacil.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PortalUrls {
  private final AppProperties properties;
  private final String port;

  public PortalUrls(AppProperties properties, @Value("${app.public-port:}") String port) {
    this.properties = properties;
    if (!port.isBlank()
        && (!port.matches("[0-9]{1,5}")
            || Integer.parseInt(port) < 1
            || Integer.parseInt(port) > 65535)) {
      throw new IllegalArgumentException("Porta pública inválida.");
    }
    this.port = port;
  }

  public String origin(String slug) {
    if (!slug.matches("[a-z][a-z0-9-]{1,61}[a-z0-9]")) {
      throw new IllegalArgumentException("Portal inválido.");
    }
    return (properties.requireHttps() ? "https://" : "http://")
        + slug
        + "."
        + properties.baseDomain()
        + (port.isBlank() ? "" : ":" + port);
  }

  public String login(String slug) {
    return origin(slug) + "/login";
  }
}
