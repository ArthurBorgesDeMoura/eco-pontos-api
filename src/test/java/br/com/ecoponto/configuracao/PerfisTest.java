package br.com.ecoponto.configuracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ecoponto.EcoPontoApplication;
import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

class PerfisTest {

    private static final List<String> VARIAVEIS_DO_PROD = List.of(
            "--ECOPONTO_DB_HOST=banco.exemplo",
            "--ECOPONTO_DB_PORTA=3307",
            "--ECOPONTO_DB_NOME=ecoponto",
            "--ECOPONTO_DB_USUARIO=ecoponto_app",
            "--ECOPONTO_DB_SENHA=senha-falsa-dos-testes",
            "--ECOPONTO_CORS_ORIGENS=https://ecoponto.exemplo");

    @Test
    void oPerfilDevLeAsVariaveisEDeixaOCookieSemSecure() {
        try (ConfigurableApplicationContext contexto = subir("dev", List.of(
                "--ECOPONTO_DB_HOST=banco.local",
                "--ECOPONTO_DB_PORTA=3310",
                "--ECOPONTO_DB_NOME=outro",
                "--ECOPONTO_DB_USUARIO=fulano",
                "--ECOPONTO_DB_SENHA=senha-falsa-dos-testes",
                "--ECOPONTO_API_PORTA=9090",
                "--ECOPONTO_CORS_ORIGENS=http://localhost:4300"))) {
            HikariDataSource banco = contexto.getBean(HikariDataSource.class);
            Environment ambiente = contexto.getEnvironment();

            assertThat(banco.getJdbcUrl()).isEqualTo("jdbc:mariadb://banco.local:3310/outro");
            assertThat(banco.getUsername()).isEqualTo("fulano");
            assertThat(ambiente.getProperty("server.port")).isEqualTo("9090");
            assertThat(ambiente.getProperty("ecoponto.cors.origens")).isEqualTo("http://localhost:4300");
            assertThat(ambiente.getProperty("server.servlet.session.cookie.secure")).isEqualTo("false");
            assertThat(ambiente.getProperty("server.servlet.session.cookie.http-only")).isEqualTo("true");
            assertThat(ambiente.getProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
        }
    }

    @Test
    void oPerfilProdLeTudoDoAmbienteEExigeHttpsNoCookie() {
        try (ConfigurableApplicationContext contexto = subir("prod", VARIAVEIS_DO_PROD)) {
            HikariDataSource banco = contexto.getBean(HikariDataSource.class);

            assertThat(banco.getJdbcUrl()).isEqualTo("jdbc:mariadb://banco.exemplo:3307/ecoponto");
            assertThat(contexto.getEnvironment().getProperty("ecoponto.cors.origens"))
                    .isEqualTo("https://ecoponto.exemplo");
            assertThat(contexto.getEnvironment().getProperty("server.servlet.session.cookie.secure"))
                    .isEqualTo("true");
        }
    }

    @Test
    void oPerfilProdNaoSobeSemAsVariaveis() {
        List<String> semOHost = VARIAVEIS_DO_PROD.stream()
                .filter(argumento -> !argumento.startsWith("--ECOPONTO_DB_HOST="))
                .toList();

        assertThatThrownBy(() -> subir("prod", semOHost).close())
                .hasStackTraceContaining("ECOPONTO_DB_HOST");
    }

    @Test
    void naoSobeComASenhaDoBancoVazia() {
        assertThatThrownBy(() -> subir("dev", List.of("--ECOPONTO_DB_SENHA=")).close())
                .hasStackTraceContaining("Preencha ECOPONTO_DB_SENHA");
    }

    private static ConfigurableApplicationContext subir(String perfil, List<String> variaveis) {
        List<String> argumentos = new ArrayList<>(Arrays.asList(
                "--spring.profiles.active=" + perfil, "--spring.main.banner-mode=off"));
        argumentos.addAll(variaveis);
        StandardEnvironment ambiente = new StandardEnvironment();
        ambiente.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        return new SpringApplicationBuilder(EcoPontoApplication.class)
                .environment(ambiente)
                .web(WebApplicationType.NONE)
                .run(argumentos.toArray(String[]::new));
    }
}
