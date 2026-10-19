package br.com.vendafacil.inventory;

import java.util.UUID;

public record StockForm(
    UUID operationId,
    String type,
    String quantity,
    String reason,
    String approverEmail,
    String approverPassword,
    String approvalReason) {
  public static StockForm empty() {
    return new StockForm(UUID.randomUUID(), "ENTRY", "", "", "", "", "");
  }

  @Override
  public String toString() {
    return "StockForm[credentials=REDACTED]";
  }
}
