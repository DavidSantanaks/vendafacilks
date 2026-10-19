package br.com.vendafacil.catalog.importing;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProductImportController {
  private final ProductImportService imports;
  private final AccessService access;

  public ProductImportController(ProductImportService imports, AccessService access) {
    this.imports = imports;
    this.access = access;
  }

  @GetMapping("/produtos/importacoes")
  public String index(Model model) {
    model.addAttribute("jobs", imports.recent());
    return "catalog/imports/index";
  }

  @PostMapping("/produtos/importacoes")
  public String preview(@RequestParam MultipartFile file, Model model) {
    access.require(Permission.PRODUCT_IMPORT);
    try {
      return "redirect:/produtos/importacoes/" + imports.preview(file);
    } catch (IllegalArgumentException exception) {
      model.addAttribute("fileError", exception.getMessage());
      return index(model);
    }
  }

  @GetMapping("/produtos/importacoes/{id}")
  public String detail(
      @PathVariable UUID id, @RequestParam(defaultValue = "0") int page, Model model) {
    model.addAttribute("preview", imports.detail(id, page));
    return "catalog/imports/preview";
  }

  @PostMapping("/produtos/importacoes/{id}/confirmar")
  public String confirm(@PathVariable UUID id, RedirectAttributes redirect) {
    boolean done = imports.confirm(id);
    redirect.addFlashAttribute(
        done ? "success" : "warning",
        done
            ? "Importação concluída. Produtos, preços e estoque foram gravados em uma única transação."
            : "O catálogo ou as permissões mudaram. Confira os erros; nenhum produto foi importado.");
    return "redirect:/produtos/importacoes/" + id;
  }

  @GetMapping("/produtos/importacoes/{id}/relatorio")
  public ResponseEntity<String> report(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=importacao-" + id + ".csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(imports.report(id));
  }

  @GetMapping("/produtos/importacoes/modelo.csv")
  public ResponseEntity<String> csvTemplate() {
    access.require(Permission.PRODUCT_IMPORT);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=produtos-v1.csv")
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .body(
            "\uFEFF"
                + String.join(";", ImportFileReader.HEADER)
                + "\r\n1;PRODUTO-001;Produto de exemplo;;Mercearia;UN;19,90;10;2;;\r\n");
  }

  @GetMapping("/produtos/importacoes/modelo.xlsx")
  public ResponseEntity<byte[]> xlsxTemplate() throws IOException {
    access.require(Permission.PRODUCT_IMPORT);
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("Produtos v1");
      var header = sheet.createRow(0);
      for (int i = 0; i < ImportFileReader.HEADER.size(); i++) {
        header.createCell(i).setCellValue(ImportFileReader.HEADER.get(i));
        sheet.setColumnWidth(i, 5000);
      }
      var row = sheet.createRow(1);
      var values =
          java.util.List.of(
              "1",
              "PRODUTO-001",
              "Produto de exemplo",
              "",
              "Mercearia",
              "UN",
              "19,90",
              "10",
              "2",
              "",
              "");
      for (int i = 0; i < values.size(); i++) {
        row.createCell(i).setCellValue(values.get(i));
      }
      workbook.write(output);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=produtos-v1.xlsx")
          .contentType(
              MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(output.toByteArray());
    }
  }
}
