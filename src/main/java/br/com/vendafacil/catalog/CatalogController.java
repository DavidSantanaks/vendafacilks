package br.com.vendafacil.catalog;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import br.com.vendafacil.shared.validation.DecimalValues;
import br.com.vendafacil.shared.validation.FieldValidationException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CatalogController {
  private final CatalogService catalog;
  private final PricingService prices;
  private final ProductImageService images;
  private final AccessService access;

  public CatalogController(
      CatalogService catalog,
      PricingService prices,
      ProductImageService images,
      AccessService access) {
    this.catalog = catalog;
    this.prices = prices;
    this.images = images;
    this.access = access;
  }

  @GetMapping("/produtos")
  public String list(
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "active") String status,
      @RequestParam(required = false) UUID category,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    model.addAttribute("products", catalog.list(q, status, category, page));
    model.addAttribute("categories", catalog.categories());
    model.addAttribute("query", q);
    model.addAttribute("filterStatus", status);
    model.addAttribute("filterCategory", category);
    return "catalog/list";
  }

  @GetMapping("/produtos/novo")
  public String createForm(Model model) {
    access.require(Permission.PRODUCT_WRITE);
    if (!model.containsAttribute("productForm")) {
      model.addAttribute("productForm", ProductForm.empty());
    }
    model.addAttribute("categories", catalog.categories());
    model.addAttribute("editing", false);
    return "catalog/form";
  }

  @PostMapping("/produtos")
  public String create(
      @Valid @ModelAttribute("productForm") ProductForm form,
      BindingResult errors,
      Model model,
      RedirectAttributes redirect) {
    access.require(Permission.PRODUCT_WRITE);
    if (!errors.hasErrors()) {
      try {
        var id = catalog.create(form);
        redirect.addFlashAttribute(
            "success", "Produto criado no catálogo da empresa. Defina o preço na filial desejada.");
        return "redirect:/produtos/" + id;
      } catch (FieldValidationException exception) {
        errors.rejectValue(exception.field(), "invalid", exception.getMessage());
      }
    }
    return createForm(model);
  }

  @GetMapping("/produtos/{id}/editar")
  public String editForm(@PathVariable UUID id, Model model) {
    access.require(Permission.PRODUCT_WRITE);
    var product = catalog.detail(id);
    model.addAttribute("product", product);
    if (!model.containsAttribute("productForm")) {
      model.addAttribute(
          "productForm",
          new ProductForm(
              product.sku(),
              product.name(),
              value(product.barcode()),
              product.categoryId(),
              product.unit(),
              product.active(),
              value(product.ncm()),
              value(product.cest()),
              product.version()));
    }
    model.addAttribute("categories", catalog.categories());
    model.addAttribute("editing", true);
    return "catalog/form";
  }

  @PostMapping("/produtos/{id}")
  public String update(
      @PathVariable UUID id,
      @Valid @ModelAttribute("productForm") ProductForm form,
      BindingResult errors,
      Model model,
      RedirectAttributes redirect) {
    access.require(Permission.PRODUCT_WRITE);
    if (!errors.hasErrors()) {
      try {
        catalog.update(id, form);
        redirect.addFlashAttribute(
            "success",
            "Cadastro atualizado para toda a empresa. Preços e saldos continuam separados por filial.");
        return "redirect:/produtos/" + id;
      } catch (FieldValidationException exception) {
        errors.rejectValue(exception.field(), "invalid", exception.getMessage());
      }
    }
    return editForm(id, model);
  }

  @GetMapping("/produtos/{id}")
  public String detail(@PathVariable UUID id, Model model) {
    var product = catalog.detail(id);
    model.addAttribute("product", product);
    if (!model.containsAttribute("priceForm")) {
      model.addAttribute(
          "priceForm",
          new PriceForm(
              DecimalValues.display(product.price()),
              product.price() == null || product.priceActive(),
              product.priceVersion()));
    }
    model.addAttribute("priceHistory", prices.history(id));
    return "catalog/detail";
  }

  @PostMapping("/produtos/{id}/preco")
  public String price(
      @PathVariable UUID id,
      @ModelAttribute("priceForm") PriceForm form,
      Model model,
      RedirectAttributes redirect) {
    try {
      prices.save(id, form);
      redirect.addFlashAttribute("success", "Preço atualizado somente nesta filial.");
      return "redirect:/produtos/" + id;
    } catch (FieldValidationException exception) {
      model.addAttribute("priceError", exception.getMessage());
      return detail(id, model);
    }
  }

  @GetMapping("/produtos/{id}/imagem")
  public ResponseEntity<byte[]> image(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.IMAGE_PNG)
        .body(images.read(id));
  }

  @PostMapping("/produtos/{id}/imagem")
  public String upload(
      @PathVariable UUID id,
      @RequestParam MultipartFile image,
      Model model,
      RedirectAttributes redirect) {
    try {
      images.upload(id, image);
      redirect.addFlashAttribute("success", "Imagem validada e salva no catálogo da empresa.");
      return "redirect:/produtos/" + id;
    } catch (FieldValidationException exception) {
      model.addAttribute("imageError", exception.getMessage());
      return detail(id, model);
    }
  }

  @PostMapping("/produtos/{id}/imagem/remover")
  public String removeImage(@PathVariable UUID id, RedirectAttributes redirect) {
    images.remove(id);
    redirect.addFlashAttribute("success", "Imagem removida.");
    return "redirect:/produtos/" + id;
  }

  @GetMapping("/produtos/categorias")
  public String categories(Model model) {
    access.require(Permission.PRODUCT_WRITE);
    model.addAttribute("categories", catalog.categories());
    return "catalog/categories";
  }

  @PostMapping("/produtos/categorias")
  public String createCategory(
      @RequestParam String name, Model model, RedirectAttributes redirect) {
    try {
      catalog.saveCategory(null, name, true, 0);
    } catch (FieldValidationException exception) {
      model.addAttribute("categoryError", exception.getMessage());
      model.addAttribute("categoryName", name);
      return categories(model);
    }
    redirect.addFlashAttribute("success", "Categoria criada.");
    return "redirect:/produtos/categorias";
  }

  @PostMapping("/produtos/categorias/{id}")
  public String updateCategory(
      @PathVariable UUID id,
      @RequestParam String name,
      @RequestParam(defaultValue = "false") boolean active,
      @RequestParam long version,
      RedirectAttributes redirect) {
    catalog.saveCategory(id, name, active, version);
    redirect.addFlashAttribute("success", "Categoria atualizada.");
    return "redirect:/produtos/categorias";
  }

  @GetMapping("/api/v1/produtos/busca")
  @ResponseBody
  public List<CatalogService.SearchProduct> search(@RequestParam(defaultValue = "") String q) {
    return catalog.search(q);
  }

  private static String value(String text) {
    return text == null ? "" : text;
  }
}
