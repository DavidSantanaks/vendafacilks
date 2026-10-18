package br.com.vendafacil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.vendafacil.identity.AccessService;
import br.com.vendafacil.identity.MembershipRepository;
import br.com.vendafacil.identity.RoleRepository;
import br.com.vendafacil.identity.StoreRoleRepository;
import br.com.vendafacil.identity.UserRepository;
import br.com.vendafacil.identity.recovery.PasswordRecoveryService;
import br.com.vendafacil.identity.recovery.RecoveryDispatcher;
import br.com.vendafacil.identity.recovery.RecoveryMail;
import br.com.vendafacil.identity.recovery.RecoveryScope;
import br.com.vendafacil.store.StoreRepository;
import br.com.vendafacil.tenant.TenantRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
@Transactional
class RecoveryAndAdministrationIntegrationTest extends PostgresTestSupport {
  private static final String A = "boa-praca.localhost";
  private static final String B = "vila-verde.localhost";
  private static final String ADMIN = "admin.localhost";
  private static final String PASSWORD = "Test-Only-VendaFacil-2026!";
  private static final String NEW_PASSWORD = "Minha-Nova-Senha-2026!";
  @Autowired MockMvc mvc;
  @Autowired PasswordRecoveryService recovery;
  @Autowired ApplicationEvents events;
  @Autowired UserRepository users;
  @Autowired TenantRepository tenants;
  @Autowired MembershipRepository memberships;
  @Autowired StoreRoleRepository grants;
  @Autowired RoleRepository roles;
  @Autowired StoreRepository stores;
  @Autowired PasswordEncoder encoder;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entityManager;
  @MockitoBean RecoveryDispatcher dispatcher;

  private UUID tenantId(String slug) {
    return tenants.findBySlug(slug).orElseThrow().getId();
  }

  private RecoveryScope scope(String slug) {
    var tenant = tenants.findBySlug(slug).orElseThrow();
    return new RecoveryScope(tenant.getId(), false, slug, tenant.getName());
  }

  private RecoveryScope platformScope() {
    return new RecoveryScope(null, true, "admin", "Administração da plataforma");
  }

  private String token(String email, RecoveryScope scope) {
    events.clear();
    recovery.request(email, scope);
    var link =
        events.stream(RecoveryMail.class)
            .filter(mail -> !mail.completed())
            .findFirst()
            .orElseThrow()
            .link();
    return link.substring(link.indexOf("token=") + 6);
  }

  private MockHttpSession login(String host, String email) throws Exception {
    return (MockHttpSession)
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
  }

  private MockHttpSession tenantAdmin() throws Exception {
    var session = login(A, "ana@boa-praca.example");
    session.setAttribute(AccessService.ACTIVE_STORE, centerId());
    return session;
  }

  private UUID centerId() {
    return stores.findAllByTenantIdOrderByNameAsc(tenantId("boa-praca")).stream()
        .filter(s -> s.getName().contains("Centro"))
        .findFirst()
        .orElseThrow()
        .getId();
  }

  private UUID operatorGrant() {
    var member =
        memberships
            .findByTenantIdAndUserIdAndActiveTrue(
                tenantId("boa-praca"),
                users.findByEmail("caixa@boa-praca.example").orElseThrow().getId())
            .orElseThrow();
    return grants
        .findAllByTenantIdAndMembershipIdAndStoreId(
            tenantId("boa-praca"), member.getId(), centerId())
        .getFirst()
        .getId();
  }

  @Test
  void publicRecoveryExistsOnBothPortalsAndDoesNotEnumerateAccounts() throws Exception {
    for (var host : new String[] {A, ADMIN}) {
      mvc.perform(get("/login").header("Host", host))
          .andExpect(content().string(containsString("Esqueci minha senha")));
      mvc.perform(get("/recuperar-senha").header("Host", host)).andExpect(status().isOk());
    }
    for (var email : new String[] {"ana@boa-praca.example", "desconhecido@example.test"}) {
      mvc.perform(post("/recuperar-senha").header("Host", A).with(csrf()).param("email", email))
          .andExpect(redirectedUrl("/recuperar-senha"))
          .andExpect(flash().attribute("recoveryRequested", true));
      verify(dispatcher).request(eq(email), any(RecoveryScope.class));
    }
    mvc.perform(post("/recuperar-senha").header("Host", A).param("email", "ana@boa-praca.example"))
        .andExpect(status().isForbidden());
  }

  @Test
  void tokensAreRandomHashedAndBoundToThePortal() throws Exception {
    var token = token("ana@boa-praca.example", scope("boa-praca"));
    assertThat(token).hasSize(43);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_tokens WHERE token_hash = ?",
                Integer.class,
                token))
        .isZero();
    entityManager.flush();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_tokens WHERE token_hash = ?",
                Integer.class,
                PasswordRecoveryService.hash(token)))
        .isOne();
    assertThat(recovery.valid(token, scope("boa-praca"))).isTrue();
    assertThat(recovery.valid(token, scope("vila-verde"))).isFalse();
    assertThat(recovery.valid(token, platformScope())).isFalse();
    mvc.perform(get("/redefinir-senha").header("Host", A).param("token", token))
        .andExpect(status().isOk())
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(content().string(containsString("Confirme a nova senha")));
    mvc.perform(get("/redefinir-senha").header("Host", B).param("token", token))
        .andExpect(content().string(containsString("Este link é inválido")));
  }

  @Test
  void resetChangesPasswordOnlyOnceAndInvalidatesExistingSessions() throws Exception {
    var session = tenantAdmin();
    var token = token("ana@boa-praca.example", scope("boa-praca"));
    mvc.perform(
            post("/redefinir-senha")
                .header("Host", A)
                .with(csrf())
                .param("token", token)
                .param("password", NEW_PASSWORD)
                .param("confirmation", NEW_PASSWORD))
        .andExpect(redirectedUrl("/login?senhaAlterada"));
    assertThat(recovery.valid(token, scope("boa-praca"))).isFalse();
    assertThatThrownBy(() -> recovery.reset(token, NEW_PASSWORD, NEW_PASSWORD, scope("boa-praca")))
        .isInstanceOf(IllegalArgumentException.class);
    mvc.perform(get("/filiais").header("Host", A).session(session))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/login")
                .header("Host", A)
                .with(csrf())
                .param("username", "ana@boa-praca.example")
                .param("password", PASSWORD))
        .andExpect(redirectedUrl("/login?erro"));
    mvc.perform(
            post("/login")
                .header("Host", A)
                .with(csrf())
                .param("username", "ana@boa-praca.example")
                .param("password", NEW_PASSWORD))
        .andExpect(redirectedUrl("/"));
    assertThat(events.stream(RecoveryMail.class).anyMatch(RecoveryMail::completed)).isTrue();
  }

  @Test
  void platformAdministratorCanRecoverWithoutAnExistingSession() {
    var token = token("plataforma@vendafacil.example", platformScope());
    recovery.reset(token, NEW_PASSWORD, NEW_PASSWORD, platformScope());
    var user = users.findByEmail("plataforma@vendafacil.example").orElseThrow();
    assertThat(encoder.matches(NEW_PASSWORD, user.getPasswordHash())).isTrue();
    assertThat(user.isPlatformAdmin()).isTrue();
  }

  @Test
  void expiredSupersededAndMismatchedTokensAreRejected() {
    var old = token("ana@boa-praca.example", scope("boa-praca"));
    var current = token("ana@boa-praca.example", scope("boa-praca"));
    assertThat(old).isNotEqualTo(current);
    assertThat(recovery.valid(old, scope("boa-praca"))).isFalse();
    assertThatThrownBy(
            () -> recovery.reset(current, NEW_PASSWORD, "Outra-Senha-2026!", scope("boa-praca")))
        .hasMessage("As senhas precisam ser iguais.");
    entityManager.flush();
    jdbc.update(
        "UPDATE password_reset_tokens SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute' WHERE token_hash = ?",
        PasswordRecoveryService.hash(current));
    entityManager.clear();
    assertThat(recovery.valid(current, scope("boa-praca"))).isFalse();
    assertThatThrownBy(
            () -> recovery.reset(current, NEW_PASSWORD, NEW_PASSWORD, scope("boa-praca")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ineligibleAccountDoesNotReceiveARecoveryToken() {
    for (var email :
        new String[] {
          "admin@vila-verde.example", "inexistente@example.test", "plataforma@vendafacil.example"
        }) {
      recovery.request(email, scope("boa-praca"));
    }
    recovery.request("ana@boa-praca.example", platformScope());
    assertThat(events.stream(RecoveryMail.class).count()).isZero();
  }

  @Test
  void resetDoesNotReactivateABlockedAccount() {
    var token = token("ana@boa-praca.example", scope("boa-praca"));
    entityManager.flush();
    jdbc.update("UPDATE users SET active = false WHERE email = 'ana@boa-praca.example'");
    entityManager.clear();
    assertThatThrownBy(() -> recovery.reset(token, NEW_PASSWORD, NEW_PASSWORD, scope("boa-praca")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(users.findByEmail("ana@boa-praca.example").orElseThrow().isActive()).isFalse();
  }

  @Test
  void platformCardsLinkDirectlyToTheCorrectPortalsAndUsersCanBeFiltered() throws Exception {
    var session = login(ADMIN, "plataforma@vendafacil.example");
    mvc.perform(get("/admin/empresas").header("Host", ADMIN).session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("http://boa-praca.localhost:8080/login")))
        .andExpect(content().string(containsString("Abrir portal")));
    mvc.perform(get("/admin/usuarios").header("Host", ADMIN).session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Gestão de usuários")));
    mvc.perform(
            get("/admin/usuarios")
                .header("Host", ADMIN)
                .session(session)
                .param("empresa", tenantId("boa-praca").toString()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("ana@boa-praca.example")))
        .andExpect(content().string(not(containsString("admin@vila-verde.example"))));
    mvc.perform(get("/admin/usuarios").header("Host", ADMIN).session(session).param("q", "Pedro"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("caixa@boa-praca.example")))
        .andExpect(content().string(not(containsString("ana@boa-praca.example"))));
  }

  @Test
  void platformCanRenameCompanyButCannotDisableOwnAdministrator() throws Exception {
    var session = login(ADMIN, "plataforma@vendafacil.example");
    mvc.perform(
            post("/admin/empresas/" + tenantId("boa-praca"))
                .header("Host", ADMIN)
                .session(session)
                .with(csrf())
                .param("name", "Mercado Renomeado"))
        .andExpect(redirectedUrl("/admin/empresas"));
    assertThat(tenants.findBySlug("boa-praca").orElseThrow().getName())
        .isEqualTo("Mercado Renomeado");
    var user = users.findByEmail("plataforma@vendafacil.example").orElseThrow();
    mvc.perform(
            post("/admin/usuarios/" + user.getId())
                .header("Host", ADMIN)
                .session(session)
                .with(csrf())
                .param("name", user.getName())
                .param("active", "false"))
        .andExpect(status().isBadRequest());
    assertThat(user.isActive()).isTrue();
  }

  @Test
  void platformCanBlockRestoreAndSendRecoveryForAnExistingUser() throws Exception {
    var session = login(ADMIN, "plataforma@vendafacil.example");
    var targetSession = login(A, "caixa@boa-praca.example");
    var userId = users.findByEmail("caixa@boa-praca.example").orElseThrow().getId();
    for (var active : new String[] {"false", "true"}) {
      mvc.perform(
              post("/admin/usuarios/" + userId)
                  .header("Host", ADMIN)
                  .session(session)
                  .with(csrf())
                  .param("name", "Pedro Atualizado")
                  .param("active", active))
          .andExpect(redirectedUrl("/admin/usuarios"));
    }
    mvc.perform(get("/filiais").header("Host", A).session(targetSession))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/admin/usuarios/" + userId + "/recuperacao")
                .header("Host", ADMIN)
                .session(session)
                .with(csrf())
                .param("tenantId", tenantId("boa-praca").toString()))
        .andExpect(redirectedUrl("/admin/usuarios"));
    assertThat(events.stream(RecoveryMail.class).filter(mail -> !mail.completed()).count()).isOne();
    mvc.perform(
            post("/admin/usuarios/" + userId + "/recuperacao")
                .header("Host", ADMIN)
                .session(session)
                .with(csrf())
                .param("tenantId", tenantId("vila-verde").toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  void tenantCanChangeRoleBlockAndRestoreOnlyItsOwnBranchAccess() throws Exception {
    var session = tenantAdmin();
    var operatorSession = login(A, "caixa@boa-praca.example");
    operatorSession.setAttribute(AccessService.ACTIVE_STORE, centerId());
    var role =
        roles.findAllByTenantIdOrderByNameAsc(tenantId("boa-praca")).stream()
            .filter(r -> r.getName().equals("Estoquista"))
            .findFirst()
            .orElseThrow();
    mvc.perform(
            post("/usuarios/acessos/" + operatorGrant())
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("roleId", role.getId().toString())
                .param("active", "false"))
        .andExpect(redirectedUrl("/usuarios"));
    mvc.perform(get("/").header("Host", A).session(operatorSession))
        .andExpect(redirectedUrl("/filiais"));
    mvc.perform(
            post("/usuarios/acessos/" + operatorGrant())
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("roleId", role.getId().toString())
                .param("active", "true"))
        .andExpect(redirectedUrl("/usuarios"));
    mvc.perform(
            post("/usuarios/acessos/" + operatorGrant() + "/recuperacao")
                .header("Host", A)
                .session(session)
                .with(csrf()))
        .andExpect(redirectedUrl("/usuarios"));
    assertThat(events.stream(RecoveryMail.class).count()).isOne();
  }

  @Test
  void tenantCannotElevateItselfUseForeignRolesOrManagePlatformUsers() throws Exception {
    var session = tenantAdmin();
    var member =
        memberships
            .findByTenantIdAndUserIdAndActiveTrue(
                tenantId("boa-praca"),
                users.findByEmail("ana@boa-praca.example").orElseThrow().getId())
            .orElseThrow();
    var ownGrant =
        grants
            .findAllByTenantIdAndMembershipIdAndStoreId(
                tenantId("boa-praca"), member.getId(), centerId())
            .getFirst();
    mvc.perform(
            post("/usuarios/acessos/" + ownGrant.getId())
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("roleId", ownGrant.getRoleId().toString())
                .param("active", "false"))
        .andExpect(status().isForbidden());
    var foreignRole = roles.findAllByTenantIdOrderByNameAsc(tenantId("vila-verde")).getFirst();
    mvc.perform(
            post("/usuarios/acessos/" + operatorGrant())
                .header("Host", A)
                .session(session)
                .with(csrf())
                .param("roleId", foreignRole.getId().toString())
                .param("active", "true"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/usuarios/acessos/" + UUID.randomUUID() + "/recuperacao")
                .header("Host", A)
                .session(session)
                .with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/admin/usuarios").header("Host", A).session(session))
        .andExpect(status().isForbidden());
  }
}
