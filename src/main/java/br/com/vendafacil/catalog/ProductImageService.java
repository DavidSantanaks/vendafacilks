package br.com.vendafacil.catalog;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Permission;
import br.com.vendafacil.shared.audit.AuditService;
import br.com.vendafacil.shared.validation.FieldValidationException;
import br.com.vendafacil.tenant.TenantContext;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProductImageService {
  public record Image(byte[] content, int width, int height) {}

  private final JdbcTemplate jdbc;
  private final AccessService access;
  private final TenantContext tenant;
  private final CatalogRepository products;
  private final AuditService audit;

  public ProductImageService(
      JdbcTemplate jdbc,
      AccessService access,
      TenantContext tenant,
      CatalogRepository products,
      AuditService audit) {
    this.jdbc = jdbc;
    this.access = access;
    this.tenant = tenant;
    this.products = products;
    this.audit = audit;
  }

  @Transactional
  public void upload(UUID productId, MultipartFile file) {
    access.require(Permission.PRODUCT_WRITE);
    products.lockProduct(tenant.id(), productId);
    products.find(tenant.id(), access.activeStore().getId(), productId, false);
    var image = normalize(file);
    jdbc.update(
        """
        INSERT INTO product_images(id,tenant_id,product_id,content,width,height) VALUES(?,?,?,?,?,?)
        ON CONFLICT(tenant_id,product_id) DO UPDATE SET content=EXCLUDED.content,width=EXCLUDED.width,height=EXCLUDED.height,created_at=CURRENT_TIMESTAMP
        """,
        UUID.randomUUID(),
        tenant.id(),
        productId,
        image.content(),
        image.width(),
        image.height());
    audit.record(
        tenant.id(),
        access.activeStore().getId(),
        access.principal().userId(),
        "PRODUCT_IMAGE_SAVED",
        productId);
  }

  @Transactional(readOnly = true)
  public byte[] read(UUID productId) {
    access.require(Permission.PRODUCT_READ);
    products.find(tenant.id(), access.activeStore().getId(), productId, false);
    return jdbc
        .query(
            "SELECT content FROM product_images WHERE tenant_id=? AND product_id=?",
            (rs, n) -> rs.getBytes(1),
            tenant.id(),
            productId)
        .stream()
        .findFirst()
        .orElseThrow(() -> new AccessDeniedException("Imagem indisponível."));
  }

  @Transactional
  public void remove(UUID productId) {
    access.require(Permission.PRODUCT_WRITE);
    products.lockProduct(tenant.id(), productId);
    products.find(tenant.id(), access.activeStore().getId(), productId, false);
    jdbc.update(
        "DELETE FROM product_images WHERE tenant_id=? AND product_id=?", tenant.id(), productId);
    audit.record(
        tenant.id(),
        access.activeStore().getId(),
        access.principal().userId(),
        "PRODUCT_IMAGE_REMOVED",
        productId);
  }

  public static Image normalize(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
      throw invalid("Envie uma imagem PNG ou JPEG de até 5 MB.");
    }
    var name =
        file.getOriginalFilename() == null
            ? ""
            : file.getOriginalFilename().toLowerCase(Locale.ROOT);
    if (!(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"))) {
      throw invalid("Use um arquivo .png, .jpg ou .jpeg.");
    }
    try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw invalid("O conteúdo não é uma imagem PNG/JPEG válida.");
      }
      var reader = readers.next();
      try {
        reader.setInput(input, true, true);
        var format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!format.equals("png") && !format.equals("jpeg")) {
          throw invalid("O conteúdo deve ser PNG ou JPEG.");
        }
        if ((format.equals("png") && !name.endsWith(".png"))
            || (format.equals("jpeg") && !(name.endsWith(".jpg") || name.endsWith(".jpeg")))) {
          throw invalid("A extensão não corresponde ao conteúdo da imagem.");
        }
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1
            || height < 1
            || width > 4096
            || height > 4096
            || (long) width * height > 8_000_000) {
          throw invalid("Use dimensões de até 4096 × 4096 e no máximo 8 milhões de pixels.");
        }
        var source = reader.read(0);
        double ratio = Math.min(1.0, 1200.0 / Math.max(width, height));
        var normalized =
            new BufferedImage(
                Math.max(1, (int) (width * ratio)),
                Math.max(1, (int) (height * ratio)),
                BufferedImage.TYPE_INT_ARGB);
        var graphics = normalized.createGraphics();
        try {
          graphics.setRenderingHint(
              RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
          graphics.drawImage(source, 0, 0, normalized.getWidth(), normalized.getHeight(), null);
        } finally {
          graphics.dispose();
          source.flush();
        }
        var output = new ByteArrayOutputStream();
        ImageIO.write(normalized, "png", output);
        if (output.size() > 3 * 1024 * 1024) {
          throw invalid("A imagem normalizada excede 3 MB. Use uma imagem menor.");
        }
        return new Image(output.toByteArray(), normalized.getWidth(), normalized.getHeight());
      } finally {
        reader.dispose();
      }
    } catch (IOException exception) {
      throw invalid("Não foi possível ler a imagem. Envie outro PNG ou JPEG.");
    }
  }

  private static FieldValidationException invalid(String message) {
    return new FieldValidationException("image", message);
  }
}
