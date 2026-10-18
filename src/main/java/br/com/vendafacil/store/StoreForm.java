package br.com.vendafacil.store;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StoreForm(
    @NotBlank(message = "Informe o nome da filial.") @Size(max = 120) String name,
    @NotBlank(message = "Informe a cidade.") @Size(max = 100) String city,
    @NotBlank(message = "Informe a UF da filial.")
        @Pattern(
            regexp =
                "AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO",
            message = "Informe uma UF válida, como SP.")
        String state,
    Boolean active) {}
