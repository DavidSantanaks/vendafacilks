package br.com.vendafacil.catalog.importing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipInputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

@Component
public class ImportFileReader {
  public static final int MAX_ROWS = 1000;
  public static final List<String> HEADER =
      List.of(
          "versao",
          "sku",
          "nome",
          "codigo_barras",
          "categoria",
          "unidade",
          "preco",
          "estoque_inicial",
          "estoque_minimo",
          "ncm",
          "cest");

  public record Row(int number, List<String> values, List<String> errors) {}

  public record File(String name, byte[] bytes, List<Row> rows) {}

  public File read(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
      throw invalid("Envie um CSV UTF-8 ou XLSX de até 5 MB.");
    }
    var filename =
        file.getOriginalFilename() == null
            ? "importacao"
            : file.getOriginalFilename().replace('\\', '/');
    filename = filename.substring(filename.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
    if (filename.length() > 120) {
      filename = filename.substring(filename.length() - 120);
    }
    var extension = filename.toLowerCase(Locale.ROOT);
    try {
      var bytes = file.getBytes();
      List<Row> rows;
      if (extension.endsWith(".csv")) {
        rows = csv(bytes);
      } else if (extension.endsWith(".xlsx")) {
        rows = xlsx(bytes);
      } else {
        throw invalid("Use .csv ou .xlsx, sem macros.");
      }
      if (rows.size() < 2) {
        throw invalid("Inclua o cabeçalho e ao menos um produto.");
      }
      if (!rows.getFirst().values().equals(HEADER) || !rows.getFirst().errors().isEmpty()) {
        throw invalid(
            "Cabeçalho inválido. Baixe o modelo versão 1 e preserve a ordem das colunas.");
      }
      return new File(filename, bytes, List.copyOf(rows.subList(1, rows.size())));
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (Exception exception) {
      throw invalid(
          "Não foi possível ler o arquivo. Confira UTF-8, o modelo e os limites de tamanho/linhas.");
    }
  }

  private List<Row> csv(byte[] bytes) throws IOException {
    var decoded =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString();
    if (decoded.startsWith("\uFEFF")) {
      decoded = decoded.substring(1);
    }
    var rows = new ArrayList<Row>();
    var format = CSVFormat.DEFAULT.builder().setDelimiter(';').setIgnoreEmptyLines(true).get();
    try (var parser = format.parse(new StringReader(decoded))) {
      for (var record : parser) {
        if (rows.size() > MAX_ROWS) {
          throw invalid("O limite é de 1.000 produtos por arquivo.");
        }
        var values = new ArrayList<String>();
        for (var value : record) {
          values.add(value);
        }
        rows.add(bounded(rows.size() + 1, values, List.of()));
      }
    }
    return rows;
  }

  private List<Row> xlsx(byte[] bytes) throws Exception {
    checkZip(bytes);
    try (var pack = OPCPackage.open(new ByteArrayInputStream(bytes))) {
      var reader = new XSSFReader(pack);
      reader.setUseReadOnlySharedStringsTable(true);
      var shared = reader.getSharedStringsTable();
      var styles = reader.getStylesTable();
      if (styles != null && styles.getNumCellStyles() > 1000) {
        throw invalid("A planilha excede o limite de estilos. Use o modelo simples.");
      }
      var sheets = reader.getSheetsData();
      if (!sheets.hasNext()) {
        throw invalid("O arquivo não contém uma planilha.");
      }
      var handler = new SheetHandler(shared, styles);
      try (var sheet = sheets.next()) {
        var xml = XMLHelper.newXMLReader();
        xml.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        xml.setFeature("http://xml.org/sax/features/external-general-entities", false);
        xml.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        xml.setEntityResolver(
            (publicId, systemId) -> {
              throw new SAXException("Referência externa não permitida.");
            });
        xml.setContentHandler(handler);
        xml.parse(new InputSource(sheet));
      }
      if (sheets.hasNext()) {
        throw invalid("Use uma única aba com os produtos.");
      }
      return handler.rows;
    }
  }

  private void checkZip(byte[] bytes) throws IOException {
    long total = 0;
    int entries = 0;
    var names = new HashSet<String>();
    try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      java.util.zip.ZipEntry entry;
      var buffer = new byte[8192];
      while ((entry = zip.getNextEntry()) != null) {
        var name = entry.getName().toLowerCase(Locale.ROOT);
        if (++entries > 100
            || !names.add(name)
            || name.contains("..")
            || name.startsWith("/")
            || name.contains("\\")
            || name.contains("vbaproject")
            || name.contains("externallinks/")
            || name.contains("embeddings/")) {
          throw invalid(
              "Planilha com estrutura não permitida. Use o modelo simples, sem macros ou vínculos externos.");
        }
        long size = 0;
        int read;
        while ((read = zip.read(buffer)) != -1) {
          total += read;
          size += read;
          if (size > 8L * 1024 * 1024 || total > 20L * 1024 * 1024) {
            throw invalid("O conteúdo descompactado excede o limite seguro da importação.");
          }
        }
      }
    }
    if (entries == 0
        || !names.contains("[content_types].xml")
        || !names.contains("xl/workbook.xml")) {
      throw invalid("O conteúdo não é um XLSX válido.");
    }
  }

  private static Row bounded(int number, List<String> input, List<String> issues) {
    var errors = new ArrayList<>(issues);
    if (input.size() != HEADER.size()) {
      errors.add("Quantidade de colunas diferente do modelo.");
    }
    var values = new ArrayList<String>();
    for (int index = 0; index < HEADER.size(); index++) {
      var value = index < input.size() ? input.get(index).trim() : "";
      if (value.length() > 256) {
        errors.add("Coluna " + HEADER.get(index) + ": máximo de 256 caracteres.");
        value = value.substring(0, 256);
      }
      if (value.chars().anyMatch(Character::isISOControl)) {
        errors.add(
            "Coluna "
                + HEADER.get(index)
                + ": não use quebras de linha ou caracteres de controle.");
      }
      values.add(value);
    }
    return new Row(number, List.copyOf(values), List.copyOf(errors));
  }

  private static IllegalArgumentException invalid(String message) {
    return new IllegalArgumentException(message);
  }

  private static final class SheetHandler extends DefaultHandler {
    private final SharedStrings shared;
    private final StylesTable styles;
    private final List<Row> rows = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();
    private List<String> cells;
    private List<String> errors;
    private int rowNumber;
    private int column;
    private String type;
    private boolean collecting;
    private boolean formula;
    private boolean date;

    SheetHandler(SharedStrings shared, StylesTable styles) {
      this.shared = shared;
      this.styles = styles;
    }

    @Override
    public void startElement(String uri, String local, String qName, Attributes attributes) {
      if ("row".equals(local)) {
        rowNumber = Integer.parseInt(attributes.getValue("r"));
        if (rowNumber < 1 || rowNumber > MAX_ROWS + 1 || rows.size() > MAX_ROWS) {
          throw invalid("O limite é de 1.000 produtos e um cabeçalho.");
        }
        if (rowNumber != rows.size() + 1) {
          throw invalid("Remova linhas vazias entre os registros da planilha.");
        }
        cells = new ArrayList<>(java.util.Collections.nCopies(HEADER.size(), ""));
        errors = new ArrayList<>();
      } else if ("c".equals(local)) {
        column = new CellReference(attributes.getValue("r")).getCol();
        if (column < 0 || column >= HEADER.size()) {
          throw invalid("A planilha possui colunas além do modelo.");
        }
        type = attributes.getValue("t");
        formula = false;
        date = false;
        text.setLength(0);
        var style = attributes.getValue("s");
        if (styles != null && style != null) {
          var cellStyle = styles.getStyleAt(Integer.parseInt(style));
          date = DateUtil.isADateFormat(cellStyle.getDataFormat(), cellStyle.getDataFormatString());
        }
      } else if ("f".equals(local)) {
        formula = true;
      } else if ("v".equals(local) || "t".equals(local)) {
        collecting = true;
      }
    }

    @Override
    public void characters(char[] chars, int start, int length) {
      if (collecting) {
        if (text.length() + length > 65536) {
          throw invalid("Uma célula excede o limite de texto permitido.");
        }
        text.append(chars, start, length);
      }
    }

    @Override
    public void endElement(String uri, String local, String qName) {
      if ("v".equals(local) || "t".equals(local)) {
        collecting = false;
      } else if ("c".equals(local)) {
        var value = text.toString();
        if ("s".equals(type) && !value.isEmpty()) {
          value = shared.getItemAt(Integer.parseInt(value)).getString();
        }
        if (formula) {
          errors.add("Coluna " + HEADER.get(column) + ": fórmulas não são permitidas.");
        }
        if (date || "d".equals(type) || "e".equals(type) || "b".equals(type)) {
          errors.add(
              "Coluna " + HEADER.get(column) + ": use texto ou número, sem datas/erros/fórmulas.");
        }
        if (rowNumber > 1
            && (column == 1 || column == 3)
            && !value.isBlank()
            && !"s".equals(type)
            && !"inlineStr".equals(type)
            && !"str".equals(type)) {
          errors.add(
              "Coluna "
                  + HEADER.get(column)
                  + ": formate como texto para preservar zeros e códigos.");
        }
        cells.set(column, value);
      } else if ("row".equals(local)) {
        rows.add(bounded(rowNumber, cells, errors));
      }
    }
  }
}
