package br.com.vendafacil.inventory;

import br.com.vendafacil.shared.validation.DecimalValues;
import br.com.vendafacil.shared.validation.FieldValidationException;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class InventoryController {
  private final InventoryService inventory;

  public InventoryController(InventoryService inventory) {
    this.inventory = inventory;
  }

  @GetMapping("/estoque")
  public String balances(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "false") boolean low,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    model.addAttribute("products", inventory.balances(q, low, page));
    model.addAttribute("query", q);
    model.addAttribute("low", low);
    return "inventory/list";
  }

  @GetMapping("/estoque/movimentacoes")
  public String movements(
      @RequestParam(required = false) UUID product,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    model.addAttribute("movements", inventory.movements(product, page));
    model.addAttribute("productId", product);
    return "inventory/movements";
  }

  @GetMapping("/estoque/{id}")
  public String detail(@PathVariable UUID id, Model model) {
    var product = inventory.product(id);
    model.addAttribute("product", product);
    if (!model.containsAttribute("stockForm")) {
      model.addAttribute("stockForm", StockForm.empty());
    }
    if (!model.containsAttribute("minimum")) {
      model.addAttribute(
          "minimum",
          DecimalValues.display(
              product.minimum() == null
                  ? null
                  : "UN".equals(product.unit())
                      ? product.minimum().setScale(0)
                      : product.minimum()));
    }
    model.addAttribute("movements", inventory.movements(id, 0));
    return "inventory/detail";
  }

  @PostMapping("/estoque/{id}/movimentacoes")
  public String move(
      @PathVariable UUID id,
      @ModelAttribute("stockForm") StockForm form,
      BindingResult errors,
      Model model,
      RedirectAttributes redirect) {
    if (!errors.hasErrors()) {
      try {
        inventory.move(id, form);
        redirect.addFlashAttribute(
            "success",
            "Movimentação registrada. Reenvios da mesma operação não duplicam o estoque.");
        return "redirect:/estoque/" + id;
      } catch (FieldValidationException exception) {
        errors.rejectValue(exception.field(), "invalid", exception.getMessage());
      }
    }
    // Nunca devolver a senha do aprovador ao HTML, mesmo em erro de validação.
    var safeForm =
        new StockForm(
            form.operationId(),
            form.type(),
            form.quantity(),
            form.reason(),
            form.approverEmail(),
            "",
            form.approvalReason());
    var safeErrors = new BeanPropertyBindingResult(safeForm, "stockForm");
    safeErrors.addAllErrors(errors);
    model.addAttribute("stockForm", safeForm);
    model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "stockForm", safeErrors);
    return detail(id, model);
  }

  @PostMapping("/estoque/{id}/minimo")
  public String minimum(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "") String minimum,
      @RequestParam long version,
      Model model,
      RedirectAttributes redirect) {
    try {
      inventory.minimum(id, minimum, version);
      redirect.addFlashAttribute("success", "Estoque mínimo atualizado nesta filial.");
      return "redirect:/estoque/" + id;
    } catch (FieldValidationException exception) {
      model.addAttribute("minimum", minimum);
      model.addAttribute("minimumError", exception.getMessage());
      return detail(id, model);
    }
  }
}
