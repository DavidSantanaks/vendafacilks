package br.com.vendafacil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.vendafacil.catalog.importing.ImportFileReader;
import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Membership;
import br.com.vendafacil.identity.MembershipRepository;
import br.com.vendafacil.identity.RoleRepository;
import br.com.vendafacil.identity.StoreRoleGrant;
import br.com.vendafacil.identity.StoreRoleRepository;
import br.com.vendafacil.identity.UserAccount;
import br.com.vendafacil.identity.UserRepository;
import br.com.vendafacil.store.StoreRepository;
import br.com.vendafacil.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogAndStockIntegrationTest extends PostgresTestSupport {
  private static final String A = "boa-praca.localhost";
  private static final String B = "vila-verde.localhost";
  private static final String PASSWORD = "Test-Only-VendaFacil-2026!";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired TenantRepository tenants;
  @Autowired StoreRepository stores;
  @Autowired UserRepository users;
  @Autowired MembershipRepository memberships;
  @Autowired StoreRoleRepository grants;
  @Autowired RoleRepository roles;
  @Autowired EntityManager entityManager;
  @Autowired javax.sql.DataSource dataSource;

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void migrationFromVersionTwoPreservesExistingCompaniesAndUsers() {
    var schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    try {
      org.flywaydb.core.Flyway.configure()
          .dataSource(dataSource)
          .schemas(schema)
          .defaultSchema(schema)
          .target("2")
          .load()
          .migrate();
      var company = UUID.randomUUID();
      var user = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO " + schema + ".tenants(id,slug,name) VALUES(?,?,?)",
          company,
          "upgrade-test",
          "Empresa preservada");
      jdbc.update(
          "INSERT INTO " + schema + ".users(id,email,name,password_hash) VALUES(?,?,?,?)",
          user,
          "upgrade@example.test",
          "Pessoa preservada",
          "test-only-hash");
      org.flywaydb.core.Flyway.configure()
          .dataSource(dataSource)
          .schemas(schema)
          .defaultSchema(schema)
          .load()
          .migrate();
      assertThat(
              jdbc.queryForObject(
                  "SELECT name FROM " + schema + ".tenants WHERE id=?", String.class, company))
          .isEqualTo("Empresa preservada");
      assertThat(
              jdbc.queryForObject(
                  "SELECT name FROM " + schema + ".users WHERE id=?", String.class, user))
          .isEqualTo("Pessoa preservada");
      assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".products", Integer.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT max(version) FROM " + schema + ".flyway_schema_history WHERE success",
                  String.class))
          .isEqualTo("3");
    } finally {
      jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
  }

  @Test
  void frameworkLoggingRespectsTheConfiguredLevel() {
    var logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger("br.com.vendafacil.loggingProbe");
    var priorLevel = logger.getLevel();
    var priorAdditive = logger.isAdditive();
    var appender =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(ch.qos.logback.classic.Level.INFO);
    logger.setAdditive(false);
    try {
      var frameworkLog = org.apache.commons.logging.LogFactory.getLog(logger.getName());
      assertThat(frameworkLog.isDebugEnabled()).isFalse();
      frameworkLog.debug("Debug de diagnóstico não deve ser emitido.");
      frameworkLog.info("Evento de diagnóstico permitido.");
      assertThat(appender.list)
          .extracting(ch.qos.logback.classic.spi.ILoggingEvent::getLevel)
          .containsExactly(ch.qos.logback.classic.Level.INFO);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
      logger.setLevel(priorLevel);
      logger.setAdditive(priorAdditive);
    }
  }

  private UUID tenant(String slug) {
    return tenants.findBySlug(slug).orElseThrow().getId();
  }

  private UUID center() {
    return stores.findAllByTenantIdOrderByNameAsc(tenant("boa-praca")).stream()
        .filter(s -> s.getName().contains("Centro"))
        .findFirst()
        .orElseThrow()
        .getId();
  }

  private UUID garden() {
    return stores.findAllByTenantIdOrderByNameAsc(tenant("boa-praca")).stream()
        .filter(s -> s.getName().contains("Jardim"))
        .findFirst()
        .orElseThrow()
        .getId();
  }

  private MockHttpSession session(String host, String email, UUID store) throws Exception {
    var session =
        (MockHttpSession)
            mvc.perform(
                    post("/login")
                        .header("Host", host)
                        .with(csrf())
                        .param("username", email)
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andReturn()
                .getRequest()
                .getSession(false);
    session.setAttribute(AccessService.ACTIVE_STORE, store);
    return session;
  }

  private MockHttpSession admin() throws Exception {
    return session(A, "ana@boa-praca.example", center());
  }

  private UUID product(MockHttpSession session, String sku, String unit) throws Exception {
    var location =
        mvc.perform(
                post("/produtos")
                    .header("Host", A)
                    .session(session)
                    .with(csrf())
                    .param("sku", sku)
                    .param("name", "Produto " + sku)
                    .param("barcode", "")
                    .param("unit", unit)
                    .param("active", "true")
                    .param("ncm", "")
                    .param("cest", "")
                    .param("version", "0"))
            .andExpect(status().is3xxRedirection())
            .andReturn()
            .getResponse()
            .getRedirectedUrl();
    return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
  }

  private void price(MockHttpSession session, UUID id, String amount, String version)
      throws Exception {
    mvc.perform(
            post("/produtos/" + id + "/preco")
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("amount", amount)
                .param("active", "true")
                .param("version", version))
        .andExpect(redirectedUrl("/produtos/" + id));
  }

  private void move(MockHttpSession session, UUID id, UUID operation, String type, String quantity)
      throws Exception {
    mvc.perform(
            post("/estoque/" + id + "/movimentacoes")
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("operationId", operation.toString())
                .param("type", type)
                .param("quantity", quantity)
                .param("reason", "Conferência de estoque"))
        .andExpect(redirectedUrl("/estoque/" + id));
  }

  private UUID preview(MockHttpSession session, String rows) throws Exception {
    var bytes =
        (String.join(";", ImportFileReader.HEADER) + "\n" + rows).getBytes(StandardCharsets.UTF_8);
    var location =
        mvc.perform(
                multipart("/produtos/importacoes")
                    .file(new MockMultipartFile("file", "produtos.csv", "text/csv", bytes))
                    .header("Host", A)
                    .session(session)
                    .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andReturn()
            .getResponse()
            .getRedirectedUrl();
    return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
  }

  @Test
  void catalogPricesAndStockAreSharedOnlyAtTheirIntendedScopes() throws Exception {
    var first = admin();
    var second = session(A, "ana@boa-praca.example", garden());
    var id = product(first, "TEST-SCOPE", "KG");
    price(first, id, "19,90", "-1");
    price(second, id, "24,50", "-1");
    move(first, id, UUID.randomUUID(), "ENTRY", "1,250");
    mvc.perform(
            get("/api/v1/produtos/busca").header("Host", A).session(first).param("q", "TEST-SCOPE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].price").value(19.90))
        .andExpect(jsonPath("$[0].quantity").value(1.250));
    mvc.perform(
            get("/api/v1/produtos/busca")
                .header("Host", A)
                .session(second)
                .param("q", "TEST-SCOPE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].price").value(24.50))
        .andExpect(jsonPath("$[0].quantity").value(0));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM products WHERE id=?", Integer.class, id))
        .isEqualTo(1);
    for (var url :
        new String[] {
          "/produtos",
          "/produtos/" + id,
          "/produtos/" + id + "/editar",
          "/produtos/categorias",
          "/estoque",
          "/estoque/" + id,
          "/estoque/movimentacoes",
          "/produtos/importacoes",
          "/"
        }) {
      mvc.perform(get(url).header("Host", A).session(first)).andExpect(status().isOk());
    }
  }

  @Test
  void productsImagesCategoriesAndImportsRejectForeignTenantIds() throws Exception {
    var first = admin();
    var id = product(first, "PRIVATE-SKU", "UN");
    var foreignStore =
        stores.findAllByTenantIdOrderByNameAsc(tenant("vila-verde")).getFirst().getId();
    var foreign = session(B, "admin@vila-verde.example", foreignStore);
    for (var url :
        new String[] {"/produtos/" + id, "/produtos/" + id + "/imagem", "/estoque/" + id}) {
      mvc.perform(get(url).header("Host", B).session(foreign)).andExpect(status().isForbidden());
    }
    mvc.perform(
            post("/produtos/" + id + "/preco")
                .header("Host", B)
                .session(foreign)
                .with(csrf())
                .param("amount", "1,00")
                .param("active", "true")
                .param("version", "-1"))
        .andExpect(status().isForbidden());
    var category = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO categories(id,tenant_id,name) VALUES(?,?,?)",
        category,
        tenant("vila-verde"),
        "Categoria privada");
    mvc.perform(
            post("/produtos")
                .header("Host", A)
                .session(first)
                .with(csrf())
                .param("sku", "CATEGORY-IDOR")
                .param("name", "Produto")
                .param("unit", "UN")
                .param("active", "true")
                .param("version", "0")
                .param("categoryId", category.toString()))
        .andExpect(status().isForbidden());
    var job = preview(first, "1;PRIVATE-IMPORT;Produto;;;UN;;;;;\n");
    for (var url :
        new String[] {
          "/produtos/importacoes/" + job, "/produtos/importacoes/" + job + "/relatorio"
        }) {
      mvc.perform(get(url).header("Host", B).session(foreign)).andExpect(status().isForbidden());
    }
    mvc.perform(
            post("/produtos/importacoes/" + job + "/confirmar")
                .header("Host", B)
                .session(foreign)
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void operatorCannotWritePricesStockOrImportsAndDoesNotReceiveStockBalances() throws Exception {
    var first = admin();
    var id = product(first, "OPERATOR-VIEW", "UN");
    price(first, id, "9,99", "-1");
    move(first, id, UUID.randomUUID(), "ENTRY", "1234");
    var operator = session(A, "caixa@boa-praca.example", center());
    mvc.perform(
            get("/api/v1/produtos/busca")
                .header("Host", A)
                .session(operator)
                .param("q", "OPERATOR-VIEW"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].quantity").isEmpty());
    mvc.perform(get("/produtos/" + id).header("Host", A).session(operator))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("1.234"))));
    for (var url : new String[] {"/produtos/novo", "/estoque", "/produtos/importacoes"}) {
      mvc.perform(get(url).header("Host", A).session(operator)).andExpect(status().isForbidden());
    }
    mvc.perform(
            post("/produtos/" + id + "/preco")
                .header("Host", A)
                .session(operator)
                .with(csrf())
                .param("amount", "1,00")
                .param("version", "0"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/estoque/" + id + "/movimentacoes")
                .header("Host", A)
                .session(operator)
                .with(csrf())
                .param("operationId", UUID.randomUUID().toString())
                .param("type", "ENTRY")
                .param("quantity", "1")
                .param("reason", "Tentativa indevida"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/produtos").header("Host", A).session(first).param("sku", "NO-CSRF"))
        .andExpect(status().isForbidden());
  }

  @Test
  void unitsMovementsAndReplaysPreserveTheLedger() throws Exception {
    var session = admin();
    var id = product(session, "UNIT-STOCK", "UN");
    mvc.perform(
            post("/estoque/" + id + "/movimentacoes")
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("operationId", UUID.randomUUID().toString())
                .param("type", "ENTRY")
                .param("quantity", "1,250")
                .param("reason", "Recebimento de mercadoria"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("quantidades inteiras")));
    var key = UUID.randomUUID();
    move(session, id, key, "ENTRY", "2");
    move(session, id, key, "ENTRY", "2");
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM stock_balances WHERE tenant_id=? AND store_id=? AND product_id=?",
                BigDecimal.class,
                tenant("boa-praca"),
                center(),
                id))
        .isEqualByComparingTo("2");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM stock_movements WHERE product_id=?", Integer.class, id))
        .isEqualTo(1);
    mvc.perform(
            post("/estoque/" + id + "/movimentacoes")
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("operationId", key.toString())
                .param("type", "ENTRY")
                .param("quantity", "3")
                .param("reason", "Conferência de estoque"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("outros dados")));
    mvc.perform(
            post("/produtos/" + id)
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("sku", "UNIT-STOCK")
                .param("name", "Produto alterado")
                .param("unit", "KG")
                .param("active", "true")
                .param("version", "0"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("unidade não pode mudar")));
    entityManager.flush();
    assertThatThrownBy(() -> jdbc.update("DELETE FROM stock_movements WHERE product_id=?", id))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void negativeStockRequiresAnotherEligibleApproverAndDoesNotEchoThePassword() throws Exception {
    var session = admin();
    var id = product(session, "NEGATIVE-APPROVAL", "UN");
    var actor = users.findByEmail("ana@boa-praca.example").orElseThrow();
    var approver =
        users.saveAndFlush(
            new UserAccount(
                "stock-manager@example.test",
                "Gerente de estoque",
                actor.getPasswordHash(),
                false));
    var member = memberships.saveAndFlush(new Membership(tenant("boa-praca"), approver.getId()));
    var role =
        roles.findAllByTenantIdOrderByNameAsc(tenant("boa-praca")).stream()
            .filter(r -> r.getName().equals("Gerente"))
            .findFirst()
            .orElseThrow();
    grants.saveAndFlush(
        new StoreRoleGrant(tenant("boa-praca"), member.getId(), center(), role.getId()));
    var operation = UUID.randomUUID();
    mvc.perform(
            post("/estoque/" + id + "/movimentacoes")
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("operationId", operation.toString())
                .param("type", "ADJUSTMENT")
                .param("quantity", "-2")
                .param("reason", "Conferência de divergência")
                .param("approverEmail", actor.getEmail())
                .param("approverPassword", PASSWORD)
                .param("approvalReason", "Exceção gerencial justificada"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("outra pessoa")))
        .andExpect(content().string(not(containsString(PASSWORD))));
    mvc.perform(
            post("/estoque/" + id + "/movimentacoes")
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("operationId", operation.toString())
                .param("type", "ADJUSTMENT")
                .param("quantity", "-2")
                .param("reason", "Conferência de divergência")
                .param("approverEmail", approver.getEmail())
                .param("approverPassword", PASSWORD)
                .param("approvalReason", "Exceção gerencial justificada"))
        .andExpect(redirectedUrl("/estoque/" + id));
    var movement = jdbc.queryForMap("SELECT * FROM stock_movements WHERE product_id=?", id);
    assertThat(movement.get("approver_id")).isEqualTo(approver.getId());
    assertThat((BigDecimal) movement.get("balance_after")).isEqualByComparingTo("-2");
  }

  @Test
  void pricesRejectStaleVersionsAndInactiveProductsDisappearFromSaleSearch() throws Exception {
    var session = admin();
    var id = product(session, "STALE-PRICE", "UN");
    price(session, id, "10,00", "-1");
    mvc.perform(
            post("/produtos/" + id + "/preco")
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("amount", "20,00")
                .param("active", "true")
                .param("version", "-1"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("preço mudou")));
    mvc.perform(
            post("/produtos/" + id)
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("sku", "STALE-PRICE")
                .param("name", "Produto inativado")
                .param("unit", "UN")
                .param("version", "0"))
        .andExpect(redirectedUrl("/produtos/" + id));
    mvc.perform(
            get("/api/v1/produtos/busca")
                .header("Host", A)
                .session(session)
                .param("q", "STALE-PRICE"))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void imagesArePrivateAndStoredInTheDatabaseBackup() throws Exception {
    var session = admin();
    var id = product(session, "IMAGE-PRODUCT", "UN");
    var bytes = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", bytes);
    mvc.perform(
            multipart("/produtos/" + id + "/imagem")
                .file(new MockMultipartFile("image", "foto.png", "image/png", bytes.toByteArray()))
                .header("Host", A)
                .session(session)
                .with(csrf()))
        .andExpect(redirectedUrl("/produtos/" + id));
    mvc.perform(get("/produtos/" + id + "/imagem").header("Host", A).session(session))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/png"))
        .andExpect(header().string("Cache-Control", "no-store"));
    assertThat(
            jdbc.queryForObject(
                "SELECT octet_length(content) FROM product_images WHERE product_id=?",
                Integer.class,
                id))
        .isPositive();
    mvc.perform(
            post("/produtos/" + id + "/imagem/remover")
                .header("Host", A)
                .session(session)
                .with(csrf()))
        .andExpect(redirectedUrl("/produtos/" + id));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM product_images WHERE product_id=?", Integer.class, id))
        .isZero();
  }

  @Test
  void validImportHasAPreviewAtomicCommitAndIdempotentConfirmation() throws Exception {
    var session = admin();
    var job =
        preview(
            session,
            "1;IMPORT-UN;Produto UN;;Mercearia;UN;19,90;10;2;;\n1;IMPORT-KG;Produto KG;;Hortifruti;KG;8.50;1,250;0,500;;\n");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM products WHERE sku LIKE 'IMPORT-%'", Integer.class))
        .isZero();
    mvc.perform(get("/produtos/importacoes/" + job).header("Host", A).session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Confirmar importação")));
    for (int i = 0; i < 2; i++) {
      mvc.perform(
              post("/produtos/importacoes/" + job + "/confirmar")
                  .header("Host", A)
                  .session(session)
                  .with(csrf()))
          .andExpect(redirectedUrl("/produtos/importacoes/" + job));
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM products WHERE sku LIKE 'IMPORT-%'", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT sb.quantity FROM stock_balances sb JOIN products p ON p.id=sb.product_id WHERE p.sku='IMPORT-KG' AND sb.store_id=?",
                BigDecimal.class,
                center()))
        .isEqualByComparingTo("1.250");
    mvc.perform(get("/produtos/importacoes/" + job).header("Host", A).session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Importação concluída")));
    mvc.perform(
            get("/produtos/importacoes/" + job + "/relatorio").header("Host", A).session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("IMPORT-KG")));
  }

  @Test
  void invalidImportReportsEveryLineAndCannotChangeTheCatalog() throws Exception {
    var session = admin();
    var job =
        preview(
            session,
            "1;DUPLICATE;Primeiro;;;UN;2,00;1,250;;;\n1;DUPLICATE;Segundo;;;KG;-1,00;;;;\n");
    mvc.perform(get("/produtos/importacoes/" + job).header("Host", A).session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("SKU repetido")))
        .andExpect(content().string(containsString("Nenhum produto foi importado")));
    mvc.perform(
            post("/produtos/importacoes/" + job + "/confirmar")
                .header("Host", A)
                .session(session)
                .with(csrf()))
        .andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM products WHERE sku='DUPLICATE'", Integer.class))
        .isZero();
  }

  @Test
  void confirmationRevalidatesConflictsAndKeepsTheWholeBatchUnchanged() throws Exception {
    var session = admin();
    var job =
        preview(
            session,
            "1;LATE-CONFLICT;Primeiro;;;UN;2,00;;;;\n1;MUST-NOT-IMPORT;Segundo;;;UN;3,00;;;;\n");
    product(session, "LATE-CONFLICT", "UN");
    mvc.perform(
            post("/produtos/importacoes/" + job + "/confirmar")
                .header("Host", A)
                .session(session)
                .with(csrf()))
        .andExpect(redirectedUrl("/produtos/importacoes/" + job));
    assertThat(jdbc.queryForObject("SELECT status FROM import_jobs WHERE id=?", String.class, job))
        .isEqualTo("REJECTED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM products WHERE sku='MUST-NOT-IMPORT'", Integer.class))
        .isZero();
  }

  @Test
  void previewCannotBeConfirmedFromAnotherBranchOrAfterExpiration() throws Exception {
    var session = admin();
    var job = preview(session, "1;EXPIRES;Produto;;;UN;;;;;\n");
    var other = session(A, "ana@boa-praca.example", garden());
    mvc.perform(
            post("/produtos/importacoes/" + job + "/confirmar")
                .header("Host", A)
                .session(other)
                .with(csrf()))
        .andExpect(status().isForbidden());
    jdbc.update(
        "UPDATE import_jobs SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE id=?", job);
    mvc.perform(
            post("/produtos/importacoes/" + job + "/confirmar")
                .header("Host", A)
                .session(session)
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentWithdrawalsCannotConsumeTheSameLastUnit() throws Exception {
    var session = admin();
    var id = product(session, "CONCURRENT-" + UUID.randomUUID(), "UN");
    move(session, id, UUID.randomUUID(), "ENTRY", "1");
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var tasks = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
      for (int i = 0; i < 2; i++) {
        tasks.add(
            pool.submit(
                () -> {
                  start.await();
                  return mvc.perform(
                          post("/estoque/" + id + "/movimentacoes")
                              .header("Host", A)
                              .session(session)
                              .with(csrf())
                              .param("operationId", UUID.randomUUID().toString())
                              .param("type", "ADJUSTMENT")
                              .param("quantity", "-1")
                              .param("reason", "Retirada concorrente de teste"))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      start.countDown();
      var statuses =
          java.util.List.of(
              tasks.get(0).get(20, java.util.concurrent.TimeUnit.SECONDS),
              tasks.get(1).get(20, java.util.concurrent.TimeUnit.SECONDS));
      assertThat(statuses).containsExactlyInAnyOrder(302, 200);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM stock_balances WHERE tenant_id=? AND store_id=? AND product_id=?",
                BigDecimal.class,
                tenant("boa-praca"),
                center(),
                id))
        .isEqualByComparingTo("0");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM stock_movements WHERE product_id=?", Integer.class, id))
        .isEqualTo(2);
  }

  @Test
  void aConfiguredMinimumCannotBeReinterpretedAsAnotherUnit() throws Exception {
    var session = admin();
    var id = product(session, "MINIMUM-UNIT", "KG");
    mvc.perform(
            post("/estoque/" + id + "/minimo")
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("minimum", "0,500")
                .param("version", "0"))
        .andExpect(redirectedUrl("/estoque/" + id));
    mvc.perform(
            post("/produtos/" + id)
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("sku", "MINIMUM-UNIT")
                .param("name", "Produto com mínimo")
                .param("unit", "UN")
                .param("active", "true")
                .param("version", "0"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("definir um mínimo")));
    assertThat(jdbc.queryForObject("SELECT unit FROM products WHERE id=?", String.class, id))
        .isEqualTo("KG");
  }

  @Test
  void confirmationRechecksPricePermissionAfterThePreview() throws Exception {
    var session = admin();
    var job = preview(session, "1;REVOKED-PRICE;Produto;;;UN;5,00;;;;\n");
    var adminRole =
        roles.findAllByTenantIdOrderByNameAsc(tenant("boa-praca")).stream()
            .filter(role -> role.getName().equals("Administrador"))
            .findFirst()
            .orElseThrow();
    entityManager.flush();
    jdbc.update(
        "DELETE FROM role_permissions WHERE role_id=? AND permission_code='PRICE_CHANGE'",
        adminRole.getId());
    entityManager.clear();
    mvc.perform(
            post("/produtos/importacoes/" + job + "/confirmar")
                .header("Host", A)
                .session(session)
                .with(csrf()))
        .andExpect(redirectedUrl("/produtos/importacoes/" + job));
    assertThat(jdbc.queryForObject("SELECT status FROM import_jobs WHERE id=?", String.class, job))
        .isEqualTo("REJECTED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM products WHERE sku='REVOKED-PRICE'", Integer.class))
        .isZero();
    mvc.perform(
            get("/produtos/importacoes/" + job + "/relatorio").header("Host", A).session(session))
        .andExpect(content().string(containsString("Sem permissão para definir preço")));
  }

  @Test
  void negativeStockCannotUseAnApproverAuthorizedOnlyInAnotherBranch() throws Exception {
    var session = admin();
    var id = product(session, "APPROVAL-BRANCH", "UN");
    var actor = users.findByEmail("ana@boa-praca.example").orElseThrow();
    var manager =
        users.saveAndFlush(
            new UserAccount(
                "garden-manager@example.test", "Gerente Jardim", actor.getPasswordHash(), false));
    var membership = memberships.saveAndFlush(new Membership(tenant("boa-praca"), manager.getId()));
    var role =
        roles.findAllByTenantIdOrderByNameAsc(tenant("boa-praca")).stream()
            .filter(r -> r.getName().equals("Gerente"))
            .findFirst()
            .orElseThrow();
    grants.saveAndFlush(
        new StoreRoleGrant(tenant("boa-praca"), membership.getId(), garden(), role.getId()));
    mvc.perform(
            post("/estoque/" + id + "/movimentacoes")
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("operationId", UUID.randomUUID().toString())
                .param("type", "ADJUSTMENT")
                .param("quantity", "-1")
                .param("reason", "Conferência de divergência")
                .param("approverEmail", manager.getEmail())
                .param("approverPassword", PASSWORD)
                .param("approvalReason", "Exceção gerencial justificada"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("nesta filial")));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM stock_movements WHERE product_id=?", Integer.class, id))
        .isZero();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void simultaneousReplaysCreateOneSingleMovement() throws Exception {
    var session = admin();
    var id = product(session, "REPLAY-" + UUID.randomUUID(), "KG");
    var operation = UUID.randomUUID();
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(
              () -> {
                start.await();
                move(session, id, operation, "ENTRY", "1,250");
                return true;
              });
      var second =
          pool.submit(
              () -> {
                start.await();
                move(session, id, operation, "ENTRY", "1,250");
                return true;
              });
      start.countDown();
      assertThat(first.get(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      assertThat(second.get(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT quantity FROM stock_balances WHERE tenant_id=? AND store_id=? AND product_id=?",
                BigDecimal.class,
                tenant("boa-praca"),
                center(),
                id))
        .isEqualByComparingTo("1.250");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM stock_movements WHERE product_id=?", Integer.class, id))
        .isEqualTo(1);
  }
}
