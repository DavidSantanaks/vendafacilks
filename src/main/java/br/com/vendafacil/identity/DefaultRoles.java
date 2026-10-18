package br.com.vendafacil.identity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DefaultRoles {
  private final RoleRepository roles;
  private final JdbcTemplate jdbc;

  public DefaultRoles(RoleRepository roles, JdbcTemplate jdbc) {
    this.roles = roles;
    this.jdbc = jdbc;
  }

  public List<Role> create(UUID tenantId) {
    jdbc.update("INSERT INTO tenant_policies (tenant_id) VALUES (?)", tenantId);
    return roles.saveAll(
        List.of(
            new Role(tenantId, "Administrador", "20", EnumSet.allOf(Permission.class)),
            new Role(
                tenantId,
                "Gerente",
                "10",
                EnumSet.complementOf(
                    EnumSet.of(Permission.USER_MANAGE, Permission.FISCAL_CONFIGURE))),
            new Role(
                tenantId,
                "Operador de caixa",
                "0",
                Set.of(
                    Permission.PRODUCT_READ,
                    Permission.SALE_CREATE,
                    Permission.CASH_OPEN,
                    Permission.CASH_CLOSE)),
            new Role(
                tenantId,
                "Estoquista",
                "0",
                Set.of(
                    Permission.PRODUCT_READ,
                    Permission.PRODUCT_WRITE,
                    Permission.PRODUCT_IMPORT,
                    Permission.STOCK_READ,
                    Permission.STOCK_ADJUST))));
  }
}
