package br.com.vendafacil.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

public final class SecurityResponses {
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private SecurityResponses() {}

  public static void error(
      HttpServletRequest request, HttpServletResponse response, int status, String detail)
      throws IOException {
    if (!request.getRequestURI().startsWith("/api/")) {
      response.sendError(status, detail);
      return;
    }
    response.setStatus(status);
    response.setContentType("application/problem+json;charset=UTF-8");
    var reference = request.getAttribute("correlationId");
    MAPPER.writeValue(
        response.getWriter(),
        Map.of(
            "type",
            "about:blank",
            "title",
            "Acesso indisponível",
            "status",
            status,
            "detail",
            detail,
            "correlationId",
            reference == null ? UUID.randomUUID().toString() : reference));
  }
}
