package br.com.ecoponto.categoria;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class CategoriasDoPainelIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ROTA = "/api/admin/categorias";
    private static final String REPETIDO = "Já existe uma categoria com este nome.";

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory fabrica;

    @Autowired
    private ContasTesteService contasTeste;

    private final Map<Integer, Map<String, Object>> doScript = new LinkedHashMap<>();
    private List<Map<String, Object>> vinculosAntes;

    private static boolean contasProntas;

    @BeforeEach
    void guardarAsCategorias() {
        if (!contasProntas) {
            contasTeste.refazerHashes(ContasDoScript.todas());
            contasProntas = true;
        }
        jdbc.queryForList("SELECT id_categoria, nome, descricao FROM categoria_residuo WHERE id_categoria <= 10")
                .forEach(linha -> doScript.put((Integer) linha.get("id_categoria"), linha));
        assertThat(doScript).hasSize(10);
        vinculosAntes = vinculos();
    }

    @AfterEach
    void devolverOScript() {
        jdbc.update("DELETE FROM categoria_residuo WHERE id_categoria > 10");
        doScript.keySet().forEach(id -> jdbc.update("UPDATE categoria_residuo SET nome = ? WHERE id_categoria = ?",
                "provisório " + id, id));
        doScript.forEach((id, linha) -> jdbc.update(
                "UPDATE categoria_residuo SET nome = ?, descricao = ? WHERE id_categoria = ?",
                linha.get("nome"), linha.get("descricao"), id));
        jdbc.update("UPDATE ponto_coleta SET status = 'APROVADO' WHERE id_ponto = 13");
        jdbc.update("UPDATE ponto_coleta SET status = 'PENDENTE' WHERE id_ponto IN (14, 15)");
        assertThat(vinculos()).as("os vínculos do script").isEqualTo(vinculosAntes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin-categorias", "admin-criar-categoria", "admin-editar-categoria",
        "admin-categoria-nome-repetido", "admin-categoria-dados-invalidos", "admin-categoria-nome-longo",
        "admin-categoria-nao-encontrada", "admin-categoria-id-invalido", "admin-categorias-tamanho-invalido"})
    void respondeComoOsExemplosDoContrato(String nome) throws Exception {
        Caso caso = Exemplos.caso(nome);
        for (Pedido pedido : caso.pedidos()) {
            String corpo = pedido.corpo() == null ? null : pedido.corpo().toString();
            Resposta resposta = cliente(pedido.sessao()).enviar(pedido.metodo(), pedido.caminho(), corpo);

            assertThat(resposta.status()).as("%s %s", pedido.metodo(), pedido.caminho()).isEqualTo(caso.status());
            JsonNode recebido = JSON.readTree(resposta.corpo());
            if (pedido.metodo().equals("POST") && caso.status() == 201) {
                assertThat(recebido.path("idCategoria").asInt()).isGreaterThan(10);
                ((ObjectNode) recebido).put("idCategoria", caso.corpo().path("idCategoria").asInt());
            }
            JSONAssert.assertEquals(caso.corpo().toString(), recebido.toString(), JSONCompareMode.STRICT);
        }
    }

    @Test
    void umaCategoriaNovaApareceComZeroPontos() throws Exception {
        Resposta criada = cliente(ContasDoScript.ADMIN).post(ROTA,
                "{\"nome\":\"Cápsulas de café\",\"descricao\":\"Cápsulas de alumínio e de plástico.\"}");
        assertThat(criada.status()).isEqualTo(201);
        int id = JSON.readTree(criada.corpo()).path("idCategoria").asInt();

        ClienteDaApi visitante = cliente(null);
        JsonNode publica = encontrar(JSON.readTree(visitante.get("/api/categorias").corpo()), id);
        assertThat(publica.path("nome").asText()).isEqualTo("Cápsulas de café");
        assertThat(publica.path("descricao").asText()).isEqualTo("Cápsulas de alumínio e de plástico.");
        JsonNode contagem = JSON.readTree(visitante.get("/api/pontos/contagem").corpo()).path("categorias");
        assertThat(encontrar(contagem, id).path("quantidade").asInt()).isZero();
        JsonNode doPainel = JSON.readTree(cliente(ContasDoScript.ADMIN).get(ROTA + "?tamanho=50").corpo());
        assertThat(doPainel.path("total").asInt()).isEqualTo(11);
        assertThat(encontrar(doPainel.path("itens"), id).path("pontos").asInt()).isZero();
        assertThat(JSON.readTree(cliente(ContasDoScript.ADMIN).get("/api/admin/resumo").corpo()).path("categorias")
                .asInt()).isEqualTo(11);
    }

    @Test
    void editarMudaNomeEDescricaoSemMudarOIdNemOsVinculos() throws Exception {
        ClienteDaApi visitante = cliente(null);
        String buscaAntes = visitante.get("/api/pontos?categoria=8&tamanho=50").corpo();
        List<Integer> pontosComOleo = idsDosPontos(JSON.readTree(buscaAntes));
        assertThat(pontosComOleo).isNotEmpty();

        Resposta editada = cliente(ContasDoScript.ADMIN).enviar("PUT", ROTA + "/8",
                "{\"nome\":\" Óleo usado \",\"descricao\":\"Frio, em garrafa PET.\"}");

        assertThat(editada.status()).as(editada.corpo()).isEqualTo(200);
        JSONAssert.assertEquals("{\"idCategoria\":8,\"nome\":\"Óleo usado\",\"descricao\":\"Frio, em garrafa PET.\","
                + "\"pontos\":2}", editada.corpo(), JSONCompareMode.STRICT);
        assertThat(jdbc.queryForMap("SELECT nome, descricao FROM categoria_residuo WHERE id_categoria = 8"))
                .containsEntry("nome", "Óleo usado").containsEntry("descricao", "Frio, em garrafa PET.");
        assertThat(vinculos()).isEqualTo(vinculosAntes);
        assertThat(idsDosPontos(JSON.readTree(visitante.get("/api/pontos?categoria=8&tamanho=50").corpo())))
                .isEqualTo(pontosComOleo);
        JsonNode detalhe = JSON.readTree(visitante.get("/api/pontos/" + pontosComOleo.get(0)).corpo());
        assertThat(encontrar(detalhe.path("materiais"), 8).path("nome").asText()).isEqualTo("Óleo usado");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categoria_residuo", Integer.class)).isEqualTo(10);
    }

    @Test
    void nomeRepetidoDa409EmQualquerForma() throws Exception {
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        String corpoDoErro = "{\"codigo\":\"NOME_REPETIDO\",\"mensagem\":\"" + REPETIDO + "\",\"campos\":{\"nome\":\""
                + REPETIDO + "\"}}";
        for (String repetido : List.of("Plástico", "plastico", "PLÁSTICO", "  Plástico  ", "papel E PAPELAO")) {
            String corpo = JSON.createObjectNode().put("nome", repetido).putNull("descricao").toString();

            Resposta nova = admin.post(ROTA, corpo);
            assertThat(nova.status()).as("POST %s", repetido).isEqualTo(409);
            JSONAssert.assertEquals(corpoDoErro, nova.corpo(), JSONCompareMode.STRICT);
            Resposta editada = admin.enviar("PUT", ROTA + "/8", corpo);
            assertThat(editada.status()).as("PUT %s", repetido).isEqualTo(409);
            JSONAssert.assertEquals(corpoDoErro, editada.corpo(), JSONCompareMode.STRICT);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categoria_residuo", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT nome FROM categoria_residuo WHERE id_categoria = 8", String.class))
                .isEqualTo(doScript.get(8).get("nome"));
    }

    @Test
    void aPropriaCategoriaPodeTrocarAsMaiusculasDoNome() {
        Resposta editada = cliente(ContasDoScript.ADMIN).enviar("PUT", ROTA + "/4",
                "{\"nome\":\"VIDRO\",\"descricao\":null}");

        assertThat(editada.status()).as(editada.corpo()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT nome FROM categoria_residuo WHERE id_categoria = 4", String.class))
                .isEqualTo("VIDRO");
    }

    @Test
    void nomeVazioOuLongoDa400ENaoGrava() throws Exception {
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        Map<String, String> casos = new LinkedHashMap<>();
        casos.put("{\"nome\":\"\",\"descricao\":null}", "{\"nome\":\"Informe o nome da categoria.\"}");
        casos.put("{\"nome\":\" \\u00a0 \",\"descricao\":null}", "{\"nome\":\"Informe o nome da categoria.\"}");
        casos.put("{\"descricao\":\"sem nome\"}", "{\"nome\":\"Informe o nome da categoria.\"}");
        casos.put("{\"nome\":\"" + "x".repeat(61) + "\"}", "{\"nome\":\"Use até 60 caracteres.\"}");
        casos.put("{\"nome\":\"Nova\",\"descricao\":\"" + "d".repeat(256) + "\"}",
                "{\"descricao\":\"Use até 255 caracteres.\"}");
        for (Map.Entry<String, String> caso : casos.entrySet()) {
            for (String[] pedido : new String[][] {{"POST", ROTA}, {"PUT", ROTA + "/8"}}) {
                Resposta resposta = admin.enviar(pedido[0], pedido[1], caso.getKey());

                assertThat(resposta.status()).as("%s %s", pedido[0], caso.getKey()).isEqualTo(400);
                JSONAssert.assertEquals("{\"codigo\":\"DADOS_INVALIDOS\","
                        + "\"mensagem\":\"Confira os campos destacados.\",\"campos\":" + caso.getValue() + "}",
                        resposta.corpo(), JSONCompareMode.STRICT);
            }
        }
        Resposta noLimite = admin.post(ROTA, JSON.createObjectNode().put("nome", "ç".repeat(60))
                .put("descricao", "é".repeat(255)).toString());
        assertThat(noLimite.status()).as("no limite").isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categoria_residuo", Integer.class)).isEqualTo(11);
        assertThat(jdbc.queryForMap("SELECT nome, descricao FROM categoria_residuo WHERE id_categoria = 8"))
                .isEqualTo(Map.of("nome", doScript.get(8).get("nome"), "descricao", doScript.get(8).get("descricao")));
    }

    @Test
    void aColunaPontosContaSoOsAprovados() throws Exception {
        assertThat(pontosDoPainel()).isEqualTo(aprovadosPorCategoria());
        Map<Integer, Integer> antes = pontosDoPainel();
        assertThat(antes.get(8)).as("o pendente 15 aceita óleo e não conta").isEqualTo(2);

        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        assertThat(admin.post("/api/admin/pontos/14/aprovar", null).status()).isEqualTo(200);
        jdbc.update("UPDATE ponto_coleta SET status = 'RECUSADO' WHERE id_ponto = 13");

        Map<Integer, Integer> depois = pontosDoPainel();
        assertThat(depois).isEqualTo(aprovadosPorCategoria());
        List<Integer> doLuizote = jdbc.queryForList(
                "SELECT id_categoria FROM ponto_categoria WHERE id_ponto = 13", Integer.class);
        for (int id = 1; id <= 10; id++) {
            int doDanielFonseca = List.of(1, 2, 3, 4, 10).contains(id) ? 1 : 0;
            int esperado = antes.get(id) + doDanielFonseca - (doLuizote.contains(id) ? 1 : 0);
            assertThat(depois.get(id)).as("categoria %d", id).isEqualTo(esperado);
        }
        assertThat(depois.get(8)).isEqualTo(2);
    }

    @Test
    void duasCriacoesComOMesmoNomeAoMesmoTempoSoUmaPassa() throws Exception {
        List<ClienteDaApi> admins = List.of(cliente(ContasDoScript.ADMIN), cliente(ContasDoScript.ADMIN));
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            for (int rodada = 1; rodada <= 10; rodada++) {
                jdbc.update("DELETE FROM categoria_residuo WHERE id_categoria > 10");
                String corpo = "{\"nome\":\"Corrida " + rodada + "\",\"descricao\":null}";
                CountDownLatch largada = new CountDownLatch(1);
                List<Future<Resposta>> respostas = new ArrayList<>();
                for (ClienteDaApi admin : admins) {
                    respostas.add(threads.submit(() -> {
                        largada.await();
                        return admin.post(ROTA, corpo);
                    }));
                }
                largada.countDown();
                List<Integer> status = new ArrayList<>();
                for (Future<Resposta> resposta : respostas) {
                    status.add(resposta.get(30, TimeUnit.SECONDS).status());
                }

                assertThat(status).as("rodada %d", rodada).containsExactlyInAnyOrder(201, 409);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categoria_residuo WHERE nome = ?", Integer.class,
                        "Corrida " + rodada)).isEqualTo(1);
            }
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void aListaRoda2ComandosSql() {
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        Statistics estatisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        assertThat(admin.get(ROTA + "?tamanho=50").status()).isEqualTo(200);

        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void visitanteEUsuarioComumNaoAcessam() {
        String corpo = "{\"nome\":\"Invasora\",\"descricao\":null}";
        for (String quem : new String[] {null, ContasDoScript.MARIA, ContasDoScript.JOAO}) {
            int esperado = quem == null ? 401 : 403;
            assertThat(cliente(quem).get(ROTA).status()).as("%s GET", quem).isEqualTo(esperado);
            assertThat(cliente(quem).post(ROTA, corpo).status()).as("%s POST", quem).isEqualTo(esperado);
            assertThat(cliente(quem).enviar("PUT", ROTA + "/8", corpo).status()).as("%s PUT", quem)
                    .isEqualTo(esperado);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categoria_residuo", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT nome FROM categoria_residuo WHERE id_categoria = 8", String.class))
                .isEqualTo(doScript.get(8).get("nome"));
    }

    private Map<Integer, Integer> pontosDoPainel() throws Exception {
        Map<Integer, Integer> pontos = new LinkedHashMap<>();
        JSON.readTree(cliente(ContasDoScript.ADMIN).get(ROTA + "?tamanho=50").corpo()).path("itens")
                .forEach(item -> pontos.put(item.path("idCategoria").asInt(), item.path("pontos").asInt()));
        return pontos;
    }

    private Map<Integer, Integer> aprovadosPorCategoria() {
        Map<Integer, Integer> contagem = new LinkedHashMap<>();
        jdbc.queryForList("SELECT c.id_categoria, COUNT(p.id_ponto) AS pontos FROM categoria_residuo c"
                + " LEFT JOIN ponto_categoria pc ON pc.id_categoria = c.id_categoria"
                + " LEFT JOIN ponto_coleta p ON p.id_ponto = pc.id_ponto AND p.status = 'APROVADO'"
                + " GROUP BY c.id_categoria ORDER BY c.id_categoria")
                .forEach(linha -> contagem.put((Integer) linha.get("id_categoria"),
                        ((Number) linha.get("pontos")).intValue()));
        return contagem;
    }

    private List<Map<String, Object>> vinculos() {
        return jdbc.queryForList("SELECT id_ponto, id_categoria FROM ponto_categoria ORDER BY id_ponto, id_categoria");
    }

    private static JsonNode encontrar(JsonNode lista, int idCategoria) {
        for (JsonNode item : lista) {
            if (item.path("idCategoria").asInt() == idCategoria) {
                return item;
            }
        }
        throw new AssertionError("a categoria " + idCategoria + " não está em " + lista);
    }

    private static List<Integer> idsDosPontos(JsonNode pagina) {
        List<Integer> ids = new ArrayList<>();
        pagina.path("itens").forEach(item -> ids.add(item.path("idPonto").asInt()));
        return ids;
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
