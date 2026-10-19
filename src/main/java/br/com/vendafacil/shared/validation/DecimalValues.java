package br.com.vendafacil.shared.validation;

import java.math.BigDecimal;

public final class DecimalValues {
  private DecimalValues() {}

  public static BigDecimal parse(String field, String text, int scale) {
    if (text == null || !text.trim().matches("-?[0-9]{1,17}([.,][0-9]{1," + scale + "})?")) {
      throw new FieldValidationException(
          field,
          "Informe um número sem separador de milhar, com até " + scale + " casas decimais.");
    }
    var value = new BigDecimal(text.trim().replace(',', '.'));
    if (value.precision() - value.scale() > 19 - scale) {
      throw new FieldValidationException(field, "O valor excede o limite permitido.");
    }
    return value.setScale(scale);
  }

  public static BigDecimal quantity(String field, String text, String unit) {
    var value = parse(field, text, 3);
    if ("UN".equals(unit) && value.stripTrailingZeros().scale() > 0) {
      throw new FieldValidationException(
          field, "Produtos UN aceitam somente quantidades inteiras.");
    }
    return value;
  }

  public static String display(BigDecimal value) {
    return value == null ? "" : value.toPlainString().replace('.', ',');
  }
}
