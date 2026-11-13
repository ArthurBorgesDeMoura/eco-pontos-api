package br.com.ecoponto.ponto;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ecoponto.contrato.ClienteDaApi;
import br.com.ecoponto.contrato.ClienteDaApi.Resposta;
import br.com.ecoponto.contrato.Exemplos;
import br.com.ecoponto.contrato.Exemplos.Caso;
import br.com.ecoponto.contrato.Exemplos.Pedido;
import br.com.ecoponto.preparacao.ContasTesteService;
import br.com.ecoponto.seguranca.ContasDoScript;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=warn"
})
class MinhasSugestoesIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ROTA = "/api/minhas-sugestoes";

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory fabrica;

    @Autowired
    private ContasTesteService contasTeste;

    private int maiorIdAntes;

    private static boolean contasProntas;

    @BeforeEach
    void prepararAsContasDeTeste() {
        if (!contasProntas) {
            contasTeste.refazerHashes(ContasDoScript.todas());
            contasProntas = true;
        }
        maiorIdAntes = jdbc.queryForObject("SELECT COALESCE(MAX(id_ponto), 0) FROM ponto_coleta", Integer.class);
    }

    @AfterEach
    void apagarOsPontosCriados() {
        jdbc.update("DELETE FROM ponto_coleta WHERE id_ponto > ?", maiorIdAntes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"minhas-sugestoes", "minhas-sugestoes-vazia", "minhas-sugestoes-status-invalido"})
    void respondeComoOsExemplosDoContrato(String nome) throws Exception {
        Caso caso = Exemplos.caso(nome);
        for (Pedido pedido : caso.pedidos()) {
            Resposta resposta = cliente(pedido.sessao()).get(pedido.caminho());

            assertThat(resposta.status()).as(pedido.caminho()).isEqualTo(caso.status());
            JSONAssert.assertEquals(caso.corpo().toString(), resposta.corpo(), JSONCompareMode.STRICT);
        }
    }

    @Test
    void cadaContaVeSoAsPropriasSugestoes() throws Exception {
        Map<String, List<Integer>> esperado = Map.of(
                ContasDoScript.MARIA, List.of(15),
                ContasDoScript.JOAO, List.of(14),
                ContasDoScript.ADMIN, IntStream.iterate(13, id -> id >= 1, id -> id - 1).boxed().toList());
        for (Map.Entry<String, List<Integer>> conta : esperado.entrySet()) {
            ClienteDaApi cliente = cliente(conta.getKey());
            Integer idDaConta = idDe(conta.getKey());

            assertThat(ids(lista(cliente, ROTA + "?tamanho=50"))).as(conta.getKey()).isEqualTo(conta.getValue())
                    .isEqualTo(jdbc.queryForList("SELECT id_ponto FROM ponto_coleta WHERE id_usuario = ?"
                            + " ORDER BY id_ponto DESC", Integer.class, idDaConta));
            for (int outro = 1; outro <= 3; outro++) {
                String comId = ROTA + "?tamanho=50&idUsuario=" + outro + "&autor=" + outro + "&usuario=" + outro;
                assertThat(ids(lista(cliente, comId))).as("%s pedindo o usuário %d", conta.getKey(), outro)
                        .isEqualTo(conta.getValue());
            }
        }
    }

    @Test
    void oFiltroDeStatusDevolveSoOStatusPedido() throws Exception {
        ClienteDaApi maria = cliente(ContasDoScript.MARIA);
        int aprovada = sugerir(maria, "Sugestão que vai ser aprovada");
        int recusada = sugerir(maria, "Sugestão que vai ser recusada");
        jdbc.update("UPDATE ponto_coleta SET status = 'APROVADO' WHERE id_ponto = ?", aprovada);
        jdbc.update("UPDATE ponto_coleta SET status = 'RECUSADO' WHERE id_ponto = ?", recusada);

        JsonNode todas = lista(maria, ROTA);
        assertThat(ids(todas)).containsExactly(recusada, aprovada, 15);
        assertThat(todas.path("total").asInt()).isEqualTo(3);
        assertThat(statusDe(todas)).containsExactly("RECUSADO", "APROVADO", "PENDENTE");
        Map<String, Integer> umDeCada = Map.of("PENDENTE", 15, "APROVADO", aprovada, "RECUSADO", recusada);
        for (Map.Entry<String, Integer> status : umDeCada.entrySet()) {
            JsonNode filtrada = lista(maria, ROTA + "?status=" + status.getKey());

            assertThat(ids(filtrada)).as(status.getKey()).containsExactly(status.getValue());
            assertThat(statusDe(filtrada)).as(status.getKey()).containsOnly(status.getKey());
            assertThat(filtrada.path("total").asInt()).as(status.getKey()).isEqualTo(1);
        }

        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        assertThat(lista(admin, ROTA + "?status=APROVADO").path("total").asInt()).isEqualTo(13);
        assertThat(lista(admin, ROTA + "?status=PENDENTE").path("total").asInt()).isZero();
        assertThat(lista(admin, ROTA + "?status=RECUSADO").path("itens")).isEmpty();
        assertThat(ids(lista(cliente(ContasDoScript.JOAO), ROTA))).as("o João não vê as da Maria").containsExactly(14);
    }

    @Test
    void semLoginDa401() throws Exception {
        String semLogin = Exemplos.caso("sem-login").corpo().toString();
        List<String> caminhos = List.of(ROTA, ROTA + "?status=PENDENTE", ROTA + "?status=X&pagina=0",
                ROTA + "?idUsuario=2");
        for (String caminho : caminhos) {
            Resposta resposta = cliente(null).get(caminho);

            assertThat(resposta.status()).as(caminho).isEqualTo(401);
            JSONAssert.assertEquals(semLogin, resposta.corpo(), JSONCompareMode.STRICT);
        }

        ClienteDaApi maria = cliente(ContasDoScript.MARIA);
        assertThat(maria.get(ROTA).status()).isEqualTo(200);
        maria.post("/api/auth/logout", null);
        assertThat(maria.get(ROTA).status()).isEqualTo(401);
    }

    @Test
    void umaSugestaoRecemEnviadaApareceComoPendente() throws Exception {
        ClienteDaApi maria = cliente(ContasDoScript.MARIA);
        Resposta criada = maria.post("/api/pontos", corpoDaSugestao("Mercado do Bairro").toString());
        assertThat(criada.status()).isEqualTo(201);
        JsonNode ponto = JSON.readTree(criada.corpo());

        JsonNode lista = lista(maria, ROTA);

        assertThat(lista.path("total").asInt()).isEqualTo(2);
        JsonNode primeira = lista.path("itens").get(0);
        assertThat(primeira.path("idPonto")).isEqualTo(ponto.path("idPonto"));
        assertThat(primeira.path("status").asText()).isEqualTo("PENDENTE");
        assertThat(primeira.path("nome")).isEqualTo(ponto.path("nome"));
        assertThat(primeira.path("endereco")).isEqualTo(ponto.path("endereco"));
        assertThat(primeira.path("bairro")).isEqualTo(ponto.path("bairro"));
        assertThat(materiais(primeira)).isEqualTo(materiais(ponto))
                .containsExactly("2 Plástico", "6 Pilhas e baterias");
        assertThat(ids(lista(maria, ROTA + "?status=PENDENTE"))).containsExactly(ponto.path("idPonto").asInt(), 15);
        assertThat(ids(lista(cliente(ContasDoScript.JOAO), ROTA))).containsExactly(14);
    }

    @Test
    void asPaginasPercorremTodasSemRepetirNemPular() throws Exception {
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        List<Integer> todas = ids(lista(admin, ROTA + "?tamanho=50"));
        assertThat(todas).hasSize(13).isSortedAccordingTo((a, b) -> b - a);

        for (int tamanho = 1; tamanho <= 13; tamanho++) {
            List<Integer> juntadas = new ArrayList<>();
            for (int pagina = 1; ; pagina++) {
                JsonNode corpo = lista(admin, ROTA + "?pagina=" + pagina + "&tamanho=" + tamanho);
                assertThat(corpo.path("total").asInt()).isEqualTo(13);
                assertThat(corpo.path("pagina").asInt()).isEqualTo(pagina);
                assertThat(corpo.path("tamanho").asInt()).isEqualTo(tamanho);
                if (corpo.path("itens").isEmpty()) {
                    break;
                }
                juntadas.addAll(ids(corpo));
            }
            assertThat(juntadas).as("tamanho %d", tamanho).isEqualTo(todas);
        }
        assertThat(lista(admin, ROTA + "?pagina=999999999&tamanho=50").path("itens")).isEmpty();
    }

    @Test
    void paginaETamanhoInvalidosDao400ComOsCampos() throws Exception {
        Resposta resposta = cliente(ContasDoScript.MARIA).get(ROTA + "?pagina=0&tamanho=51&status=pendente");

        assertThat(resposta.status()).isEqualTo(400);
        JSONAssert.assertEquals("{\"codigo\":\"DADOS_INVALIDOS\",\"mensagem\":\"Confira os filtros.\",\"campos\":"
                + "{\"status\":\"Use PENDENTE, APROVADO ou RECUSADO.\",\"pagina\":\"A página começa em 1.\","
                + "\"tamanho\":\"O tamanho vai de 1 a 50.\"}}", resposta.corpo(), JSONCompareMode.STRICT);
    }

    @Test
    void osMateriaisBatemComOsDaBusca() throws Exception {
        Map<Integer, List<String>> daBusca = new HashMap<>();
        for (JsonNode ponto : JSON.readTree(cliente(null).get("/api/pontos?tamanho=50").corpo()).path("itens")) {
            daBusca.put(ponto.path("idPonto").asInt(), materiais(ponto));
        }

        JsonNode doAdmin = lista(cliente(ContasDoScript.ADMIN), ROTA + "?tamanho=50");

        assertThat(doAdmin.path("itens")).hasSize(13).allSatisfy(sugestao -> assertThat(materiais(sugestao))
                .as("ponto %s", sugestao.path("idPonto")).isNotEmpty()
                .isEqualTo(daBusca.get(sugestao.path("idPonto").asInt())));
    }

    @Test
    void umaPaginaRoda3ComandosSqlEAVazia1() throws Exception {
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        Statistics estatisticas = fabrica.unwrap(SessionFactory.class).getStatistics();

        estatisticas.clear();
        long inicio = System.nanoTime();
        assertThat(admin.get(ROTA).status()).isEqualTo(200);
        Duration tempo = Duration.ofNanos(System.nanoTime() - inicio);
        assertThat(estatisticas.getPrepareStatementCount()).as("contagem, página e materiais").isEqualTo(3);
        assertThat(tempo).isLessThan(Duration.ofSeconds(2));

        estatisticas.clear();
        assertThat(admin.get(ROTA + "?status=RECUSADO").status()).isEqualTo(200);
        assertThat(estatisticas.getPrepareStatementCount()).as("lista vazia").isEqualTo(1);
    }

    private int sugerir(ClienteDaApi cliente, String nome) throws Exception {
        Resposta resposta = cliente.post("/api/pontos", corpoDaSugestao(nome).toString());
        assertThat(resposta.status()).as(resposta.corpo()).isEqualTo(201);
        return JSON.readTree(resposta.corpo()).path("idPonto").asInt();
    }

    private static ObjectNode corpoDaSugestao(String nome) {
        ObjectNode corpo = JSON.createObjectNode().put("nome", nome).put("endereco", "Rua das Flores, 50")
                .put("idBairro", 20).put("horario", "8h às 18h").putNull("contato");
        corpo.putArray("categorias").add(6).add(2);
        return corpo;
    }

    private static JsonNode lista(ClienteDaApi cliente, String caminho) throws Exception {
        Resposta resposta = cliente.get(caminho);
        assertThat(resposta.status()).as("%s: %s", caminho, resposta.corpo()).isEqualTo(200);
        return JSON.readTree(resposta.corpo());
    }

    private static List<Integer> ids(JsonNode pagina) {
        List<Integer> ids = new ArrayList<>();
        pagina.path("itens").forEach(item -> ids.add(item.path("idPonto").asInt()));
        return ids;
    }

    private static List<String> statusDe(JsonNode pagina) {
        List<String> status = new ArrayList<>();
        pagina.path("itens").forEach(item -> status.add(item.path("status").asText()));
        return status;
    }

    private static List<String> materiais(JsonNode ponto) {
        List<String> materiais = new ArrayList<>();
        ponto.path("materiais")
                .forEach(m -> materiais.add(m.path("idCategoria").asInt() + " " + m.path("nome").asText()));
        return materiais;
    }

    private Integer idDe(String email) {
        return jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE email = ?", Integer.class, email);
    }

    private ClienteDaApi cliente(String email) {
        ClienteDaApi cliente = new ClienteDaApi(porta);
        if (email != null) {
            Resposta login = cliente.post("/api/auth/login", ContasDoScript.corpoDoLogin(email));
            assertThat(login.status()).as("login de %s", email).isEqualTo(200);
        }
        return cliente;
    }
}
