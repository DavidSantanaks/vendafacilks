package br.com.vendafacil.shared.config;

import br.com.vendafacil.identity.DefaultRoles;
import br.com.vendafacil.identity.Membership;
import br.com.vendafacil.identity.MembershipRepository;
import br.com.vendafacil.identity.Role;
import br.com.vendafacil.identity.StoreRoleGrant;
import br.com.vendafacil.identity.StoreRoleRepository;
import br.com.vendafacil.identity.UserAccount;
import br.com.vendafacil.identity.UserRepository;
import br.com.vendafacil.shared.security.PasswordPolicy;
import br.com.vendafacil.store.Store;
import br.com.vendafacil.store.StoreRepository;
import br.com.vendafacil.tenant.Tenant;
import br.com.vendafacil.tenant.TenantRepository;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!production & (local | test)")
@ConditionalOnProperty(name = "app.demo-enabled", havingValue = "true")
public class DemoData implements ApplicationRunner {
  private final TenantRepository tenants;
  private final StoreRepository stores;
  private final UserRepository users;
  private final MembershipRepository memberships;
  private final StoreRoleRepository grants;
  private final DefaultRoles roles;
  private final PasswordEncoder encoder;
  private final AppProperties properties;

  public DemoData(
      TenantRepository tenants,
      StoreRepository stores,
      UserRepository users,
      MembershipRepository memberships,
      StoreRoleRepository grants,
      DefaultRoles roles,
      PasswordEncoder encoder,
      AppProperties properties) {
    this.tenants = tenants;
    this.stores = stores;
    this.users = users;
    this.memberships = memberships;
    this.grants = grants;
    this.roles = roles;
    this.encoder = encoder;
    this.properties = properties;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    if (tenants.count() > 0) {
      return;
    }
    if (properties.demoPassword() == null
        || properties.demoPassword().length() < 12
        || properties.demoPassword().length() > 64) {
      throw new IllegalStateException(
          "Defina DEMO_PASSWORD com 12 a 64 caracteres para criar os dados locais.");
    }
    PasswordPolicy.validate(properties.demoPassword());
    var hash = encoder.encode(properties.demoPassword());
    users.saveAndFlush(
        new UserAccount("plataforma@vendafacil.example", "Equipe vendaFacil", hash, true));
    var boaPraca = tenants.saveAndFlush(new Tenant("boa-praca", "Mercado Boa Praça"));
    var vilaVerde = tenants.saveAndFlush(new Tenant("vila-verde", "Hortifruti Vila Verde"));
    var center =
        stores.saveAndFlush(new Store(boaPraca.getId(), "Matriz · Centro", "Campinas", "SP"));
    var garden =
        stores.saveAndFlush(new Store(boaPraca.getId(), "Filial · Jardim", "Campinas", "SP"));
    var green =
        stores.saveAndFlush(new Store(vilaVerde.getId(), "Matriz · Taquaral", "Campinas", "SP"));
    var boaRoles = roles.create(boaPraca.getId());
    var greenRoles = roles.create(vilaVerde.getId());
    addUser(
        "ana@boa-praca.example",
        "Ana Souza",
        hash,
        boaPraca,
        List.of(center, garden),
        boaRoles.get(0));
    addUser(
        "caixa@boa-praca.example", "Pedro Lima", hash, boaPraca, List.of(center), boaRoles.get(2));
    addUser(
        "admin@vila-verde.example",
        "Marina Costa",
        hash,
        vilaVerde,
        List.of(green),
        greenRoles.get(0));
  }

  private void addUser(
      String email, String name, String hash, Tenant tenant, List<Store> allowedStores, Role role) {
    var user = users.saveAndFlush(new UserAccount(email, name, hash, false));
    var membership = memberships.saveAndFlush(new Membership(tenant.getId(), user.getId()));
    for (var store : allowedStores) {
      grants.save(
          new StoreRoleGrant(tenant.getId(), membership.getId(), store.getId(), role.getId()));
    }
  }
}
