package br.com.vendafacil;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class PostgresTestSupport {
  private static final PostgreSQLContainer POSTGRES;

  static {
    if (System.getenv("TEST_DB_URL") == null) {
      POSTGRES = new PostgreSQLContainer("postgres:17.11-alpine");
      POSTGRES.start();
    } else {
      POSTGRES = null;
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () -> POSTGRES == null ? System.getenv("TEST_DB_URL") : POSTGRES.getJdbcUrl());
    registry.add(
        "spring.datasource.username",
        () ->
            POSTGRES == null
                ? System.getenv().getOrDefault("TEST_DB_USER", "vendafacil")
                : POSTGRES.getUsername());
    registry.add(
        "spring.datasource.password",
        () ->
            POSTGRES == null
                ? System.getenv().getOrDefault("TEST_DB_PASSWORD", "")
                : POSTGRES.getPassword());
    registry.add("app.base-domain", () -> "localhost");
    registry.add("app.public-port", () -> "8080");
    registry.add("app.require-https", () -> false);
    registry.add("app.demo-enabled", () -> true);
    registry.add("app.demo-password", () -> "Test-Only-VendaFacil-2026!");
  }
}
