package br.com.vendafacil.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.vendafacil.catalog.importing.ImportFileReader;
import br.com.vendafacil.shared.validation.CsvFiles;
import br.com.vendafacil.shared.validation.DecimalValues;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CatalogInputTest {
  private final ImportFileReader reader = new ImportFileReader();

  @Test
  void decimalsPreserveKilogramsAndRejectFractionalUnitsAndAmbiguousMoney() {
    assertThat(DecimalValues.quantity("q", "1,250", "KG")).isEqualByComparingTo("1.250");
    assertThat(DecimalValues.quantity("q", "2.000", "UN")).isEqualByComparingTo("2");
    assertThatThrownBy(() -> DecimalValues.quantity("q", "1,250", "UN"))
        .hasMessageContaining("inteiras");
    for (var value : new String[] {"1.234,56", "1,234.56", "1e3", "NaN", "-", "1.999"}) {
      assertThatThrownBy(() -> DecimalValues.parse("price", value, 2))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void imageContentIsValidatedAndReencodedWithoutTrustingTheExtension() throws Exception {
    var output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB), "png", output);
    var image =
        ProductImageService.normalize(
            new MockMultipartFile("image", "foto.png", "image/png", output.toByteArray()));
    assertThat(image.width()).isEqualTo(20);
    assertThat(image.height()).isEqualTo(10);
    assertThat(ImageIO.read(new java.io.ByteArrayInputStream(image.content()))).isNotNull();
    assertThatThrownBy(
            () ->
                ProductImageService.normalize(
                    new MockMultipartFile("image", "foto.jpg", "image/jpeg", output.toByteArray())))
        .hasMessageContaining("extensão");
    assertThatThrownBy(
            () ->
                ProductImageService.normalize(
                    new MockMultipartFile(
                        "image",
                        "foto.png",
                        "image/png",
                        "<svg onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8))))
        .isInstanceOf(IllegalArgumentException.class);
    output.reset();
    ImageIO.write(new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB), "png", output);
    assertThatThrownBy(
            () ->
                ProductImageService.normalize(
                    new MockMultipartFile(
                        "image", "grande.png", "image/png", output.toByteArray())))
        .hasMessageContaining("dimensões");
  }

  @Test
  void csvSupportsUtf8BomQuotedDelimitersAndPreservesCodes() {
    var csv =
        "\uFEFF"
            + String.join(";", ImportFileReader.HEADER)
            + "\r\n1;SKU-01;\"Café; moído\";00000001;Mercearia;UN;19,90;10;2;;\r\n";
    var result =
        reader.read(
            new MockMultipartFile(
                "file", "produtos.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().getFirst().values().get(2)).isEqualTo("Café; moído");
    assertThat(result.rows().getFirst().values().get(3)).isEqualTo("00000001");
  }

  @Test
  void csvRejectsWrongHeadersInvalidEncodingAndMoreThanOneThousandRows() {
    assertThatThrownBy(
            () ->
                reader.read(
                    new MockMultipartFile(
                        "file",
                        "wrong.csv",
                        "text/csv",
                        "sku;nome\nA;Item".getBytes(StandardCharsets.UTF_8))))
        .hasMessageContaining("Cabeçalho");
    assertThatThrownBy(
            () ->
                reader.read(
                    new MockMultipartFile(
                        "file", "wrong.csv", "text/csv", new byte[] {(byte) 0xc3, 0x28})))
        .isInstanceOf(IllegalArgumentException.class);
    var tooMany =
        String.join(";", ImportFileReader.HEADER) + "\n" + "1;A;Item;;;UN;;;;;\n".repeat(1001);
    assertThatThrownBy(
            () ->
                reader.read(
                    new MockMultipartFile(
                        "file", "large.csv", "text/csv", tooMany.getBytes(StandardCharsets.UTF_8))))
        .hasMessageContaining("1.000");
  }

  @Test
  void xlsxReadsUsingEventsAndFlagsFormulasAndNumericIdentifiers() throws Exception {
    try (var workbook = new XSSFWorkbook();
        var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("Produtos");
      var header = sheet.createRow(0);
      for (int i = 0; i < ImportFileReader.HEADER.size(); i++) {
        header.createCell(i).setCellValue(ImportFileReader.HEADER.get(i));
      }
      var row = sheet.createRow(1);
      for (int i = 0; i < ImportFileReader.HEADER.size(); i++) {
        row.createCell(i).setCellValue("");
      }
      row.getCell(0).setCellValue("1");
      row.getCell(1).setCellValue(123);
      row.getCell(2).setCellValue("Produto");
      row.getCell(5).setCellValue("KG");
      row.getCell(6).setCellFormula("1+2");
      workbook.write(output);
      var result =
          reader.read(
              new MockMultipartFile(
                  "file", "dados.xlsx", "application/octet-stream", output.toByteArray()));
      assertThat(result.rows()).hasSize(1);
      assertThat(result.rows().getFirst().errors()).anyMatch(error -> error.contains("fórmulas"));
      assertThat(result.rows().getFirst().errors()).anyMatch(error -> error.contains("texto"));
    }
  }

  @Test
  void xlsxRejectsCompressedBombsBeforeOpeningTheWorkbook() throws Exception {
    var output = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(output)) {
      zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
      var zeros = new byte[16384];
      for (int i = 0; i < 513; i++) {
        zip.write(zeros);
      }
      zip.closeEntry();
    }
    assertThatThrownBy(
            () ->
                reader.read(
                    new MockMultipartFile(
                        "file", "bomb.xlsx", "application/octet-stream", output.toByteArray())))
        .hasMessageContaining("descompactado");
  }

  @Test
  void csvReportsPreventSpreadsheetFormulaInjection() {
    assertThat(CsvFiles.cell("  =HYPERLINK(\"https://invalid.example\")")).startsWith("\"'");
    assertThat(CsvFiles.cell("+1+1")).isEqualTo("\"'+1+1\"");
    assertThat(CsvFiles.cell("Produto; normal")).isEqualTo("\"Produto; normal\"");
  }
}
