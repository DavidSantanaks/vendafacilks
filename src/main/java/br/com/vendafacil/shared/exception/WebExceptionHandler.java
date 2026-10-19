package br.com.vendafacil.shared.exception;

import br.com.vendafacil.identity.StoreSelectionRequired;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class WebExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(WebExceptionHandler.class);

  @ExceptionHandler(StoreSelectionRequired.class)
  public Object selectStore(HttpServletRequest request) {
    if (request.getRequestURI().startsWith("/api/")) {
      return problem(HttpStatus.CONFLICT, "Selecione uma filial antes de continuar.", request);
    }
    return new ModelAndView("redirect:/filiais");
  }

  @ExceptionHandler(AccessDeniedException.class)
  public Object forbidden(HttpServletRequest request) {
    return error(HttpStatus.FORBIDDEN, "Você não tem acesso a esta operação ou filial.", request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public Object invalid(IllegalArgumentException exception, HttpServletRequest request) {
    return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public Object conflict(HttpServletRequest request) {
    LOG.warn("Operação rejeitada por integridade de dados.");
    return error(
        HttpStatus.CONFLICT,
        "Os dados entraram em conflito com outro cadastro. Atualize a página e confira as informações.",
        request);
  }

  private Object error(HttpStatus status, String message, HttpServletRequest request) {
    if (request.getRequestURI().startsWith("/api/")) {
      return problem(status, message, request);
    }
    return new ModelAndView(
        "error/error", Map.of("status", status.value(), "message", message), status);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public Object uploadTooLarge(HttpServletRequest request) {
    return error(
        HttpStatus.PAYLOAD_TOO_LARGE,
        "O arquivo excede o limite de 5 MB. Reduza o tamanho e tente novamente.",
        request);
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String message, HttpServletRequest request) {
    var problem = ProblemDetail.forStatusAndDetail(status, message);
    problem.setTitle("Não foi possível concluir a operação");
    problem.setProperty("correlationId", request.getAttribute("correlationId"));
    return ResponseEntity.status(status).body(problem);
  }
}
