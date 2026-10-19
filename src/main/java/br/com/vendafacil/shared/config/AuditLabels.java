package br.com.vendafacil.shared.config;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component("auditLabels")
public class AuditLabels {
  private static final Map<String, String> LABELS =
      Map.ofEntries(
          Map.entry("SALE_COMPLETED", "Venda de demonstração concluída"),
          Map.entry("SALE_DISCOUNT_APPROVED", "Desconto comercial aprovado"),
          Map.entry("CASH_REGISTER_CREATED", "Caixa cadastrado na filial"),
          Map.entry("CASH_OPENED", "Sessão de caixa aberta"),
          Map.entry("CASH_SUPPLIED", "Suprimento de caixa registrado"),
          Map.entry("CASH_WITHDRAWN", "Sangria de caixa registrada"),
          Map.entry("CASH_COUNT_DECLARED", "Contagem de caixa declarada"),
          Map.entry("CASH_CLOSED", "Sessão de caixa encerrada"),
          Map.entry("CASH_POLICY_CHANGED", "Políticas comerciais atualizadas"),
          Map.entry("STORE_SELECTED", "Filial selecionada para trabalhar"),
          Map.entry("STORE_CREATED", "Nova filial cadastrada"),
          Map.entry("STORE_UPDATED", "Informações da filial atualizadas"),
          Map.entry("USER_CREATED", "Novo acesso de usuário cadastrado"),
          Map.entry("USER_ACCESS_UPDATED", "Permissões da equipe atualizadas"),
          Map.entry("USER_ACCESS_BLOCKED", "Acesso de usuário bloqueado"),
          Map.entry("PRODUCT_CREATED", "Produto cadastrado no catálogo"),
          Map.entry("PRODUCT_UPDATED", "Cadastro de produto atualizado"),
          Map.entry("CATEGORY_SAVED", "Categoria de produtos atualizada"),
          Map.entry("PRICE_CHANGED", "Preço de produto atualizado na filial"),
          Map.entry("PRODUCT_IMAGE_SAVED", "Imagem de produto atualizada"),
          Map.entry("PRODUCT_IMAGE_REMOVED", "Imagem de produto removida"),
          Map.entry("STOCK_MOVED", "Movimentação de estoque registrada"),
          Map.entry("STOCK_MINIMUM_CHANGED", "Estoque mínimo atualizado"),
          Map.entry("NEGATIVE_STOCK_APPROVED", "Exceção de estoque negativo aprovada"),
          Map.entry("PRODUCT_IMPORT_PREVIEWED", "Prévia de importação de produtos gerada"),
          Map.entry("PRODUCT_IMPORT_COMPLETED", "Importação de produtos concluída"));

  public String label(String action) {
    return LABELS.getOrDefault(action, "Operação registrada na filial");
  }
}
