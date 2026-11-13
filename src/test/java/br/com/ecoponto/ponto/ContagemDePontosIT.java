package br.com.ecoponto.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import br.com.ecoponto.contrato.ClienteDaApi;
import br.com.ecoponto.contrato.ClienteDaApi.Resposta;
import br.com.ecoponto.contrato.Exemplos;
import br.com.ecoponto.contrato.Exemplos.Caso;
import br.com.ecoponto.contrato.Exemplos.Pedido;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
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
class ContagemDePontosIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<Integer> CATEGORIAS = IntStream.rangeClosed(1, 10).boxed().toList();
    private static final Duration RNF01 = Duration.ofSeconds(2);

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory fabrica;

    private final Map<Integer, String> statusOriginais = new LinkedHashMap<>();

    @AfterEach
    void devolverOsStatusDoScript() {
        statusOriginais.forEach((id, status) ->
                jdbc.update("UPDATE ponto_coleta SET status = ? WHERE id_ponto = ?", status, id));
        statusOriginais.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"contagem", "contagem-por-bairro", "contagem-bairro-invalido"})
    void respondeComoOsExemplosDoContrato(String nome) throws Exception {
        Caso caso = Exemplos.caso(nome);
        for (Pedido pedido : caso.pedidos()) {
            Resposta resposta = new ClienteDaApi(porta).get(pedido.caminho());

            assertThat(resposta.status()).as(pedido.caminho()).isEqualTo(caso.status());
            JSONAssert.assertEquals(caso.corpo().toString(), resposta.corpo(), JSONCompareMode.STRICT);
        }
    }

    @Test
    void semBairroOTotalE13EOsMateriaisSaoOsDoCartao() throws Exception {
        JsonNode contagem = contagem("");

        assertThat(contagem.path("total").asInt()).isEqualTo(13);
        assertThat(porNome(contagem)).containsExactly(
                entry("Papel e papelão", 3), entry("Plástico", 3), entry("Metal", 3), entry("Vidro", 3),
                entry("Eletrônicos", 2), entry("Pilhas e baterias", 6), entry("Lâmpadas", 3),
                entry("Óleo de cozinha usado", 2), entry("Medicamentos vencidos", 3), entry("Entulho e volumosos", 1));
        assertThat(soma(contagem)).as("um ponto aceita vários materiais").isEqualTo(29);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 7, 999})
    void bairroSemPontosAprovadosDaTudoZero(int bairro) throws Exception {
        JsonNode contagem = contagem("?bairro=" + bairro);

        assertThat(contagem.path("total").asInt()).as("total do bairro %d", bairro).isZero();
        assertThat(quantidades(contagem)).containsOnlyKeys(CATEGORIAS).allSatisfy((id, quantidade) ->
                assertThat(quantidade).as("categoria %d", id).isZero());
    }

    @Test
    void altoUmuaramaEOBairroDoCartao() {
        assertThat(jdbc.queryForObject("SELECT nome FROM bairro WHERE id_bairro = 2", String.class))
                .isEqualTo("Alto Umuarama");
        assertThat(jdbc.queryForObject("SELECT nome FROM bairro WHERE id_bairro = 7", String.class))
                .isEqualTo("Daniel Fonseca");
    }

    @Test
    void pendentesDoScriptNaoEntramNaConta() throws Exception {
        Map<Integer, Integer> semBairro = quantidades(contagem(""));

        assertThat(semBairro.get(10)).as("Entulho: o 13, sem o 14 pendente").isEqualTo(1);
        assertThat(semBairro.get(8)).as("Óleo: o 9 e o 10, sem o 15 pendente").isEqualTo(2);
        assertThat(contagem("?bairro=9").path("total").asInt()).as("Jardim Brasília só tem o 15").isZero();
    }

    @Test
    void mudarOStatusMudaAConta() throws Exception {
        mudarStatus(13, "RECUSADO");
        mudarStatus(14, "APROVADO");

        JsonNode semBairro = contagem("");
        assertThat(semBairro.path("total").asInt()).isEqualTo(13);
        assertThat(quantidades(semBairro)).contains(entry(1, 3), entry(2, 3), entry(3, 3), entry(4, 3), entry(10, 1));
        assertThat(contagem("?bairro=11").path("total").asInt()).as("Luizote: o 13 recusado").isZero();
        assertThat(contagem("?bairro=7").path("total").asInt()).as("Daniel Fonseca: o 14 aprovado").isEqualTo(1);

        mudarStatus(14, "PENDENTE");
        mudarStatus(12, "PENDENTE");
        JsonNode semDois = contagem("");
        assertThat(semDois.path("total").asInt()).isEqualTo(11);
        assertThat(quantidades(semDois)).contains(entry(1, 1), entry(2, 1), entry(3, 1), entry(4, 1), entry(10, 0));
    }

    @Test
    void aContagemBateComABuscaEmTodoBairroETodoMaterial() throws Exception {
        List<String> bairros = new ArrayList<>(List.of(""));
        IntStream.rangeClosed(1, 20).forEach(id -> bairros.add("bairro=" + id));
        for (String bairro : bairros) {
            JsonNode contagem = contagem(bairro.isEmpty() ? "" : "?" + bairro);
            String filtro = bairro.isEmpty() ? "" : "&" + bairro;

            assertThat(contagem.path("total").asInt()).as("total, %s", bairro)
                    .isEqualTo(totalDaBusca("/api/pontos?tamanho=1" + filtro));
            for (Map.Entry<Integer, Integer> categoria : quantidades(contagem).entrySet()) {
                assertThat(categoria.getValue()).as("categoria %d, %s", categoria.getKey(), bairro)
                        .isEqualTo(totalDaBusca("/api/pontos?tamanho=1&categoria=" + categoria.getKey() + filtro));
            }
        }
    }

    @Test
    void cadaContagemRoda2ComandosSql() throws Exception {
        Statistics estatisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
        for (String consulta : List.of("", "?bairro=4", "?bairro=2")) {
            estatisticas.clear();
            contagem(consulta);
            assertThat(estatisticas.getPrepareStatementCount()).as("total e categorias, %s", consulta).isEqualTo(2);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1 OR 1=1", "1;DROP TABLE ponto_coleta", "1'--", "1) OR (1=1", "-1", "0", "9999999999"})
    void textoMaliciosoDa400ENaoMexeNoBanco(String valor) {
        Resposta resposta = new ClienteDaApi(porta)
                .get("/api/pontos/contagem?bairro=" + URLEncoder.encode(valor, StandardCharsets.UTF_8));

        assertThat(resposta.status()).as(valor).isEqualTo(400);
        assertThat(resposta.corpo()).contains("\"bairro\":\"Use o id de um bairro.\"");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta", Integer.class)).isEqualTo(15);
    }

    @Test
    void cadaContagemRespondeEmMenosDe2Segundos() {
        for (String consulta : List.of("", "?bairro=4", "?bairro=2", "?bairro=20")) {
            long inicio = System.nanoTime();
            Resposta resposta = new ClienteDaApi(porta).get("/api/pontos/contagem" + consulta);
            Duration tempo = Duration.ofNanos(System.nanoTime() - inicio);

            assertThat(resposta.status()).isEqualTo(200);
            assertThat(tempo).as(consulta).isLessThan(RNF01);
        }
    }

    private void mudarStatus(int idPonto, String status) {
        statusOriginais.putIfAbsent(idPonto,
                jdbc.queryForObject("SELECT status FROM ponto_coleta WHERE id_ponto = ?", String.class, idPonto));
        jdbc.update("UPDATE ponto_coleta SET status = ? WHERE id_ponto = ?", status, idPonto);
    }

    private JsonNode contagem(String consulta) throws Exception {
        return corpo("/api/pontos/contagem" + consulta);
    }

    private int totalDaBusca(String caminho) throws Exception {
        return corpo(caminho).path("total").asInt();
    }

    private JsonNode corpo(String caminho) throws Exception {
        Resposta resposta = new ClienteDaApi(porta).get(caminho);
        assertThat(resposta.status()).as(caminho).isEqualTo(200);
        return JSON.readTree(resposta.corpo());
    }

    private static Map<Integer, Integer> quantidades(JsonNode contagem) {
        Map<Integer, Integer> quantidades = new LinkedHashMap<>();
        contagem.path("categorias").forEach(categoria ->
                quantidades.put(categoria.path("idCategoria").asInt(), categoria.path("quantidade").asInt()));
        assertThat(quantidades.keySet()).containsExactlyElementsOf(CATEGORIAS);
        return quantidades;
    }

    private Map<String, Integer> porNome(JsonNode contagem) {
        Map<Integer, String> nomes = new LinkedHashMap<>();
        jdbc.query("SELECT id_categoria, nome FROM categoria_residuo", linha -> {
            nomes.put(linha.getInt("id_categoria"), linha.getString("nome"));
        });
        Map<String, Integer> porNome = new LinkedHashMap<>();
        quantidades(contagem).forEach((id, quantidade) -> porNome.put(nomes.get(id), quantidade));
        return porNome;
    }

    private static int soma(JsonNode contagem) {
        return quantidades(contagem).values().stream().mapToInt(Integer::intValue).sum();
    }
}
