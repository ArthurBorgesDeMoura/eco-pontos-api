package br.com.ecoponto.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ecoponto.contrato.ClienteDaApi;
import br.com.ecoponto.preparacao.ContasTesteService;
import br.com.ecoponto.preparacao.ContasTesteService.ContaTeste;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AutorizacaoIT {

    @LocalServerPort
    private int porta;

    @Autowired
    private ContasTesteService contasTeste;

    private List<ContaTeste> contas;

    @BeforeEach
    void prepararAsContasDeTeste() throws Exception {
        contas = ContasTesteService.lerContas(Path.of("..", "db", "ecoponto.sql"));
        contasTeste.refazerHashes(contas);
    }

    @Test
    void cadaContaDoScriptTemOAcessoDoSeuPerfil() {
        Map<String, List<Integer>> esperado = Map.of(
                ContasDoScript.ADMIN, List.of(200, 200),
                ContasDoScript.MARIA, List.of(403, 200),
                ContasDoScript.JOAO, List.of(403, 200));
        assertThat(contas).extracting(ContaTeste::email).containsExactlyInAnyOrderElementsOf(esperado.keySet());

        for (ContaTeste conta : contas) {
            ClienteDaApi cliente = new ClienteDaApi(porta);
            assertThat(cliente.post("/api/auth/login", ContasDoScript.corpo(conta.email(), conta.senha())).status())
                    .as("login de %s", conta.email()).isEqualTo(200);

            assertThat(acessos(cliente)).as(conta.email()).isEqualTo(esperado.get(conta.email()));
        }

        assertThat(acessos(new ClienteDaApi(porta))).as("visitante").containsExactly(401, 401);
    }

    private static List<Integer> acessos(ClienteDaApi cliente) {
        return List.of(cliente.get("/api/admin/resumo").status(), cliente.get("/api/minhas-sugestoes").status());
    }
}
