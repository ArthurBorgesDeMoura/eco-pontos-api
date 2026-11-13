package br.com.ecoponto;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ecoponto.contrato.ClienteDaApi;
import br.com.ecoponto.contrato.ClienteDaApi.Resposta;
import br.com.ecoponto.preparacao.ContasTesteService;
import br.com.ecoponto.seguranca.ContasDoScript;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RoteiroDePontaAPontaIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContasTesteService contasTeste;

    private String emailCriado;
    private Integer pontoCriado;

    @AfterEach
    void apagarOQueORoteiroCriou() {
        if (pontoCriado != null) {
            jdbc.update("DELETE FROM ponto_coleta WHERE id_ponto = ?", pontoCriado);
        }
        if (emailCriado != null) {
            jdbc.update("DELETE FROM usuario WHERE email = ?", emailCriado);
        }
    }

    @Test
    void cadastrarEntrarBuscarSugerirAprovarEditarExcluir() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta WHERE status = 'APROVADO'", Integer.class))
                .as("aprovados; recarregue o db/ecoponto.sql (README, Antes da entrega)").isEqualTo(13);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta", Integer.class)).as("pontos").isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categoria_residuo", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario", Integer.class)).as("contas").isEqualTo(3);
        contasTeste.refazerHashes(ContasDoScript.todas());

        emailCriado = "roteiro-" + System.nanoTime() + "@example.com";
        ClienteDaApi pessoa = new ClienteDaApi(porta);
        Resposta cadastro = pessoa.post("/api/auth/cadastro", JSON.createObjectNode().put("nome", "Ana Roteiro")
                .put("email", emailCriado).put("senha", "senha-do-roteiro").toString());
        assertThat(cadastro.status()).as("cadastro: %s", cadastro.corpo()).isEqualTo(201);
        assertThat(ler(cadastro).path("perfil").asText()).isEqualTo("USUARIO");
        assertThat(pessoa.post("/api/auth/login", ContasDoScript.corpo(emailCriado, "senha-do-roteiro")).status())
                .as("login").isEqualTo(200);
        assertThat(ler(pessoa.get("/api/auth/eu")).path("email").asText()).isEqualTo(emailCriado);

        JsonNode busca = ler(pessoa.get("/api/pontos"));
        assertThat(busca.path("total").asInt()).isEqualTo(13);
        assertThat(busca.path("itens")).hasSize(7);
        assertThat(ids(ler(pessoa.get("/api/pontos?bairro=4&categoria=7")))).containsExactly(4);
        JsonNode luizote = ler(pessoa.get("/api/pontos/13"));
        assertThat(luizote.path("nome").asText()).isEqualTo("Ecoponto Luizote de Freitas");
        assertThat(luizote.path("materiais")).isNotEmpty();

        ObjectNode sugestao = JSON.createObjectNode().put("nome", "Mercado do Roteiro")
                .put("endereco", "Rua das Flores, 50").put("idBairro", 20).put("horario", "8h às 18h")
                .putNull("contato");
        sugestao.putArray("categorias").add(6).add(2);
        Resposta sugerida = pessoa.post("/api/pontos", sugestao.toString());
        assertThat(sugerida.status()).as("sugerir: %s", sugerida.corpo()).isEqualTo(201);
        pontoCriado = ler(sugerida).path("idPonto").asInt();
        JsonNode minhas = ler(pessoa.get("/api/minhas-sugestoes"));
        assertThat(ids(minhas)).containsExactly(pontoCriado);
        assertThat(minhas.path("itens").get(0).path("status").asText()).isEqualTo("PENDENTE");
        assertThat(ids(ler(pessoa.get("/api/pontos?tamanho=50")))).doesNotContain(pontoCriado);

        ClienteDaApi admin = new ClienteDaApi(porta);
        assertThat(admin.post("/api/auth/login", ContasDoScript.corpoDoLogin(ContasDoScript.ADMIN)).status())
                .isEqualTo(200);
        assertThat(ids(ler(admin.get("/api/admin/pontos")))).containsExactly(14, 15, pontoCriado);
        Resposta aprovada = admin.post("/api/admin/pontos/" + pontoCriado + "/aprovar", null);
        assertThat(aprovada.status()).as("aprovar: %s", aprovada.corpo()).isEqualTo(200);
        assertThat(ids(ler(pessoa.get("/api/pontos?bairro=20&tamanho=50")))).contains(pontoCriado);
        assertThat(ler(pessoa.get("/api/minhas-sugestoes")).path("itens").get(0).path("status").asText())
                .isEqualTo("APROVADO");
        assertThat(ler(admin.get("/api/admin/resumo")).toString())
                .isEqualTo("{\"pendentes\":2,\"pontos\":14,\"categorias\":10}");

        ObjectNode edicao = sugestao.deepCopy().put("nome", "Mercado do Roteiro (editado)");
        edicao.putArray("categorias").add(7);
        Resposta editada = admin.enviar("PUT", "/api/admin/pontos/" + pontoCriado, edicao.toString());
        assertThat(editada.status()).as("editar: %s", editada.corpo()).isEqualTo(200);
        JsonNode detalhe = ler(pessoa.get("/api/pontos/" + pontoCriado));
        assertThat(detalhe.path("nome").asText()).isEqualTo("Mercado do Roteiro (editado)");
        assertThat(detalhe.path("materiais")).singleElement()
                .satisfies(material -> assertThat(material.path("idCategoria").asInt()).isEqualTo(7));
        assertThat(ids(ler(pessoa.get("/api/pontos?categoria=7&tamanho=50")))).contains(pontoCriado);
        assertThat(ids(ler(pessoa.get("/api/pontos?categoria=6&tamanho=50")))).doesNotContain(pontoCriado);

        assertThat(admin.enviar("DELETE", "/api/admin/pontos/" + pontoCriado, null).status()).isEqualTo(204);
        assertThat(pessoa.get("/api/pontos/" + pontoCriado).status()).isEqualTo(404);
        assertThat(ler(pessoa.get("/api/pontos")).path("total").asInt()).isEqualTo(13);
        assertThat(ler(admin.get("/api/admin/resumo")).toString())
                .isEqualTo("{\"pendentes\":2,\"pontos\":13,\"categorias\":10}");
        assertThat(ler(pessoa.get("/api/minhas-sugestoes")).path("total").asInt()).isZero();
        pontoCriado = null;

        pessoa.post("/api/auth/logout", null);
        admin.post("/api/auth/logout", null);
        assertThat(pessoa.get("/api/auth/eu").status()).isEqualTo(401);
        assertThat(admin.get("/api/admin/resumo").status()).isEqualTo(401);
    }

    private static JsonNode ler(Resposta resposta) throws Exception {
        assertThat(resposta.status()).as(resposta.corpo()).isBetween(200, 299);
        return JSON.readTree(resposta.corpo());
    }

    private static List<Integer> ids(JsonNode pagina) {
        List<Integer> ids = new ArrayList<>();
        pagina.path("itens").forEach(item -> ids.add(item.path("idPonto").asInt()));
        return ids;
    }
}
