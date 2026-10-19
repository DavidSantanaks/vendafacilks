package br.com.vendafacil.shared.validation;

import jakarta.validation.Validator;

public final class FormChecks {
  private FormChecks() {}

  public static <T> void validate(Validator validator, T form) {
    var errors = validator.validate(form);
    if (!errors.isEmpty()) {
      var error = errors.iterator().next();
      throw new FieldValidationException(error.getPropertyPath().toString(), error.getMessage());
    }
  }
}
