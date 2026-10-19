package br.com.vendafacil.shared.validation;

public class FieldValidationException extends IllegalArgumentException {
  private final String field;

  public FieldValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String field() {
    return field;
  }
}
