package br.com.vendafacil;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.vendafacil.identity.AccessService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommerceIntegrationTest extends PostgresTestSupport {
  private static final String HOST = "boa-praca.localhost";
  private static final String PASSWORD = "Test-Only-VendaFacil-2026!";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private UUID tenant() {
    return jdbc.queryForObject("SELECT id FROM tenants WHERE slug='boa-praca'", UUID.class);
  }

  private UUID store() {
    return jdbc.queryForObject(
        "SELECT id FROM stores WHERE tenant_id=? AND name LIKE '%Centro%'", UUID.class, tenant());
  }

  private UUID user(String email) {
    return jdbc.queryForObject("SELECT id FROM users WHERE email=?", UUID.class, email);
  }

  private MockHttpSession login(String email) throws Exception {
    var session =
        (MockHttpSession)
            mvc.perform(
                    post("/login")
                        .header("Host", HOST)
                        .with(csrf())
                        .param("username", email)
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andReturn()
                .getRequest()
                .getSession(false);
    session.setAttribute(AccessService.ACTIVE_STORE, store());
    return session;
  }

  private String manager() {
    var email = "manager-" + UUID.randomUUID() + "@example.test";
    var id = UUID.randomUUID();
    var member = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users(id,email,name,password_hash) SELECT ?,?,'Gerente teste',password_hash FROM users WHERE email='ana@boa-praca.example'",
        id,
        email);
    jdbc.update(
        "INSERT INTO memberships(id,tenant_id,user_id) VALUES(?,?,?)", member, tenant(), id);
    jdbc.update(
        "INSERT INTO membership_store_roles(id,tenant_id,membership_id,store_id,role_id) SELECT ?,?,?,?,id FROM roles WHERE tenant_id=? AND name='Gerente'",
        UUID.randomUUID(),
        tenant(),
        member,
        store(),
        tenant());
    return email;
  }

  private ResultActions send(MockHttpSession session, String path, Object body) throws Exception {
    return mvc.perform(
        post(path)
            .header("Host", HOST)
            .session(session)
            .with(csrf())
            .contentType("application/json")
            .accept("application/json")
            .content(JSON.writeValueAsString(body)));
  }

  private UUID resultId(ResultActions result) throws Exception {
    return UUID.fromString(
        JSON.readTree(
                result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
            .get("id")
            .asString());
  }

  private UUID register(MockHttpSession admin) throws Exception {
    return resultId(
        send(admin, "/api/caixa/registros", Map.of("name", "Caixa " + UUID.randomUUID())));
  }

  private UUID open(MockHttpSession operator, UUID register, String amount) throws Exception {
    return resultId(
        send(
            operator,
            "/api/caixa/sessoes",
            Map.of("operationId", UUID.randomUUID(), "registerId", register, "initial", amount)));
  }

  private UUID product(String unit, String price, String stock) {
    var id = UUID.randomUUID();
    var sku = "TEST-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    jdbc.update(
        "INSERT INTO products(id,tenant_id,sku,name,unit,ncm) VALUES(?,?,?,'Produto original',?,'12345678')",
        id,
        tenant(),
        sku,
        unit);
    jdbc.update(
        "INSERT INTO store_prices(tenant_id,store_id,product_id,amount) VALUES(?,?,?,?)",
        tenant(),
        store(),
        id,
        new BigDecimal(price));
    jdbc.update(
        "INSERT INTO stock_balances(tenant_id,store_id,product_id,quantity) VALUES(?,?,?,?)",
        tenant(),
        store(),
        id,
        new BigDecimal(stock));
    return id;
  }

  private Map<String, Object> item(UUID id, String price, String quantity) {
    var item = new LinkedHashMap<String, Object>();
    item.put("productId", id);
    item.put("unitPrice", price);
    item.put("quantity", quantity);
    item.put("productVersion", 0);
    return item;
  }

  private Map<String, Object> sale(
      UUID session, UUID product, String price, String quantity, String method, String payment) {
    var body = new LinkedHashMap<String, Object>();
    body.put("operationId", UUID.randomUUID());
    body.put("sessionId", session);
    body.put("customer", "Cliente teste");
    body.put("items", List.of(item(product, price, quantity)));
    body.put("payments", List.of(Map.of("method", method, "amount", payment)));
    return body;
  }

  private Map<String, String> approval(String email) {
    return Map.of(
        "email", email, "password", PASSWORD, "reason", "Exceção conferida presencialmente");
  }

  private BigDecimal quantity(UUID product) {
    return jdbc.queryForObject(
        "SELECT quantity FROM stock_balances WHERE tenant_id=? AND store_id=? AND product_id=?",
        BigDecimal.class,
        tenant(),
        store(),
        product);
  }

  @Test
  void fullCycleSplitsPaymentsRoundsKgSnapshotsAndReplaysExactlyOnce() throws Exception {
    var admin = login("ana@boa-praca.example");
    var operator = login("caixa@boa-praca.example");
    var session = open(operator, register(admin), "50");
    var kg = product("KG", "19.90", "5");
    var unit = product("UN", "10", "5");
    var body = sale(session, kg, "19.90", "1.255", "CASH", "20");
    body.put("items", List.of(item(kg, "19.90", "1.255"), item(unit, "10", "1")));
    body.put(
        "payments",
        List.of(Map.of("method", "CASH", "amount", "20"), Map.of("method", "PIX", "amount", "20")));
    send(operator, "/api/pdv/previsao", body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(
            jsonPath("$.items[?(@.productId == '" + kg + "')].total")
                .value(org.hamcrest.Matchers.contains(24.97)))
        .andExpect(
            jsonPath("$.items[?(@.productId == '" + unit + "')].total")
                .value(org.hamcrest.Matchers.contains(10.0)));
    var id = resultId(send(operator, "/api/pdv/vendas", body));
    assertThat(resultId(send(operator, "/api/pdv/vendas", body))).isEqualTo(id);
    assertThat(quantity(kg)).isEqualByComparingTo("3.745");
    assertThat(quantity(unit)).isEqualByComparingTo("4");
    assertThat(jdbc.queryForObject("SELECT total FROM sales WHERE id=?", BigDecimal.class, id))
        .isEqualByComparingTo("34.97");
    assertThat(jdbc.queryForObject("SELECT change FROM sales WHERE id=?", BigDecimal.class, id))
        .isEqualByComparingTo("5.03");
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(amount) FROM cash_events WHERE session_id=?",
                BigDecimal.class,
                session))
        .isEqualByComparingTo("64.97");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM stock_movements WHERE sale_id=?", Integer.class, id))
        .isEqualTo(2);
    jdbc.update("UPDATE products SET name='Nome posterior' WHERE id=?", kg);
    mvc.perform(get("/vendas/" + id + "/recibo").header("Host", HOST).session(operator))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("RECIBO NÃO FISCAL")))
        .andExpect(content().string(containsString("Produto original")))
        .andExpect(content().string(not(containsString("Nome posterior"))));
    body.put("customer", "Mudança incompatível");
    send(operator, "/api/pdv/vendas", body).andExpect(status().isBadRequest());
  }

  @Test
  void blindCloseDoesNotExposeBalanceUntilImmutableDeclarationAndBlocksFurtherOperations()
      throws Exception {
    var admin = login("ana@boa-praca.example");
    var operator = login("caixa@boa-praca.example");
    var session = open(operator, register(admin), "50");
    var p = product("UN", "10", "2");
    mvc.perform(get("/api/caixa/" + session).header("Host", HOST).session(operator))
        .andExpect(jsonPath("$.expected").isEmpty())
        .andExpect(jsonPath("$.count").isEmpty())
        .andExpect(jsonPath("$.events").isEmpty());
    mvc.perform(get("/caixa/" + session).header("Host", HOST).session(operator))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("Dinheiro esperado"))));
    send(operator, "/api/caixa/" + session + "/contagem", Map.of("counted", "50"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/caixa/" + session).header("Host", HOST).session(operator))
        .andExpect(jsonPath("$.expected").value(50));
    send(operator, "/api/caixa/" + session + "/contagem", Map.of("counted", "49"))
        .andExpect(status().isBadRequest());
    send(operator, "/api/pdv/vendas", sale(session, p, "10", "1", "CASH", "10"))
        .andExpect(status().isBadRequest());
    var close =
        Map.of("operationId", UUID.randomUUID(), "reason", "Contagem conferida sem diferença");
    send(operator, "/api/caixa/" + session + "/fechamento", close).andExpect(status().isOk());
    send(operator, "/api/caixa/" + session + "/fechamento", close).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM cash_sessions WHERE id=?", String.class, session))
        .isEqualTo("CLOSED");
    send(operator, "/api/pdv/vendas", sale(session, p, "10", "1", "CASH", "10"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void operatorDiscountRequiresAnotherAuthorizedPersonWithoutChangingTheSession() throws Exception {
    var admin = login("ana@boa-praca.example");
    var operator = login("caixa@boa-praca.example");
    var session = open(operator, register(admin), "0");
    var p = product("UN", "100", "3");
    var body = sale(session, p, "100", "1", "CASH", "100");
    body.put("discount", Map.of("type", "PERCENT", "value", "5"));
    send(operator, "/api/pdv/vendas", body).andExpect(status().isBadRequest());
    assertThat(quantity(p)).isEqualByComparingTo("3");
    body.put("discountApproval", approval(manager()));
    var id = resultId(send(operator, "/api/pdv/vendas", body));
    assertThat(jdbc.queryForObject("SELECT actor_id FROM sales WHERE id=?", UUID.class, id))
        .isEqualTo(user("caixa@boa-praca.example"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sale_approvals WHERE sale_id=?", Integer.class, id))
        .isEqualTo(1);
  }

  @Test
  void cumulativeDiscountCannotBypassManagerOrCompanyLimits() throws Exception {
    var admin = login("ana@boa-praca.example");
    var managerEmail = manager();
    var manager = login(managerEmail);
    var session = open(manager, register(admin), "0");
    var p = product("UN", "100", "3");
    var body = sale(session, p, "100", "1", "CASH", "100");
    var line = item(p, "100", "1");
    line.put("discount", Map.of("type", "PERCENT", "value", "10"));
    body.put("items", List.of(line));
    body.put("discount", Map.of("type", "PERCENT", "value", "10"));
    send(manager, "/api/pdv/vendas", body).andExpect(status().isBadRequest());
    body.put("discountApproval", approval("ana@boa-praca.example"));
    var id = resultId(send(manager, "/api/pdv/vendas", body));
    assertThat(jdbc.queryForObject("SELECT total FROM sales WHERE id=?", BigDecimal.class, id))
        .isEqualByComparingTo("81");
    body.put("operationId", UUID.randomUUID());
    body.put("discount", Map.of("type", "PERCENT", "value", "20"));
    send(manager, "/api/pdv/vendas", body).andExpect(status().isBadRequest());
    body.put("discount", Map.of("type", "PERCENT", "value", "100"));
    send(manager, "/api/pdv/vendas", body).andExpect(status().isBadRequest());
  }

  @Test
  void approverMustHaveOwnLimitAndCannotApproveTheirOwnOperation() throws Exception {
    var admin = login("ana@boa-praca.example");
    var operator = login("caixa@boa-praca.example");
    var session = open(operator, register(admin), "0");
    var p = product("UN", "100", "3");
    var body = sale(session, p, "100", "1", "PIX", "85");
    body.put("discount", Map.of("type", "PERCENT", "value", "15"));
    body.put("discountApproval", approval(manager()));
    send(operator, "/api/pdv/vendas", body).andExpect(status().isBadRequest());
    body.put("discountApproval", approval("caixa@boa-praca.example"));
    send(operator, "/api/pdv/vendas", body).andExpect(status().isBadRequest());
    body.put("discountApproval", approval("admin@vila-verde.example"));
    send(operator, "/api/pdv/vendas", body).andExpect(status().isBadRequest());
    assertThat(quantity(p)).isEqualByComparingTo("3");
  }

  @Test
  void negativeStockRequiresContextualApprovalAndIsAtomic() throws Exception {
    var admin = login("ana@boa-praca.example");
    var operator = login("caixa@boa-praca.example");
    var session = open(operator, register(admin), "0");
    var p = product("UN", "10", "0");
    var body = sale(session, p, "10", "1", "PIX", "10");
    send(operator, "/api/pdv/vendas", body).andExpect(status().isBadRequest());
    assertThat(quantity(p)).isZero();
    body.put("stockApproval", approval("ana@boa-praca.example"));
    var id = resultId(send(operator, "/api/pdv/vendas", body));
    assertThat(quantity(p)).isEqualByComparingTo("-1");
    assertThat(
            jdbc.queryForObject(
                "SELECT approver_id FROM stock_movements WHERE sale_id=?", UUID.class, id))
        .isEqualTo(user("ana@boa-praca.example"));
  }

  @Test
  void cashWithdrawalUsesApprovalAndOnlyPhysicalMoney() throws Exception {
    var admin = login("ana@boa-praca.example");
    var session = open(admin, register(admin), "50");
    var body =
        new LinkedHashMap<String, Object>(
            Map.of(
                "operationId",
                UUID.randomUUID(),
                "type",
                "WITHDRAW",
                "amount",
                "10",
                "reason",
                "Retirada conferida"));
    send(admin, "/api/caixa/" + session + "/movimentos", body).andExpect(status().isBadRequest());
    body.put("approval", approval("ana@boa-praca.example"));
    send(admin, "/api/caixa/" + session + "/movimentos", body).andExpect(status().isBadRequest());
    body.put("approval", approval(manager()));
    send(admin, "/api/caixa/" + session + "/movimentos", body).andExpect(status().isOk());
    send(admin, "/api/caixa/" + session + "/movimentos", body).andExpect(status().isOk());
    send(
            admin,
            "/api/caixa/" + session + "/movimentos",
            Map.of(
                "operationId",
                UUID.randomUUID(),
                "type",
                "SUPPLY",
                "amount",
                "3",
                "reason",
                "Suprimento conferido"))
        .andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(amount) FROM cash_events WHERE session_id=?",
                BigDecimal.class,
                session))
        .isEqualByComparingTo("43");
    body.put("operationId", UUID.randomUUID());
    body.put("amount", "44");
    send(admin, "/api/caixa/" + session + "/movimentos", body).andExpect(status().isBadRequest());
  }

  @Test
  void differenceAboveDefaultZeroRequiresApprovalAndClosedSessionsRejectMovement()
      throws Exception {
    var admin = login("ana@boa-praca.example");
    var session = open(admin, register(admin), "50");
    send(admin, "/api/caixa/" + session + "/contagem", Map.of("counted", "49.99"))
        .andExpect(status().isOk());
    var close =
        new LinkedHashMap<String, Object>(
            Map.of("operationId", UUID.randomUUID(), "reason", "Faltou um centavo na gaveta"));
    send(admin, "/api/caixa/" + session + "/fechamento", close).andExpect(status().isBadRequest());
    close.put("approval", approval(manager()));
    send(admin, "/api/caixa/" + session + "/fechamento", close).andExpect(status().isOk());
    send(
            admin,
            "/api/caixa/" + session + "/movimentos",
            Map.of(
                "operationId",
                UUID.randomUUID(),
                "type",
                "SUPPLY",
                "amount",
                "1",
                "reason",
                "Movimento depois de fechar"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void changedPriceInactiveProductAndFractionalUnitsDoNotWritePartialSales() throws Exception {
    var admin = login("ana@boa-praca.example");
    var session = open(admin, register(admin), "0");
    var p = product("UN", "10", "5");
    send(admin, "/api/pdv/vendas", sale(session, p, "9", "1", "PIX", "10"))
        .andExpect(status().isBadRequest());
    send(admin, "/api/pdv/vendas", sale(session, p, "10", "0.5", "PIX", "10"))
        .andExpect(status().isBadRequest());
    jdbc.update("UPDATE products SET active=false WHERE id=?", p);
    send(admin, "/api/pdv/vendas", sale(session, p, "10", "1", "PIX", "10"))
        .andExpect(status().isBadRequest());
    assertThat(quantity(p)).isEqualByComparingTo("5");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales WHERE session_id=?", Integer.class, session))
        .isZero();
  }

  @Test
  void digitalOverpaymentsAndUnderpaymentsCannotCreateChangeOrSales() throws Exception {
    var admin = login("ana@boa-praca.example");
    var session = open(admin, register(admin), "0");
    var p = product("UN", "10", "5");
    send(admin, "/api/pdv/vendas", sale(session, p, "10", "1", "PIX", "11"))
        .andExpect(status().isBadRequest());
    send(admin, "/api/pdv/vendas", sale(session, p, "10", "1", "CASH", "9"))
        .andExpect(status().isBadRequest());
    var body = sale(session, p, "10", "1", "PIX", "0");
    body.put("payments", List.of());
    send(admin, "/api/pdv/previsao", body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totals.total").value(10));
    send(admin, "/api/pdv/vendas", body).andExpect(status().isBadRequest());
    assertThat(quantity(p)).isEqualByComparingTo("5");
  }

  @Test
  void sessionsReceiptsReportsAndRegistersAreScopedToBranchAndActor() throws Exception {
    var admin = login("ana@boa-praca.example");
    var operator = login("caixa@boa-praca.example");
    var register = register(admin);
    var session = open(admin, register, "0");
    var p = product("UN", "10", "5");
    send(operator, "/api/pdv/vendas", sale(session, p, "10", "1", "PIX", "10"))
        .andExpect(status().isForbidden());
    send(
            operator,
            "/api/caixa/sessoes",
            Map.of("operationId", UUID.randomUUID(), "registerId", register, "initial", "0"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/relatorios/vendas").header("Host", HOST).session(operator))
        .andExpect(status().isForbidden());
    var id = resultId(send(admin, "/api/pdv/vendas", sale(session, p, "10", "1", "PIX", "10")));
    mvc.perform(get("/vendas/" + id + "/recibo").header("Host", HOST).session(operator))
        .andExpect(status().isForbidden());
    var garden =
        jdbc.queryForObject(
            "SELECT id FROM stores WHERE tenant_id=? AND name LIKE '%Jardim%'",
            UUID.class, tenant());
    admin.setAttribute(AccessService.ACTIVE_STORE, garden);
    mvc.perform(get("/vendas/" + id + "/recibo").header("Host", HOST).session(admin))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/caixa/" + session).header("Host", HOST).session(admin))
        .andExpect(status().isForbidden());
    send(
            admin,
            "/api/caixa/sessoes",
            Map.of("operationId", UUID.randomUUID(), "registerId", register, "initial", "0"))
        .andExpect(status().isForbidden());
  }

  @Test
  void reportsReconcileNetCashAndCsvNeutralizesSpreadsheetFormulas() throws Exception {
    var admin = login("ana@boa-praca.example");
    var session = open(admin, register(admin), "0");
    var p = product("UN", "10", "5");
    var body = sale(session, p, "10", "1", "CASH", "20");
    body.put("customer", "=UNTRUSTED()");
    resultId(send(admin, "/api/pdv/vendas", body));
    mvc.perform(get("/relatorios/vendas").header("Host", HOST).session(admin))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("R$ 10,00")));
    var today = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).toString();
    mvc.perform(
            get("/relatorios/vendas.csv")
                .param("from", today)
                .param("to", today)
                .header("Host", HOST)
                .session(admin))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("'=UNTRUSTED()")));
  }

  @Test
  void cashAndSalesRequireCsrfAndAnonymousRequestsAreRejected() throws Exception {
    var admin = login("ana@boa-praca.example");
    mvc.perform(
            post("/api/caixa/registros")
                .header("Host", HOST)
                .session(admin)
                .contentType("application/json")
                .content("{\"name\":\"forbidden\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/pdv/produtos").header("Host", HOST)).andExpect(status().isUnauthorized());
  }

  @Test
  void completedSalesAreImmutableInPostgres() throws Exception {
    var admin = login("ana@boa-praca.example");
    var session = open(admin, register(admin), "0");
    var p = product("UN", "10", "5");
    var id = resultId(send(admin, "/api/pdv/vendas", sale(session, p, "10", "1", "PIX", "10")));
    assertThatThrownBy(() -> jdbc.update("DELETE FROM sales WHERE id=?", id))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void policyIsRestrictedAndWithdrawalLimitIsEnforcedWithoutApprovalBypass() throws Exception {
    var admin = login("ana@boa-praca.example");
    var operator = login("caixa@boa-praca.example");
    var policy =
        Map.of(
            "blind",
            false,
            "withdrawalApproval",
            false,
            "differenceLimit",
            "1.00",
            "withdrawalLimit",
            "5.00",
            "maximumDiscount",
            "15");
    send(operator, "/api/caixa/politicas", policy).andExpect(status().isForbidden());
    send(admin, "/api/caixa/politicas", policy).andExpect(status().isOk());
    var session = open(admin, register(admin), "20");
    mvc.perform(get("/api/caixa/" + session).header("Host", HOST).session(admin))
        .andExpect(jsonPath("$.expected").value(20));
    send(
            admin,
            "/api/caixa/" + session + "/movimentos",
            Map.of(
                "operationId",
                UUID.randomUUID(),
                "type",
                "WITHDRAW",
                "amount",
                "5.01",
                "reason",
                "Acima do limite configurado"))
        .andExpect(status().isBadRequest());
    send(
            admin,
            "/api/caixa/" + session + "/movimentos",
            Map.of(
                "operationId",
                UUID.randomUUID(),
                "type",
                "WITHDRAW",
                "amount",
                "5.00",
                "reason",
                "Dentro do limite configurado"))
        .andExpect(status().isOk());
    send(admin, "/api/caixa/" + session + "/contagem", Map.of("counted", "14.50"))
        .andExpect(status().isOk());
    send(
            admin,
            "/api/caixa/" + session + "/fechamento",
            Map.of("operationId", UUID.randomUUID(), "reason", "Diferença dentro da tolerância"))
        .andExpect(status().isOk());
  }

  @Test
  void countAndCashLedgerCannotBeRewritten() throws Exception {
    var admin = login("ana@boa-praca.example");
    var session = open(admin, register(admin), "0");
    send(admin, "/api/caixa/" + session + "/contagem", Map.of("counted", "0"))
        .andExpect(status().isOk());
    assertThatThrownBy(
            () -> jdbc.update("UPDATE cash_counts SET counted=1 WHERE session_id=?", session))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void cashEventsCannotBeDeleted() throws Exception {
    var admin = login("ana@boa-praca.example");
    var session = open(admin, register(admin), "0");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM cash_events WHERE session_id=?", session))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void foreignTenantCannotReadCashOrSellProductsAndSessionCannotBeReusedOnAnotherHost()
      throws Exception {
    var admin = login("ana@boa-praca.example");
    var session = open(admin, register(admin), "0");
    var product = product("UN", "10", "5");
    var other =
        (MockHttpSession)
            mvc.perform(
                    post("/login")
                        .header("Host", "vila-verde.localhost")
                        .with(csrf())
                        .param("username", "admin@vila-verde.example")
                        .param("password", PASSWORD))
                .andExpect(redirectedUrl("/"))
                .andReturn()
                .getRequest()
                .getSession(false);
    other.setAttribute(
        AccessService.ACTIVE_STORE,
        jdbc.queryForObject(
            "SELECT s.id FROM stores s JOIN tenants t ON t.id=s.tenant_id WHERE t.slug='vila-verde'",
            UUID.class));
    mvc.perform(get("/api/caixa/" + session).header("Host", "vila-verde.localhost").session(other))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/pdv/vendas")
                .header("Host", "vila-verde.localhost")
                .session(other)
                .with(csrf())
                .contentType("application/json")
                .content(JSON.writeValueAsString(sale(session, product, "10", "1", "PIX", "10"))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/caixa/" + session).header("Host", "vila-verde.localhost").session(admin))
        .andExpect(status().isForbidden());
    assertThat(quantity(product)).isEqualByComparingTo("5");
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentRetriesReturnTheSameSaleWithoutDuplicatingStockOrCash() throws Exception {
    var email = manager();
    var admin = login("ana@boa-praca.example");
    var operator = login(email);
    var session = open(operator, register(admin), "0");
    var product = product("UN", "10", "1");
    var body = sale(session, product, "10", "1", "CASH", "10");
    var latch = new java.util.concurrent.CountDownLatch(1);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var first =
          executor.submit(
              () -> {
                latch.await();
                return resultId(send(operator, "/api/pdv/vendas", body));
              });
      var second =
          executor.submit(
              () -> {
                latch.await();
                return resultId(send(operator, "/api/pdv/vendas", body));
              });
      latch.countDown();
      var id = first.get();
      assertThat(second.get()).isEqualTo(id);
      assertThat(quantity(product)).isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM stock_movements WHERE sale_id=?", Integer.class, id))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT sum(amount) FROM cash_events WHERE session_id=?",
                  BigDecimal.class,
                  session))
          .isEqualByComparingTo("10");
    } finally {
      executor.shutdownNow();
      send(operator, "/api/caixa/" + session + "/contagem", Map.of("counted", "10"))
          .andExpect(status().isOk());
      send(
              operator,
              "/api/caixa/" + session + "/fechamento",
              Map.of(
                  "operationId", UUID.randomUUID(), "reason", "Encerramento do teste de repetição"))
          .andExpect(status().isOk());
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentCheckoutsCannotSellTheLastUnitTwice() throws Exception {
    var admin = login("ana@boa-praca.example");
    var operator = login("caixa@boa-praca.example");
    var s1 = open(admin, register(admin), "0");
    var s2 = open(operator, register(admin), "0");
    var p = product("UN", "10", "1");
    var latch = new java.util.concurrent.CountDownLatch(1);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var a =
          executor.submit(
              () -> {
                latch.await();
                return send(admin, "/api/pdv/vendas", sale(s1, p, "10", "1", "PIX", "10"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      var b =
          executor.submit(
              () -> {
                latch.await();
                return send(operator, "/api/pdv/vendas", sale(s2, p, "10", "1", "PIX", "10"))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              });
      latch.countDown();
      assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 400);
      assertThat(quantity(p)).isZero();
    } finally {
      executor.shutdownNow();
      for (var entry : List.of(Map.entry(admin, s1), Map.entry(operator, s2))) {
        send(entry.getKey(), "/api/caixa/" + entry.getValue() + "/contagem", Map.of("counted", "0"))
            .andExpect(status().isOk());
        send(
                entry.getKey(),
                "/api/caixa/" + entry.getValue() + "/fechamento",
                Map.of(
                    "operationId",
                    UUID.randomUUID(),
                    "reason",
                    "Encerramento do teste concorrente"))
            .andExpect(status().isOk());
      }
    }
  }
}
