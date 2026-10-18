package br.com.vendafacil.shared.security;

import br.com.vendafacil.identity.MembershipRepository;
import br.com.vendafacil.identity.UserRepository;
import br.com.vendafacil.shared.config.AppProperties;
import br.com.vendafacil.tenant.HostResolver;
import br.com.vendafacil.tenant.TenantRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@Configuration
public class SecurityConfiguration {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      TenantAuthenticationProvider provider,
      HostResolver resolver,
      TenantRepository tenants,
      AppProperties properties,
      MembershipRepository memberships,
      UserRepository users)
      throws Exception {
    http.authenticationProvider(provider)
        .addFilterBefore(
            new TenantHostFilter(resolver, tenants, properties), SecurityContextHolderFilter.class)
        .addFilterBefore(new MembershipGuardFilter(memberships, users), AuthorizationFilter.class)
        .authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(
                        "/login",
                        "/recuperar-senha",
                        "/redefinir-senha",
                        "/css/**",
                        "/js/**",
                        "/vendor/**",
                        "/img/**",
                        "/error",
                        "/actuator/health")
                    .permitAll()
                    .requestMatchers("/admin/**")
                    .hasRole("PLATFORM_ADMIN")
                    .anyRequest()
                    .authenticated())
        .formLogin(
            login ->
                login
                    .loginPage("/login")
                    .defaultSuccessUrl("/", true)
                    .failureUrl("/login?erro")
                    .permitAll())
        .logout(
            logout ->
                logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/login?saiu")
                    .invalidateHttpSession(true)
                    .deleteCookies("VFSESSION"))
        .sessionManagement(
            session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp ->
                            csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"))
                    .frameOptions(frame -> frame.deny()))
        .exceptionHandling(
            errors ->
                errors
                    .defaultAuthenticationEntryPointFor(
                        (request, response, _) ->
                            SecurityResponses.error(
                                request,
                                response,
                                401,
                                "Sua sessão expirou. Entre novamente para continuar."),
                        request -> request.getRequestURI().startsWith("/api/"))
                    .accessDeniedHandler(
                        (request, response, _) ->
                            SecurityResponses.error(
                                request,
                                response,
                                403,
                                "Você não tem permissão para esta operação.")));
    return http.build();
  }
}
