package br.com.vendafacil.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AppProperties(
    String baseDomain, boolean demoEnabled, String demoPassword, boolean requireHttps) {}
