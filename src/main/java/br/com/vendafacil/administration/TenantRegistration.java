package br.com.vendafacil.administration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantRegistration(
    @NotBlank(message = "Informe o nome da empresa.") @Size(max = 120) String name,
    @Pattern(
            regexp = "[a-z][a-z0-9-]{1,61}[a-z0-9]",
            message = "Use de 3 a 63 letras minúsculas, números e hífens, começando com letra.")
        @NotBlank
        String slug,
    @NotBlank(message = "Informe o nome do administrador.") @Size(max = 120) String adminName,
    @NotBlank(message = "Informe o e-mail do administrador.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 254)
        String email,
    @NotBlank @Size(min = 12, max = 64, message = "Use uma senha entre 12 e 64 caracteres.")
        String password) {}
