package br.com.ecoponto.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import br.com.ecoponto.categoria.Categoria;
import br.com.ecoponto.categoria.CategoriaRepositorio;
import br.com.ecoponto.contrato.ClienteDaApi;
import br.com.ecoponto.contrato.ClienteDaApi.Resposta;
import br.com.ecoponto.contrato.Exemplos;
import br.com.ecoponto.contrato.Exemplos.Caso;
import br.com.ecoponto.preparacao.ContasTesteService;
import br.com.ecoponto.seguranca.ContasDoScript;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=warn"
})
class SugestaoDePontosIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CONFIRA = "Confira os campos destacados.";
    private static final String MATERIAL_DA_LISTA = "Escolha só materiais da lista, sem repetir.";

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory fabrica;

    @Autowired
    private EntityManager gerente;

    @Autowired
    private ContasTesteService contasTeste;

    @SpyBean
    private CategoriaRepositorio categorias;

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

    @Test
    void umaSugestaoValidaCriaUmPontoPendenteDoUsuarioLogado() throws Exception {
        Caso caso = Exemplos.caso("sugerir-criado");
        LocalDateTime antes = agoraNoBanco();

        Resposta resposta = cliente(ContasDoScript.MARIA).post("/api/pontos", caso.pedidos().get(0).corpo().toString());

        assertThat(resposta.status()).as(resposta.corpo()).isEqualTo(201);
        ObjectNode criado = (ObjectNode) JSON.readTree(resposta.corpo());
        int id = criado.path("idPonto").asInt();
        assertThat(id).isGreaterThan(maiorIdAntes);
        criado.put("idPonto", 16);
        JSONAssert.assertEquals(caso.corpo().toString(), criado.toString(), JSONCompareMode.STRICT);

        Map<String, Object> linha = jdbc.queryForMap("SELECT nome, endereco, id_bairro, horario, contato, status,"
                + " id_usuario FROM ponto_coleta WHERE id_ponto = ?", id);
        assertThat(linha).containsEntry("nome", "Mercado do Bairro").containsEntry("endereco", "Rua das Flores, 50")
                .containsEntry("id_bairro", 20).containsEntry("horario", "8h às 18h").containsEntry("contato", null)
                .containsEntry("status", "PENDENTE").containsEntry("id_usuario", idDe(ContasDoScript.MARIA));
        assertThat(dataDeCadastro(id)).as("a data é a do banco").isBetween(antes.minusSeconds(1), agoraNoBanco());
        assertThat(materiaisNoBanco(id)).containsExactly(2, 6);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sugerir-dados-invalidos", "sugerir-bairro-e-materiais-invalidos"})
    void osCasosDeErroSaemComoOContratoENaoGravam(String nome) throws Exception {
        Caso caso = Exemplos.caso(nome);

        Resposta resposta = cliente(ContasDoScript.MARIA).post("/api/pontos", caso.pedidos().get(0).corpo().toString());

        assertThat(resposta.status()).isEqualTo(caso.status());
        JSONAssert.assertEquals(caso.corpo().toString(), resposta.corpo(), JSONCompareMode.STRICT);
        assertNadaFoiGravado();
    }

    @Test
    void semLoginDa401ESemTokenCsrfDa403() throws Exception {
        String corpo = Exemplos.caso("sugerir-criado").pedidos().get(0).corpo().toString();

        Resposta visitante = cliente(null).post("/api/pontos", corpo);
        assertThat(visitante.status()).isEqualTo(401);
        JSONAssert.assertEquals(Exemplos.caso("sem-login").corpo().toString(), visitante.corpo(),
                JSONCompareMode.STRICT);

        Resposta semToken = cliente(ContasDoScript.MARIA).semTokenCsrf().post("/api/pontos", corpo);
        assertThat(semToken.status()).isEqualTo(403);
        assertNadaFoiGravado();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entradasInvalidas")
    void entradaInvalidaDa400NoCampoENaoGrava(String descricao, Consumer<ObjectNode> mudar, String campo,
            String mensagem) throws Exception {
        ObjectNode corpo = corpoValido();
        mudar.accept(corpo);

        Resposta resposta = cliente(ContasDoScript.MARIA).post("/api/pontos", corpo.toString());

        assertThat(resposta.status()).as(resposta.corpo()).isEqualTo(400);
        JSONAssert.assertEquals(JSON.createObjectNode().put("codigo", "DADOS_INVALIDOS").put("mensagem", CONFIRA)
                .set("campos", JSON.createObjectNode().put(campo, mensagem)).toString(), resposta.corpo(),
                JSONCompareMode.STRICT);
        assertNadaFoiGravado();
    }

    static Stream<Arguments> entradasInvalidas() {
        return Stream.of(
                invalida("sem materiais", c -> c.putArray("categorias"), "categorias",
                        "Escolha pelo menos um material."),
                invalida("sem a lista de materiais", c -> c.remove("categorias"), "categorias",
                        "Escolha pelo menos um material."),
                invalida("material que não existe", c -> c.putArray("categorias").add(2).add(99), "categorias",
                        MATERIAL_DA_LISTA),
                invalida("material 0", c -> c.putArray("categorias").add(0), "categorias", MATERIAL_DA_LISTA),
                invalida("material repetido", c -> c.putArray("categorias").add(2).add(2), "categorias",
                        MATERIAL_DA_LISTA),
                invalida("material null", c -> c.putArray("categorias").addNull(), "categorias", MATERIAL_DA_LISTA),
                invalida("101 materiais", c -> {
                    ArrayNode lista = c.putArray("categorias");
                    IntStream.rangeClosed(1, 101).forEach(lista::add);
                }, "categorias", MATERIAL_DA_LISTA),
                invalida("bairro que não existe", c -> c.put("idBairro", 99), "idBairro",
                        "Escolha um bairro da lista."),
                invalida("bairro 0", c -> c.put("idBairro", 0), "idBairro", "Escolha um bairro da lista."),
                invalida("bairro negativo", c -> c.put("idBairro", -20), "idBairro", "Escolha um bairro da lista."),
                invalida("sem bairro", c -> c.remove("idBairro"), "idBairro", "Escolha o bairro."),
                invalida("bairro em texto", c -> c.put("idBairro", "Tibery"), "idBairro", "Valor inválido."),
                invalida("nome com 121", c -> c.put("nome", "n".repeat(121)), "nome", "Use até 120 caracteres."),
                invalida("endereço com 201", c -> c.put("endereco", "e".repeat(201)), "endereco",
                        "Use até 200 caracteres."),
                invalida("horário com 101", c -> c.put("horario", "h".repeat(101)), "horario",
                        "Use até 100 caracteres."),
                invalida("contato com 101", c -> c.put("contato", "c".repeat(101)), "contato",
                        "Use até 100 caracteres."),
                invalida("nome só de espaços", c -> c.put("nome", "  \t "), "nome", "Informe o nome do ponto."),
                invalida("sem nome", c -> c.remove("nome"), "nome", "Informe o nome do ponto."),
                invalida("endereço só de espaços", c -> c.put("endereco", "   "), "endereco", "Informe o endereço."));
    }

    @Test
    void textoNoLimiteEGravadoInteiro() throws Exception {
        ObjectNode corpo = corpoValido().put("nome", "Ç".repeat(120)).put("endereco", "é".repeat(200))
                .put("horario", "ã".repeat(100)).put("contato", "ü".repeat(100));

        int id = sugerir(ContasDoScript.MARIA, corpo).path("idPonto").asInt();

        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT nome, endereco, horario, contato FROM ponto_coleta WHERE id_ponto = ?", id);
        assertThat(linha).containsEntry("nome", "Ç".repeat(120)).containsEntry("endereco", "é".repeat(200))
                .containsEntry("horario", "ã".repeat(100)).containsEntry("contato", "ü".repeat(100));
    }

    @Test
    void textosVaoSemAsPontasEOpcionaisVaziosViramNull() throws Exception {
        ObjectNode corpo = corpoValido().put("nome", "  Mercado do Bairro ").put("endereco", "\tRua 1 ")
                .put("horario", "   ").put("contato", "");

        JsonNode criado = sugerir(ContasDoScript.MARIA, corpo);

        assertThat(criado.path("nome").asText()).isEqualTo("Mercado do Bairro");
        assertThat(criado.path("horario").isNull()).isTrue();
        assertThat(criado.path("contato").isNull()).isTrue();
        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT nome, endereco, horario, contato FROM ponto_coleta WHERE id_ponto = ?",
                criado.path("idPonto").asInt());
        assertThat(linha).containsEntry("nome", "Mercado do Bairro").containsEntry("endereco", "Rua 1")
                .containsEntry("horario", null).containsEntry("contato", null);
    }

    @Test
    void textoComSqlEGravadoComoTexto() throws Exception {
        String nome = "x'); DELETE FROM ponto_coleta; --";
        int pontosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta", Integer.class);

        int id = sugerir(ContasDoScript.MARIA, corpoValido().put("nome", nome)).path("idPonto").asInt();

        assertThat(jdbc.queryForObject("SELECT nome FROM ponto_coleta WHERE id_ponto = ?", String.class, id))
                .isEqualTo(nome);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta", Integer.class)).isEqualTo(pontosAntes + 1);
    }

    @Test
    void statusEAutorDoCorpoNaoValem() throws Exception {
        Map<String, Object> pontoUmAntes = jdbc.queryForMap("SELECT * FROM ponto_coleta WHERE id_ponto = 1");
        ObjectNode corpo = corpoValido().put("status", "APROVADO").put("idUsuario", 1).put("idPonto", 1)
                .put("perfil", "ADMIN").put("dataCadastro", "2000-01-01T00:00:00");
        corpo.putObject("autor").put("idUsuario", 1);

        for (String quem : List.of(ContasDoScript.MARIA, ContasDoScript.JOAO, ContasDoScript.ADMIN)) {
            JsonNode criado = sugerir(quem, corpo);
            int id = criado.path("idPonto").asInt();

            assertThat(criado.path("status").asText()).as(quem).isEqualTo("PENDENTE");
            assertThat(id).as(quem).isGreaterThan(maiorIdAntes);
            Map<String, Object> linha = jdbc.queryForMap(
                    "SELECT status, id_usuario FROM ponto_coleta WHERE id_ponto = ?", id);
            assertThat(linha).as(quem).containsEntry("status", "PENDENTE").containsEntry("id_usuario", idDe(quem));
            assertThat(dataDeCadastro(id).getYear()).as(quem).isGreaterThan(2000);
        }
        assertThat(jdbc.queryForMap("SELECT * FROM ponto_coleta WHERE id_ponto = 1")).isEqualTo(pontoUmAntes);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta WHERE status = 'APROVADO'", Integer.class))
                .isEqualTo(13);
    }

    @Test
    void seUmVinculoFalharNenhumPontoFicaGravado() throws Exception {
        doAnswer(pedido -> List.of(gerente.find(Categoria.class, 2), gerente.getReference(Categoria.class, 999)))
                .when(categorias).findAllById(any());
        long proximoIdAntes = proximoId();

        Resposta resposta = cliente(ContasDoScript.MARIA).post("/api/pontos", corpoValido().toString());

        assertThat(resposta.status()).as(resposta.corpo()).isEqualTo(500);
        assertThat(JSON.readTree(resposta.corpo()).path("codigo").asText()).isEqualTo("ERRO_INTERNO");
        assertThat(proximoId()).as("o ponto chegou a ser inserido").isGreaterThan(proximoIdAntes);
        assertNadaFoiGravado();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta WHERE nome = 'Mercado do Bairro'",
                Integer.class)).isZero();
    }

    @Test
    void oPontoNovoNaoApareceNaBuscaAntesDaAprovacao() throws Exception {
        List<String> publicas = List.of("/api/pontos?tamanho=50", "/api/pontos?bairro=20&tamanho=50",
                "/api/pontos?categoria=6&tamanho=50", "/api/pontos?bairro=20&categoria=2",
                "/api/pontos/contagem", "/api/pontos/contagem?bairro=20");
        ClienteDaApi visitante = cliente(null);
        List<String> antes = publicas.stream().map(rota -> visitante.get(rota).corpo()).toList();

        int id = sugerir(ContasDoScript.MARIA, corpoValido()).path("idPonto").asInt();

        assertThat(publicas.stream().map(rota -> visitante.get(rota).corpo()).toList()).isEqualTo(antes);
        assertThat(JSON.readTree(visitante.get("/api/pontos?tamanho=50").corpo()).path("total").asInt())
                .isEqualTo(13);
        assertThat(visitante.get("/api/pontos/" + id).status()).isEqualTo(404);
        assertThat(cliente(ContasDoScript.MARIA).get("/api/pontos/" + id).status()).as("nem para a autora")
                .isEqualTo(404);
        Resposta doAdmin = cliente(ContasDoScript.ADMIN).get("/api/pontos/" + id);
        assertThat(doAdmin.status()).isEqualTo(200);
        assertThat(JSON.readTree(doAdmin.corpo()).path("nome").asText()).isEqualTo("Mercado do Bairro");
    }

    @Test
    void sugestaoRepetidaNaoEBloqueada() throws Exception {
        int primeiro = sugerir(ContasDoScript.MARIA, corpoValido()).path("idPonto").asInt();
        int segundo = sugerir(ContasDoScript.MARIA, corpoValido()).path("idPonto").asInt();

        assertThat(segundo).isGreaterThan(primeiro);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta WHERE nome = 'Mercado do Bairro'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void aSugestaoLeBairroEMateriaisEGravaPontoEVinculos() throws Exception {
        ClienteDaApi maria = cliente(ContasDoScript.MARIA);
        Statistics estatisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();

        long inicio = System.nanoTime();
        Resposta resposta = maria.post("/api/pontos", corpoValido().toString());
        Duration tempo = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(resposta.status()).isEqualTo(201);
        assertThat(estatisticas.getPrepareStatementCount()).as("2 leituras, 1 ponto e 2 vínculos").isEqualTo(5);
        assertThat(tempo).isLessThan(Duration.ofSeconds(2));
    }

    private static Arguments invalida(String descricao, Consumer<ObjectNode> mudar, String campo, String mensagem) {
        return Arguments.of(descricao, mudar, campo, mensagem);
    }

    private static ObjectNode corpoValido() {
        ObjectNode corpo = JSON.createObjectNode().put("nome", "Mercado do Bairro")
                .put("endereco", "Rua das Flores, 50").put("idBairro", 20).put("horario", "8h às 18h")
                .putNull("contato");
        corpo.putArray("categorias").add(6).add(2);
        return corpo;
    }

    private JsonNode sugerir(String quem, ObjectNode corpo) throws Exception {
        Resposta resposta = cliente(quem).post("/api/pontos", corpo.toString());
        assertThat(resposta.status()).as("%s: %s", quem, resposta.corpo()).isEqualTo(201);
        return JSON.readTree(resposta.corpo());
    }

    private void assertNadaFoiGravado() {
        assertThat(jdbc.queryForObject("SELECT COALESCE(MAX(id_ponto), 0) FROM ponto_coleta", Integer.class))
                .as("nenhum ponto novo").isEqualTo(maiorIdAntes);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_categoria WHERE id_ponto > ?", Integer.class,
                maiorIdAntes)).as("nenhum vínculo novo").isZero();
    }

    private List<Integer> materiaisNoBanco(int idPonto) {
        return jdbc.queryForList("SELECT id_categoria FROM ponto_categoria WHERE id_ponto = ? ORDER BY id_categoria",
                Integer.class, idPonto);
    }

    private Integer idDe(String email) {
        return jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE email = ?", Integer.class, email);
    }

    private LocalDateTime dataDeCadastro(int idPonto) {
        return jdbc.queryForObject("SELECT data_cadastro FROM ponto_coleta WHERE id_ponto = ?", LocalDateTime.class,
                idPonto);
    }

    private LocalDateTime agoraNoBanco() {
        return jdbc.queryForObject("SELECT NOW()", LocalDateTime.class);
    }

    private long proximoId() {
        return jdbc.queryForObject("SELECT AUTO_INCREMENT FROM information_schema.TABLES"
                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ponto_coleta'", Long.class);
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
