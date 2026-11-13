package br.com.ecoponto.ponto;

import static org.assertj.core.api.Assertions.assertThat;

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
class BuscaDePontosIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<Integer> APROVADOS_NA_ORDEM = List.of(2, 10, 3, 4, 8, 1, 13, 6, 5, 12, 7, 9, 11);
    private static final Duration RNF01 = Duration.ofSeconds(2);

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory fabrica;

    private final Map<Integer, String> nomesOriginais = new LinkedHashMap<>();

    @AfterEach
    void devolverOsNomesDoScript() {
        nomesOriginais.forEach((id, nome) ->
                jdbc.update("UPDATE ponto_coleta SET nome = ? WHERE id_ponto = ?", nome, id));
        nomesOriginais.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pontos-primeira-pagina", "pontos-segunda-pagina"})
    void semFiltroSaemOsAprovadosComoNoContrato(String nome) throws Exception {
        conferirComOExemplo(Exemplos.caso(nome));
    }

    @Test
    void semFiltroSaemOsTrezeAprovadosENenhumPendente() throws Exception {
        assertThat(ids("")).isEqualTo(APROVADOS_NA_ORDEM).doesNotContain(14, 15);
        assertThat(corpo("/api/pontos?tamanho=50").path("total").asInt()).isEqualTo(13);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pontos-por-bairro-e-material", "pontos-lista-vazia"})
    void bairroEMaterialSaemComoNoContrato(String nome) throws Exception {
        conferirComOExemplo(Exemplos.caso(nome));
    }

    @Test
    void bairroEMaterialJuntosDaoAIntersecaoEOPendenteNuncaEntra() throws Exception {
        assertThat(ids("bairro=4")).as("Centro").containsExactly(4, 8);
        assertThat(ids("categoria=7")).as("Lâmpadas").containsExactly(4, 6, 5);
        assertThat(ids("bairro=4&categoria=7")).as("Centro e Lâmpadas").containsExactly(4);
        assertThat(ids("categoria=10")).as("Entulho: o 14 é pendente").containsExactly(13);
        assertThat(ids("categoria=8")).as("Óleo: o 15 é pendente").containsExactly(10, 9);
        assertThat(ids("bairro=7")).as("Daniel Fonseca só tem o 14, pendente").isEmpty();
        assertThat(ids("bairro=999")).as("bairro que não existe").isEmpty();
        assertThat(ids("bairro=4&categoria=1")).as("Centro e Papel: nada em comum").isEmpty();
    }

    @Test
    void umaPaginaDe7PontosRodaSo3ComandosSql() throws Exception {
        Statistics estatisticas = fabrica.unwrap(SessionFactory.class).getStatistics();

        estatisticas.clear();
        JsonNode pagina = corpo("/api/pontos");
        long comandos = estatisticas.getPrepareStatementCount();

        assertThat(pagina.path("itens")).hasSize(7);
        pagina.path("itens").forEach(ponto -> assertThat(ponto.path("materiais")).isNotEmpty());
        assertThat(comandos).as("contagem, página e materiais").isEqualTo(3);

        estatisticas.clear();
        corpo("/api/pontos?tamanho=50");
        assertThat(estatisticas.getPrepareStatementCount()).as("13 pontos, as mesmas 3").isEqualTo(3);

        estatisticas.clear();
        corpo("/api/pontos?bairro=2");
        assertThat(estatisticas.getPrepareStatementCount()).as("lista vazia: só a contagem").isEqualTo(1);
    }

    @Test
    void parametrosInvalidosDao400ComoOContrato() throws Exception {
        conferirComOExemplo(Exemplos.caso("pontos-filtros-invalidos"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1 OR 1=1", "1;DROP TABLE ponto_coleta", "1'--", "1) OR (1=1", "-1", "0", "9999999999"})
    void textoMaliciosoDa400ENaoMexeNoBanco(String valor) {
        String codificado = URLEncoder.encode(valor, StandardCharsets.UTF_8);
        for (String parametro : List.of("bairro", "categoria")) {
            Resposta resposta = new ClienteDaApi(porta).get("/api/pontos?" + parametro + "=" + codificado);

            assertThat(resposta.status()).as("%s=%s", parametro, valor).isEqualTo(400);
            assertThat(resposta.corpo()).contains("\"codigo\":\"DADOS_INVALIDOS\"");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta", Integer.class)).isEqualTo(15);
    }

    @Test
    void asPaginasSeguidasTrazemTodosNaMesmaOrdemSemRepetirNemPular() throws Exception {
        for (int tamanho = 1; tamanho <= 13; tamanho++) {
            assertThat(percorrer("", tamanho)).as("tamanho %d", tamanho).isEqualTo(APROVADOS_NA_ORDEM);
        }
        assertThat(percorrer("categoria=6", 2)).isEqualTo(ids("categoria=6"));
    }

    @Test
    void oEmpateEmBairroENomeEDesfeitoPeloId() throws Exception {
        String nomeDoDois = jdbc.queryForObject("SELECT nome FROM ponto_coleta WHERE id_ponto = 2", String.class);
        nomesOriginais.put(10, jdbc.queryForObject("SELECT nome FROM ponto_coleta WHERE id_ponto = 10", String.class));
        jdbc.update("UPDATE ponto_coleta SET nome = ? WHERE id_ponto = 10", nomeDoDois);

        assertThat(percorrer("bairro=1", 1)).containsExactly(2, 10);
        assertThat(percorrer("", 1)).startsWith(2, 10).hasSize(13).doesNotHaveDuplicates();
    }

    @Test
    void paginaDepoisDoFimEhVaziaCom200EOMesmoTotal() throws Exception {
        for (String pagina : List.of("3", "999999999")) {
            JsonNode corpo = corpo("/api/pontos?pagina=" + pagina);
            assertThat(corpo.path("itens")).isEmpty();
            assertThat(corpo.path("total").asInt()).isEqualTo(13);
        }
    }

    @Test
    void cadaBuscaRespondeEmMenosDe2Segundos() throws Exception {
        for (String consulta : List.of("", "?bairro=4", "?categoria=6", "?bairro=4&categoria=7", "?pagina=2")) {
            long inicio = System.nanoTime();
            Resposta resposta = new ClienteDaApi(porta).get("/api/pontos" + consulta);
            Duration tempo = Duration.ofNanos(System.nanoTime() - inicio);

            assertThat(resposta.status()).isEqualTo(200);
            assertThat(tempo).as(consulta).isLessThan(RNF01);
        }
    }

    private void conferirComOExemplo(Caso caso) throws Exception {
        for (Pedido pedido : caso.pedidos()) {
            Resposta resposta = new ClienteDaApi(porta).get(pedido.caminho());

            assertThat(resposta.status()).as(pedido.caminho()).isEqualTo(caso.status());
            JSONAssert.assertEquals(caso.corpo().toString(), resposta.corpo(), JSONCompareMode.STRICT);
        }
    }

    private List<Integer> ids(String filtros) throws Exception {
        JsonNode corpo = corpo("/api/pontos?tamanho=50" + (filtros.isEmpty() ? "" : "&" + filtros));
        assertThat(corpo.path("total").asInt()).isEqualTo(corpo.path("itens").size());
        return idsDos(corpo);
    }

    private List<Integer> percorrer(String filtros, int tamanho) throws Exception {
        List<Integer> juntados = new ArrayList<>();
        Integer total = null;
        for (int pagina = 1; pagina <= 20; pagina++) {
            JsonNode corpo = corpo("/api/pontos?tamanho=" + tamanho + "&pagina=" + pagina
                    + (filtros.isEmpty() ? "" : "&" + filtros));
            assertThat(corpo.path("itens").size()).isLessThanOrEqualTo(tamanho);
            if (total == null) {
                total = corpo.path("total").asInt();
            }
            assertThat(corpo.path("total").asInt()).as("total na página %d", pagina).isEqualTo(total);
            if (corpo.path("itens").isEmpty()) {
                assertThat(juntados).hasSize(total);
                return juntados;
            }
            juntados.addAll(idsDos(corpo));
        }
        throw new AssertionError("a lista não terminou em 20 páginas");
    }

    private JsonNode corpo(String caminho) throws Exception {
        Resposta resposta = new ClienteDaApi(porta).get(caminho);
        assertThat(resposta.status()).as(caminho).isEqualTo(200);
        return JSON.readTree(resposta.corpo());
    }

    private static List<Integer> idsDos(JsonNode corpo) {
        List<Integer> ids = new ArrayList<>();
        corpo.path("itens").forEach(ponto -> ids.add(ponto.path("idPonto").asInt()));
        return ids;
    }
}
