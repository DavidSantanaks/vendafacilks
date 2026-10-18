package br.com.vendafacil.shared.security;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
  private PasswordPolicy() {}

  public static void validate(String password) {
    if (password == null
        || password.length() < 12
        || password.length() > 64
        || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new IllegalArgumentException(
          "Use de 12 a 64 caracteres na senha, com no máximo 72 bytes em UTF-8. Reduza os caracteres acentuados se necessário.");
    }
  }
}
