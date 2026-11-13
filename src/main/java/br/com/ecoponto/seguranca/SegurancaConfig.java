package br.com.ecoponto.seguranca;

import br.com.ecoponto.conta.Perfil;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
public class SegurancaConfig {

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class Filtros {

        @Bean
        SecurityFilterChain filtrosDeSeguranca(HttpSecurity http, RespostasDeSeguranca respostas,
                CookieCsrfTokenRepository tokensCsrf, SecurityContextRepository contextos) throws Exception {
            http
                    .cors(Customizer.withDefaults())
                    .csrf(csrf -> csrf.csrfTokenRepository(tokensCsrf).csrfTokenRequestHandler(new TokenCsrfDeSpa()))
                    .addFilterAfter(new CarregarTokenCsrfFilter(), CsrfFilter.class)
                    .securityContext(contexto -> contexto.securityContextRepository(contextos))
                    .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable)
                    .exceptionHandling(erros -> erros.authenticationEntryPoint(respostas)
                            .accessDeniedHandler(respostas))
                    .logout(saida -> saida
                            .logoutUrl("/api/auth/logout")
                            .logoutSuccessHandler(new SaidaDaConta(tokensCsrf))
                            .deleteCookies("JSESSIONID"))
                    .authorizeHttpRequests(SegurancaConfig::quemAcessaCadaRota);
            return http.build();
        }
    }

    static void quemAcessaCadaRota(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry rotas) {
        rotas
                .requestMatchers("/api/admin", "/api/admin/**").hasRole(Perfil.ADMIN.name())
                .requestMatchers("/api/auth/eu").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/pontos").authenticated()
                .requestMatchers("/api/minhas-sugestoes").authenticated()
                .anyRequest().permitAll();
    }

    @Bean
    public CookieCsrfTokenRepository tokensCsrf() {
        CookieCsrfTokenRepository tokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokens.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return tokens;
    }

    @Bean
    public SecurityContextRepository contextosDeSeguranca() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SessionAuthenticationStrategy aoEntrar(CookieCsrfTokenRepository tokensCsrf) {
        CsrfAuthenticationStrategy trocarToken = new CsrfAuthenticationStrategy(tokensCsrf);
        trocarToken.setRequestHandler(new TokenCsrfDeSpa());
        return new CompositeSessionAuthenticationStrategy(
                List.of(new ChangeSessionIdAuthenticationStrategy(), trocarToken));
    }

    @Bean
    public UserDetailsService semUsuariosDoSpring() {
        return nome -> {
            throw new UsernameNotFoundException("O login é o POST /api/auth/login.");
        };
    }

    @Bean
    public Clock relogio() {
        return Clock.systemDefaultZone();
    }
}
