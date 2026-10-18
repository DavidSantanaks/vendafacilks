package br.com.vendafacil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.Membership;
import br.com.vendafacil.identity.MembershipRepository;
import br.com.vendafacil.identity.RoleRepository;
import br.com.vendafacil.identity.StoreRoleGrant;
import br.com.vendafacil.identity.StoreRoleRepository;
import br.com.vendafacil.identity.UserRepository;
import br.com.vendafacil.store.Store;
import br.com.vendafacil.store.StoreRepository;
import br.com.vendafacil.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class IsolationIntegrationTest extends PostgresTestSupport {
  private static final String HOST_A = "boa-praca.localhost";
  private static final String HOST_B = "vila-verde.localhost";
  private static final String ADMIN = "admin.localhost";
  private static final String PASSWORD = "Test-Only-VendaFacil-2026!";
  @Autowired MockMvc mvc;
  @Autowired TenantRepository tenants;
  @Autowired StoreRepository stores;
  @Autowired UserRepository users;
  @Autowired MembershipRepository memberships;
  @Autowired StoreRoleRepository grants;
  @Autowired RoleRepository roles;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entityManager;

  private UUID tenantId(String slug) {
    return tenants.findBySlug(slug).orElseThrow().getId();
  }

  private Store storeA() {
    return stores.findAllByTenantIdOrderByNameAsc(tenantId("boa-praca")).stream()
        .filter(s -> s.getName().contains("Centro"))
        .findFirst()
        .orElseThrow();
  }

  private Store storeB() {
    return stores.findAllByTenantIdOrderByNameAsc(tenantId("vila-verde")).getFirst();
  }

  private MockHttpSession login(String host, String email) throws Exception {
    return (MockHttpSession)
        mvc.perform(
                post("/login")
                    .header("Host", host)
                    .with(csrf())
                    .param("username", email)
                    .param("password", PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"))
            .andReturn()
            .getRequest()
            .getSession(false);
  }

  private MockHttpSession adminSession() throws Exception {
    var session = login(HOST_A, "ana@boa-praca.example");
    mvc.perform(
            post("/filiais/ativa")
                .header("Host", HOST_A)
                .session(session)
                .with(csrf())
                .param("storeId", storeA().getId().toString()))
        .andExpect(redirectedUrl("/"));
    return session;
  }

  @Test
  void loginIsContextualAndAssetsAreLocal() throws Exception {
    mvc.perform(get("/login").header("Host", HOST_A))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Mercado Boa Praça")))
        .andExpect(content().string(not(containsString("Hortifruti Vila Verde"))))
        .andExpect(content().string(containsString("/vendor/bootstrap/bootstrap.min.css")));
    mvc.perform(get("/vendor/bootstrap/bootstrap.min.css").header("Host", HOST_A))
        .andExpect(status().isOk());
  }

  @Test
  void foreignTenantCredentialsCannotAuthenticate() throws Exception {
    mvc.perform(
            post("/login")
                .header("Host", HOST_A)
                .with(csrf())
                .param("username", "admin@vila-verde.example")
                .param("password", PASSWORD))
        .andExpect(redirectedUrl("/login?erro"));
  }

  @Test
  void sessionCannotBeReplayedOnAnotherHost() throws Exception {
    var session = login(HOST_A, "ana@boa-praca.example");
    mvc.perform(get("/filiais").header("Host", HOST_B).session(session))
        .andExpect(status().isForbidden());
  }

  @Test
  void onlyAuthorizedBranchesAreReturnedAndCanBeSelected() throws Exception {
    var session = login(HOST_A, "caixa@boa-praca.example");
    mvc.perform(get("/filiais").header("Host", HOST_A).session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Matriz · Centro")))
        .andExpect(content().string(not(containsString("Filial · Jardim"))))
        .andExpect(content().string(not(containsString("Taquaral"))));
    var garden =
        stores.findAllByTenantIdOrderByNameAsc(tenantId("boa-praca")).stream()
            .filter(s -> s.getName().contains("Jardim"))
            .findFirst()
            .orElseThrow();
    mvc.perform(
            post("/filiais/ativa")
                .header("Host", HOST_A)
                .session(session)
                .with(csrf())
                .param("storeId", garden.getId().toString()))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/filiais/ativa")
                .header("Host", HOST_A)
                .session(session)
                .with(csrf())
                .param("storeId", storeB().getId().toString()))
        .andExpect(status().isForbidden());
    assertThat(session.getAttribute(AccessService.ACTIVE_STORE)).isNull();
  }

  @Test
  void csrfIsRequiredAndForwardedHostIsIgnored() throws Exception {
    var session = login(HOST_A, "ana@boa-praca.example");
    mvc.perform(
            post("/filiais/ativa")
                .header("Host", HOST_A)
                .session(session)
                .param("storeId", storeA().getId().toString()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/login").header("Host", "attacker.example").header("X-Forwarded-Host", HOST_A))
        .andExpect(status().isBadRequest());
  }

  @Test
  void guessedBranchIdsAndDownloadsDoNotCrossTenants() throws Exception {
    var session = adminSession();
    mvc.perform(get("/api/v1/filiais/" + storeB().getId()).header("Host", HOST_A).session(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("correlationId").isNotEmpty())
        .andExpect(jsonPath("detail").value("Você não tem acesso a esta operação ou filial."));
    mvc.perform(get("/api/v1/filiais/exportacao").header("Host", HOST_A).session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Centro")))
        .andExpect(content().string(not(containsString("Taquaral"))));
    assertThat(stores.findByTenantIdAndId(tenantId("boa-praca"), storeB().getId())).isEmpty();
  }

  @Test
  void operatorCannotManageUsersOrDownloadAdministration() throws Exception {
    var session = login(HOST_A, "caixa@boa-praca.example");
    session.setAttribute(AccessService.ACTIVE_STORE, storeA().getId());
    mvc.perform(get("/usuarios").header("Host", HOST_A).session(session))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/filiais/exportacao").header("Host", HOST_A).session(session))
        .andExpect(status().isForbidden());
    mvc.perform(get("/").header("Host", HOST_A).session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("href=\"/usuarios\""))));
  }

  @Test
  void dashboardRequiresSelectionThenRendersEveryFoundationPage() throws Exception {
    var session = login(HOST_A, "ana@boa-praca.example");
    mvc.perform(get("/").header("Host", HOST_A).session(session))
        .andExpect(redirectedUrl("/filiais"));
    session.setAttribute(AccessService.ACTIVE_STORE, storeA().getId());
    for (var path : new String[] {"/", "/usuarios", "/configuracoes", "/configuracoes/filiais"}) {
      mvc.perform(get(path).header("Host", HOST_A).session(session))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("Mercado Boa Praça")));
    }
  }

  @Test
  void suspensionAndMembershipRevocationTakeEffectImmediately() throws Exception {
    var session = adminSession();
    jdbc.update(
        "UPDATE memberships SET active = false WHERE tenant_id = ? AND user_id = ?",
        tenantId("boa-praca"),
        users.findByEmail("ana@boa-praca.example").orElseThrow().getId());
    mvc.perform(get("/").header("Host", HOST_A).session(session)).andExpect(status().isForbidden());
    jdbc.update("UPDATE tenants SET status = 'SUSPENDED' WHERE id = ?", tenantId("vila-verde"));
    entityManager.clear();
    mvc.perform(get("/login").header("Host", HOST_B)).andExpect(status().isNotFound());
  }

  @Test
  void inactiveUserAndRevokedBranchAccessAreRevalidated() throws Exception {
    var session = adminSession();
    jdbc.update(
        "DELETE FROM membership_store_roles WHERE tenant_id = ? AND store_id = ?",
        tenantId("boa-praca"),
        storeA().getId());
    mvc.perform(get("/").header("Host", HOST_A).session(session))
        .andExpect(redirectedUrl("/filiais"));
    assertThat(session.getAttribute(AccessService.ACTIVE_STORE)).isNull();
    jdbc.update("UPDATE users SET active = false WHERE email = 'ana@boa-praca.example'");
    entityManager.clear();
    mvc.perform(get("/filiais").header("Host", HOST_A).session(session))
        .andExpect(status().isForbidden());
  }

  @Test
  void platformAdminHasNoImplicitAccessToTenantData() throws Exception {
    var session = login(ADMIN, "plataforma@vendafacil.example");
    mvc.perform(get("/admin/empresas").header("Host", ADMIN).session(session))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/filiais").header("Host", ADMIN).session(session))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/login")
                .header("Host", HOST_A)
                .with(csrf())
                .param("username", "plataforma@vendafacil.example")
                .param("password", PASSWORD))
        .andExpect(redirectedUrl("/login?erro"));
    var tenantSession = adminSession();
    mvc.perform(get("/admin/empresas").header("Host", HOST_A).session(tenantSession))
        .andExpect(status().isForbidden());
  }

  @Test
  void platformCanCreateSuspendAndReactivateAnIsolatedTenant() throws Exception {
    var session = login(ADMIN, "plataforma@vendafacil.example");
    mvc.perform(
            post("/admin/empresas")
                .header("Host", ADMIN)
                .session(session)
                .with(csrf())
                .param("name", "Nova Loja")
                .param("slug", "nova-loja")
                .param("adminName", "Nova Pessoa")
                .param("email", "nova@nova-loja.example")
                .param("password", PASSWORD))
        .andExpect(redirectedUrl("/admin/empresas"));
    var id = tenantId("nova-loja");
    assertThat(stores.findAllByTenantIdOrderByNameAsc(id)).hasSize(1);
    var tenantSession = login("nova-loja.localhost", "nova@nova-loja.example");
    mvc.perform(get("/filiais").header("Host", "nova-loja.localhost").session(tenantSession))
        .andExpect(status().isOk());
    mvc.perform(
            post("/admin/empresas/" + id + "/status")
                .header("Host", ADMIN)
                .session(session)
                .with(csrf())
                .param("active", "false"))
        .andExpect(redirectedUrl("/admin/empresas"));
    mvc.perform(get("/login").header("Host", "nova-loja.localhost"))
        .andExpect(status().isNotFound());
    mvc.perform(
            post("/admin/empresas/" + id + "/status")
                .header("Host", ADMIN)
                .session(session)
                .with(csrf())
                .param("active", "true"))
        .andExpect(redirectedUrl("/admin/empresas"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE tenant_id = ?", Integer.class, id))
        .isGreaterThanOrEqualTo(3);
  }

  @Test
  void reservedSlugAndExistingIdentityCannotBeTakenOver() throws Exception {
    var session = login(ADMIN, "plataforma@vendafacil.example");
    for (var slug : new String[] {"admin", "new-company"}) {
      mvc.perform(
              post("/admin/empresas")
                  .header("Host", ADMIN)
                  .session(session)
                  .with(csrf())
                  .param("name", "Tentativa")
                  .param("slug", slug)
                  .param("adminName", "Pessoa")
                  .param("email", "ana@boa-praca.example")
                  .param("password", PASSWORD))
          .andExpect(status().isOk())
          .andExpect(view().name("admin/tenants"));
    }
    assertThat(tenants.findBySlug("new-company")).isEmpty();
  }

  @Test
  void newUserIsRestrictedToTheActiveBranchAndForeignRoleIsRejected() throws Exception {
    var session = adminSession();
    var role =
        roles.findAllByTenantIdOrderByNameAsc(tenantId("boa-praca")).stream()
            .filter(r -> r.getName().equals("Operador de caixa"))
            .findFirst()
            .orElseThrow();
    mvc.perform(
            post("/usuarios")
                .header("Host", HOST_A)
                .session(session)
                .with(csrf())
                .param("name", "Novo Operador")
                .param("email", "novo@boa-praca.example")
                .param("password", PASSWORD)
                .param("roleId", role.getId().toString()))
        .andExpect(redirectedUrl("/usuarios"));
    var newUserSession = login(HOST_A, "novo@boa-praca.example");
    mvc.perform(get("/api/v1/filiais").header("Host", HOST_A).session(newUserSession))
        .andExpect(jsonPath("$.length()").value(1));
    var foreignRole = roles.findAllByTenantIdOrderByNameAsc(tenantId("vila-verde")).getFirst();
    mvc.perform(
            post("/usuarios")
                .header("Host", HOST_A)
                .session(session)
                .with(csrf())
                .param("name", "Outra Pessoa")
                .param("email", "outra@boa-praca.example")
                .param("password", PASSWORD)
                .param("roleId", foreignRole.getId().toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  void databasePreventsCrossTenantRoleGrants() {
    var tenantA = tenantId("boa-praca");
    var membership =
        memberships
            .findByTenantIdAndUserIdAndActiveTrue(
                tenantA, users.findByEmail("ana@boa-praca.example").orElseThrow().getId())
            .orElseThrow();
    var role = roles.findAllByTenantIdOrderByNameAsc(tenantA).getFirst();
    assertThatThrownBy(
            () ->
                grants.saveAndFlush(
                    new StoreRoleGrant(
                        tenantA, membership.getId(), storeB().getId(), role.getId())))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void auditHistoryCannotBeChanged() throws Exception {
    adminSession();
    entityManager.flush();
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM audit_logs WHERE tenant_id = ?", tenantId("boa-praca")))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void branchCanBeCreatedDeactivatedAndReactivatedWithoutLosingHistory() throws Exception {
    var session = adminSession();
    mvc.perform(
            post("/configuracoes/filiais")
                .header("Host", HOST_A)
                .session(session)
                .with(csrf())
                .param("name", "Filial · Cambuí")
                .param("city", "Campinas")
                .param("state", "SP"))
        .andExpect(redirectedUrl("/configuracoes/filiais"));
    var created =
        stores.findAllByTenantIdOrderByNameAsc(tenantId("boa-praca")).stream()
            .filter(s -> s.getName().contains("Cambuí"))
            .findFirst()
            .orElseThrow();
    for (var active : new String[] {"false", "true"}) {
      mvc.perform(
              post("/configuracoes/filiais/" + created.getId())
                  .header("Host", HOST_A)
                  .session(session)
                  .with(csrf())
                  .param("name", "Filial · Cambuí")
                  .param("city", "Campinas")
                  .param("state", "SP")
                  .param("active", active))
          .andExpect(redirectedUrl("/filiais"));
      mvc.perform(get("/configuracoes/filiais").header("Host", HOST_A).session(session))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("Cambuí")));
    }
    assertThat(stores.findByTenantIdAndId(tenantId("boa-praca"), created.getId())).isPresent();
  }

  @Test
  void sameIdentityHasIndependentPermissionsInDifferentTenants() throws Exception {
    var user = users.findByEmail("ana@boa-praca.example").orElseThrow();
    var membership = memberships.saveAndFlush(new Membership(tenantId("vila-verde"), user.getId()));
    var stockRole =
        roles.findAllByTenantIdOrderByNameAsc(tenantId("vila-verde")).stream()
            .filter(r -> r.getName().equals("Estoquista"))
            .findFirst()
            .orElseThrow();
    grants.saveAndFlush(
        new StoreRoleGrant(
            tenantId("vila-verde"), membership.getId(), storeB().getId(), stockRole.getId()));
    var session = login(HOST_B, "ana@boa-praca.example");
    session.setAttribute(AccessService.ACTIVE_STORE, storeB().getId());
    mvc.perform(get("/").header("Host", HOST_B).session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Estoquista")));
    mvc.perform(get("/usuarios").header("Host", HOST_B).session(session))
        .andExpect(status().isForbidden());
  }

  @Test
  void expiredApiSessionAndMissingCsrfReturnProblemDetails() throws Exception {
    mvc.perform(get("/api/v1/filiais").header("Host", HOST_A))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("correlationId").isNotEmpty())
        .andExpect(jsonPath("status").value(401));
    mvc.perform(post("/api/v1/filiais").header("Host", HOST_A))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("status").value(403));
  }

  @Test
  void foreignBranchCannotBeEditedAndWeakPasswordsAreRejected() throws Exception {
    var session = adminSession();
    mvc.perform(
            post("/configuracoes/filiais/" + storeB().getId())
                .header("Host", HOST_A)
                .session(session)
                .with(csrf())
                .param("name", "Ataque")
                .param("city", "Campinas")
                .param("state", "SP")
                .param("active", "true"))
        .andExpect(status().isForbidden());
    var role = roles.findAllByTenantIdOrderByNameAsc(tenantId("boa-praca")).getFirst();
    mvc.perform(
            post("/usuarios")
                .header("Host", HOST_A)
                .session(session)
                .with(csrf())
                .param("name", "Pessoa")
                .param("email", "weak@example.test")
                .param("password", "short")
                .param("roleId", role.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Use de 12 a 64 caracteres")));
    assertThat(users.findByEmail("weak@example.test")).isEmpty();
  }
}
