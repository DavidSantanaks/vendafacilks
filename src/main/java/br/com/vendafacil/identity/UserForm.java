package br.com.vendafacil.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UserForm(
    @NotBlank(message = "Informe o nome.") @Size(max = 120) String name,
    @Email(message = "Informe um e-mail válido.") @NotBlank @Size(max = 254) String email,
    @Size(min = 12, max = 64, message = "Use de 12 a 64 caracteres.") @NotBlank String password,
    @NotNull(message = "Selecione um perfil.") UUID roleId) {}
