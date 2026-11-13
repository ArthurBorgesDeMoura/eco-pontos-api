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
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
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
class DetalheDoPontoIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory fabrica;

    @Autowired
    private ContasTesteService contasTeste;

    private final List<Object[]> originais = new ArrayList<>();

    private static boolean contasProntas;

    @BeforeEach
    void prepararAsContasDeTeste() {
        if (!contasProntas) {
            contasTeste.refazerHashes(ContasDoScript.todas());
            contasProntas = true;
        }
    }

    @AfterEach
    void devolverOsPontosDoScript() {
        originais.forEach(linha -> jdbc.update(
                "UPDATE ponto_coleta SET horario = ?, contato = ?, status = ? WHERE id_ponto = ?", linha));
        originais.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ponto-detalhe", "ponto-nao-encontrado", "ponto-pendente-para-admin", "ponto-id-invalido"})
    void respondeComoOsExemplosDoContrato(String nome) throws Exception {
        Caso caso = Exemplos.caso(nome);
        for (Pedido pedido : caso.pedidos()) {
            Resposta resposta = cliente(pedido.sessao()).get(pedido.caminho());

            assertThat(resposta.status()).as("%s, %s", pedido.caminho(), pedido.sessao()).isEqualTo(caso.status());
            JSONAssert.assertEquals(caso.corpo().toString(), resposta.corpo(), JSONCompareMode.STRICT);
        }
    }

    @Test
    void oLuizoteDeFreitasTrazTudoEOContatoNull() throws Exception {
        JsonNode luizote = detalheComo(null, 13);

        assertThat(luizote.path("endereco").asText()).isEqualTo("Rua Wilson Gonçalves de Souza, 10");
        assertThat(luizote.path("bairro").path("nome").asText()).isEqualTo("Luizote de Freitas");
        assertThat(luizote.path("horario").asText()).isEqualTo("7h às 19h, todos os dias");
        assertThat(luizote.has("contato")).isTrue();
        assertThat(luizote.path("contato").isNull()).isTrue();
        assertThat(luizote.path("materiais")).hasSize(5).allSatisfy(material -> {
            String descricao = jdbc.queryForObject("SELECT descricao FROM categoria_residuo WHERE id_categoria = ?",
                    String.class, material.path("idCategoria").asInt());
            assertThat(material.path("descricao").asText()).isEqualTo(descricao).isNotBlank();
        });
    }

    @Test
    void horarioEContatoVaziosNoBancoVoltamComoNull() throws Exception {
        guardar(4);
        jdbc.update("UPDATE ponto_coleta SET horario = '', contato = '   ' WHERE id_ponto = 4");

        JsonNode bretas = detalheComo(null, 4);

        assertThat(bretas.has("horario")).isTrue();
        assertThat(bretas.path("horario").isNull()).isTrue();
        assertThat(bretas.has("contato")).isTrue();
        assertThat(bretas.path("contato").isNull()).isTrue();
    }

    @Test
    void nenhumPontoDoScriptTrazTextoVazioNemNaoInformado() throws Exception {
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        for (int id : IntStream.rangeClosed(1, 15).toArray()) {
            JsonNode ponto = detalhe(admin, id);
            for (String campo : List.of("horario", "contato")) {
                JsonNode valor = ponto.path(campo);
                assertThat(ponto.has(campo)).as("%s do ponto %d", campo, id).isTrue();
                if (!valor.isNull()) {
                    assertThat(valor.asText()).as("%s do ponto %d", campo, id).isNotBlank()
                            .doesNotContainIgnoringCase("não informado");
                }
            }
        }
        assertThat(detalheComo(null, 9).path("contato").asText()).as("o Dmae tem contato").isEqualTo("(34) 3228-7744");
    }

    @Test
    void naoAprovadoDaOMesmo404QueOInexistenteParaQuemNaoEAdmin() {
        String inexistente = cliente(null).get("/api/pontos/999").corpo();
        for (String quem : new String[] {null, ContasDoScript.MARIA, ContasDoScript.JOAO}) {
            for (int id : new int[] {14, 15, 999999999}) {
                Resposta resposta = cliente(quem).get("/api/pontos/" + id);

                assertThat(resposta.status()).as("ponto %d para %s", id, quem).isEqualTo(404);
                assertThat(resposta.corpo()).as("ponto %d para %s", id, quem).isEqualTo(inexistente);
            }
        }
    }

    @Test
    void oRecusadoTambemDa404ParaQuemNaoEAdminEAbreParaOAdmin() throws Exception {
        guardar(13);
        jdbc.update("UPDATE ponto_coleta SET status = 'RECUSADO' WHERE id_ponto = 13");

        assertThat(cliente(null).get("/api/pontos/13").status()).isEqualTo(404);
        assertThat(cliente(ContasDoScript.MARIA).get("/api/pontos/13").status()).isEqualTo(404);
        assertThat(detalheComo(ContasDoScript.ADMIN, 13).path("nome").asText())
                .isEqualTo("Ecoponto Luizote de Freitas");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "9999999999", "1%20OR%201=1", "%27", "1'--", "0x0D", "+13", "%2013"})
    void idQueNaoEPositivoDa400ENaoMexeNoBanco(String id) {
        Resposta resposta = cliente(null).get("/api/pontos/" + id);

        assertThat(resposta.status()).as(id).isEqualTo(400);
        assertThat(resposta.corpo())
                .isEqualTo("{\"codigo\":\"DADOS_INVALIDOS\",\"mensagem\":\"Use o id de um ponto.\"}");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta", Integer.class)).isEqualTo(15);
    }

    @Test
    void idComPontoEVirgulaNemChegaARota() {
        Resposta resposta = cliente(null).get("/api/pontos/1;DROP%20TABLE%20ponto_coleta");

        assertThat(resposta.status()).isEqualTo(400);
        assertThat(resposta.corpo()).contains("\"codigo\":\"DADOS_INVALIDOS\"");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta", Integer.class)).isEqualTo(15);
    }

    @Test
    void oAdminAbreOsPendentes() throws Exception {
        assertThat(detalheComo(ContasDoScript.ADMIN, 14).path("nome").asText()).isEqualTo("Ecoponto Daniel Fonseca");
        JsonNode coru = detalheComo(ContasDoScript.ADMIN, 15);
        assertThat(coru.path("nome").asText()).isEqualTo("Cooperativa dos Recicladores de Uberlândia (CORU)");
        assertThat(coru.path("contato").asText()).isEqualTo("(34) 99798-1494");
        assertThat(coru.path("horario").isNull()).isTrue();
        assertThat(coru.path("materiais")).singleElement()
                .satisfies(material -> assertThat(material.path("idCategoria").asInt()).isEqualTo(8));
        assertThat(cliente(ContasDoScript.ADMIN).get("/api/pontos/999").status()).isEqualTo(404);
    }

    @Test
    void oDetalheRoda2ComandosSqlEO404So1() throws Exception {
        Statistics estatisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
        ClienteDaApi visitante = cliente(null);

        estatisticas.clear();
        assertThat(visitante.get("/api/pontos/13").status()).isEqualTo(200);
        assertThat(estatisticas.getPrepareStatementCount()).as("ponto e materiais").isEqualTo(2);

        estatisticas.clear();
        assertThat(visitante.get("/api/pontos/14").status()).isEqualTo(404);
        assertThat(estatisticas.getPrepareStatementCount()).as("pendente para visitante").isEqualTo(1);
    }

    @Test
    void oDetalheBateComABusca() throws Exception {
        ClienteDaApi visitante = cliente(null);
        Resposta busca = visitante.get("/api/pontos?tamanho=50");
        for (JsonNode resumo : JSON.readTree(busca.corpo()).path("itens")) {
            JsonNode detalhe = detalhe(visitante, resumo.path("idPonto").asInt());

            assertThat(detalhe.path("nome")).isEqualTo(resumo.path("nome"));
            assertThat(detalhe.path("endereco")).isEqualTo(resumo.path("endereco"));
            assertThat(detalhe.path("bairro")).isEqualTo(resumo.path("bairro"));
            assertThat(materiais(detalhe)).as("ponto %s", resumo.path("idPonto")).isEqualTo(materiais(resumo));
        }
    }

    private static List<String> materiais(JsonNode ponto) {
        List<String> materiais = new ArrayList<>();
        ponto.path("materiais").forEach(m -> materiais.add(m.path("idCategoria") + " " + m.path("nome")));
        return materiais;
    }

    private void guardar(int idPonto) {
        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT horario, contato, status FROM ponto_coleta WHERE id_ponto = ?", idPonto);
        originais.add(new Object[] {linha.get("horario"), linha.get("contato"), linha.get("status"), idPonto});
    }

    private JsonNode detalheComo(String quem, int idPonto) throws Exception {
        return detalhe(cliente(quem), idPonto);
    }

    private static JsonNode detalhe(ClienteDaApi cliente, int idPonto) throws Exception {
        Resposta resposta = cliente.get("/api/pontos/" + idPonto);
        assertThat(resposta.status()).as("ponto %d", idPonto).isEqualTo(200);
        return JSON.readTree(resposta.corpo());
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
