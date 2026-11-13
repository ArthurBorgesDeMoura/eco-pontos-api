package br.com.ecoponto.conta;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ecoponto.contrato.ClienteDaApi;
import br.com.ecoponto.contrato.ClienteDaApi.Resposta;
import br.com.ecoponto.contrato.Exemplos;
import br.com.ecoponto.contrato.Exemplos.Caso;
import br.com.ecoponto.preparacao.ContasTesteService;
import br.com.ecoponto.preparacao.ContasTesteService.ContaTeste;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SessaoIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int porta;

    @Autowired
    private ContasTesteService contasTeste;

    @Autowired
    private UsuarioRepositorio usuarios;

    private List<ContaTeste> contas;

    @BeforeEach
    void prepararAsContasDeTeste() throws Exception {
        contas = ContasTesteService.lerContas(Path.of("..", "db", "ecoponto.sql"));
        contasTeste.refazerHashes(contas);
    }

    @Test
    void asTresContasDeTesteEntramEFicamNaSessao() throws Exception {
        assertThat(contas).hasSize(3);
        for (ContaTeste conta : contas) {
            ClienteDaApi cliente = new ClienteDaApi(porta);
            Resposta login = cliente.post("/api/auth/login",
                    JSON.writeValueAsString(Map.of("email", conta.email(), "senha", conta.senha())));

            assertThat(login.status()).as("login de %s", conta.email()).isEqualTo(200);
            JsonNode eu = JSON.readTree(cliente.get("/api/auth/eu").corpo());
            assertThat(eu.path("email").asText()).isEqualTo(conta.email());
            assertThat(eu.path("perfil").asText())
                    .isEqualTo(conta.email().startsWith("admin@") ? "ADMIN" : "USUARIO");
        }
    }

    @Test
    void oEmailComAcentoNaoEntraNaContaDoAdmin() throws Exception {
        String variacao = "ádmin@exámple.com";
        ContaTeste admin = contas.stream().filter(conta -> conta.email().startsWith("admin@")).findFirst()
                .orElseThrow();
        assertThat(usuarios.findByEmail(variacao)).as("a collation do banco ignora acentos").isPresent();

        Resposta login = new ClienteDaApi(porta).post("/api/auth/login",
                JSON.writeValueAsString(Map.of("email", variacao, "senha", admin.senha())));

        assertThat(login.status()).isEqualTo(401);
    }

    @Test
    void oLoginDaMariaSaiComoNoContratoEOLogoutEncerraASessao() throws Exception {
        Caso caso = Exemplos.caso("login-ok");
        ClienteDaApi cliente = new ClienteDaApi(porta);

        Resposta login = cliente.post("/api/auth/login", caso.pedidos().get(0).corpo().toString());
        assertThat(login.status()).isEqualTo(caso.status());
        JSONAssert.assertEquals(caso.corpo().toString(), login.corpo(), JSONCompareMode.STRICT);

        assertThat(cliente.post("/api/auth/logout", null).status()).isEqualTo(204);
        assertThat(cliente.get("/api/auth/eu").status()).isEqualTo(401);
    }
}
