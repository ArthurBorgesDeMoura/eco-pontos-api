package br.com.ecoponto.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ecoponto.contrato.ClienteDaApi;
import br.com.ecoponto.contrato.ClienteDaApi.Resposta;
import br.com.ecoponto.contrato.Exemplos;
import br.com.ecoponto.contrato.Exemplos.Caso;
import br.com.ecoponto.contrato.Exemplos.Pedido;
import br.com.ecoponto.conta.UsuarioRepositorio;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class SessaoTest {

    @LocalServerPort
    private int porta;

    @Autowired
    private PasswordEncoder codificador;

    @MockBean
    private UsuarioRepositorio usuarios;

    @BeforeEach
    void contasDoScript() {
        ContasDoScript.simular(usuarios, codificador);
    }

    @Test
    void semSessaoOEuDa401DoContratoEJaEntregaOTokenCsrf() throws Exception {
        Resposta resposta = new ClienteDaApi(porta).get("/api/auth/eu");

        assertThat(resposta.status()).isEqualTo(401);
        JSONAssert.assertEquals(Exemplos.caso("sem-login").corpo().toString(), resposta.corpo(),
                JSONCompareMode.STRICT);
        assertThat(resposta.setCookie("XSRF-TOKEN")).hasValueSatisfying(cookie -> assertThat(cookie)
                .contains("SameSite=Lax").doesNotContain("HttpOnly"));
        assertThat(resposta.setCookie("JSESSIONID")).as("o visitante não ganha sessão").isEmpty();
    }

    @Test
    void semOTokenCsrfOLoginDa403() {
        Resposta resposta = new ClienteDaApi(porta).semTokenCsrf().post("/api/auth/login", corpoDoLoginDaMaria());

        assertThat(resposta.status()).isEqualTo(403);
        assertThat(resposta.corpo()).isEqualTo(
                "{\"codigo\":\"SEM_PERMISSAO\",\"mensagem\":\"Atualize a página e tente de novo.\"}");
    }

    @Test
    void loginCertoAbreASessaoNoCookieDaDa2ETrocaOTokenCsrf() throws Exception {
        Caso caso = Exemplos.caso("login-ok");
        ClienteDaApi cliente = new ClienteDaApi(porta);
        cliente.get("/api/auth/eu");
        String tokenAntes = cliente.cookie("XSRF-TOKEN").orElseThrow();

        Resposta login = cliente.post("/api/auth/login", corpoDoLoginDaMaria());

        assertThat(login.status()).isEqualTo(caso.status());
        JSONAssert.assertEquals(caso.corpo().toString(), login.corpo(), JSONCompareMode.STRICT);
        assertThat(login.setCookie("JSESSIONID")).hasValueSatisfying(cookie -> assertThat(cookie)
                .contains("HttpOnly").contains("SameSite=Lax").doesNotContain("Secure"));
        assertThat(cliente.cookie("XSRF-TOKEN")).isPresent().isNotEqualTo(Optional.of(tokenAntes));

        Resposta eu = cliente.get("/api/auth/eu");
        assertThat(eu.status()).isEqualTo(200);
        JSONAssert.assertEquals(caso.corpo().toString(), eu.corpo(), JSONCompareMode.STRICT);
    }

    @Test
    void oEuDoAdminSaiComoNoContrato() throws Exception {
        Caso caso = Exemplos.caso("eu-logado");
        ClienteDaApi cliente = new ClienteDaApi(porta);
        cliente.post("/api/auth/login", corpoDoLogin("admin@example.com"));

        Resposta eu = cliente.get("/api/auth/eu");

        assertThat(eu.status()).isEqualTo(caso.status());
        JSONAssert.assertEquals(caso.corpo().toString(), eu.corpo(), JSONCompareMode.STRICT);
    }

    @Test
    void depoisDoLogoutOEuDa401() {
        ClienteDaApi cliente = new ClienteDaApi(porta);
        assertThat(cliente.post("/api/auth/login", corpoDoLoginDaMaria()).status()).isEqualTo(200);

        Resposta logout = cliente.post("/api/auth/logout", null);

        assertThat(logout.status()).isEqualTo(204);
        assertThat(logout.corpo()).isEmpty();
        assertThat(cliente.get("/api/auth/eu").status()).isEqualTo(401);
        assertThat(cliente.post("/api/auth/logout", null).status()).as("logout sem sessão").isEqualTo(204);
    }

    @Test
    void depoisDoLogoutDaParaEntrarDeNovoSemRecarregarAPagina() {
        ClienteDaApi cliente = new ClienteDaApi(porta);
        cliente.post("/api/auth/login", corpoDoLoginDaMaria());
        String tokenDaSessao = cliente.cookie("XSRF-TOKEN").orElseThrow();

        cliente.post("/api/auth/logout", null);

        assertThat(cliente.cookie("XSRF-TOKEN")).isPresent().isNotEqualTo(Optional.of(tokenDaSessao));
        assertThat(cliente.post("/api/auth/login", corpoDoLoginDaMaria()).status()).isEqualTo(200);
    }

    @Test
    void cadaLoginTrocaOIdDaSessao() {
        ClienteDaApi cliente = new ClienteDaApi(porta);
        cliente.post("/api/auth/login", corpoDoLoginDaMaria());
        String primeira = cliente.cookie("JSESSIONID").orElseThrow();

        cliente.post("/api/auth/login", corpoDoLoginDaMaria());

        assertThat(cliente.cookie("JSESSIONID")).isPresent().isNotEqualTo(Optional.of(primeira));
    }

    @Test
    void loginErradoDaO401UnicoDoContrato() throws Exception {
        Caso caso = Exemplos.caso("login-errado");
        for (Pedido pedido : caso.pedidos()) {
            Resposta resposta = new ClienteDaApi(porta).post(pedido.caminho(), pedido.corpo().toString());

            assertThat(resposta.status()).isEqualTo(caso.status());
            JSONAssert.assertEquals(caso.corpo().toString(), resposta.corpo(), JSONCompareMode.STRICT);
        }
    }

    @Test
    void depoisDe5FalhasALoginSeguinteLeva429ComRetryAfter() throws Exception {
        String email = "bloqueado-" + System.nanoTime() + "@example.com";
        ClienteDaApi cliente = new ClienteDaApi(porta);
        for (int i = 0; i < 5; i++) {
            assertThat(cliente.post("/api/auth/login", corpo(email, "senha errada")).status()).isEqualTo(401);
        }

        Resposta bloqueado = cliente.post("/api/auth/login", corpo(email, "senha errada"));

        assertThat(bloqueado.status()).isEqualTo(429);
        assertThat(bloqueado.retryAfter()).contains("300");
        JSONAssert.assertEquals(Exemplos.caso("login-bloqueado").corpo().toString(), bloqueado.corpo(),
                JSONCompareMode.STRICT);
    }

    @Test
    void nenhumLogMostraSenhaNemSenhaGeradaDoSpring(CapturedOutput saida) {
        ClienteDaApi cliente = new ClienteDaApi(porta);
        cliente.post("/api/auth/login", corpoDoLoginDaMaria());
        cliente.post("/api/auth/login", corpo("maria.souza@example.com", "SenhaErradaQueNaoAparece"));

        assertThat(saida.getAll()).doesNotContain("Using generated security password")
                .doesNotContain("SenhaErradaQueNaoAparece");
        ContasDoScript.todas().forEach(conta -> assertThat(saida.getAll()).doesNotContain(conta.senha()));
    }

    private static String corpoDoLoginDaMaria() {
        return corpoDoLogin(ContasDoScript.MARIA);
    }

    private static String corpoDoLogin(String email) {
        return ContasDoScript.corpoDoLogin(email);
    }

    private static String corpo(String email, String senha) {
        return ContasDoScript.corpo(email, senha);
    }
}
