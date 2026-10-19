package br.com.vendafacil.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ProductForm(
    @NotBlank(message = "Informe o SKU.")
        @Pattern(
            regexp = "[A-Za-z0-9][A-Za-z0-9._/-]{0,59}",
            message = "Use até 60 letras, números, ponto, hífen, barra ou sublinhado.")
        String sku,
    @NotBlank(message = "Informe o nome do produto.")
        @Size(max = 160, message = "Use até 160 caracteres.")
        String name,
    @Pattern(
            regexp = "|[A-Za-z0-9._/-]{1,64}",
            message = "Use até 64 letras/números, sem espaços, no código de barras.")
        String barcode,
    UUID categoryId,
    @NotBlank @Pattern(regexp = "UN|KG", message = "Selecione UN ou KG.") String unit,
    Boolean active,
    @Pattern(regexp = "|[0-9]{8}", message = "NCM deve ter 8 dígitos ou ficar vazio.") String ncm,
    @Pattern(regexp = "|[0-9]{7}", message = "CEST deve ter 7 dígitos ou ficar vazio.") String cest,
    long version) {
  public static ProductForm empty() {
    return new ProductForm("", "", "", null, "UN", true, "", "", 0);
  }
}
