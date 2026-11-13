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
import br.com.ecoponto.contrato.Exemplos.Pedido;
import br.com.ecoponto.preparacao.ContasTesteService;
import br.com.ecoponto.seguranca.ContasDoScript;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class EdicaoDePontosIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BRETAS = "/api/admin/pontos/4";
    private static final List<Integer> GUARDADOS = List.of(4, 5, 14);
    private static final Set<String> TABELAS = Set.of("ponto_categoria", "bairro", "categoria_residuo", "usuario");

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager gerente;

    @Autowired
    private ContasTesteService contasTeste;

    @SpyBean
    private CategoriaRepositorio categorias;

    private final Map<Integer, Map<String, Object>> linhas = new LinkedHashMap<>();
    private final Map<Integer, List<Integer>> vinculos = new LinkedHashMap<>();
    private int vinculosAntes;

    private static boolean contasProntas;

    @BeforeEach
    void guardarOsPontos() {
        if (!contasProntas) {
            contasTeste.refazerHashes(ContasDoScript.todas());
            contasProntas = true;
        }
        for (int id : GUARDADOS) {
            linhas.put(id, jdbc.queryForMap("SELECT * FROM ponto_coleta WHERE id_ponto = ?", id));
            vinculos.put(id, materiaisNoBanco(id));
        }
        vinculosAntes = contar("ponto_categoria");
    }

    @AfterEach
    void devolverOsPontos() {
        for (int id : GUARDADOS) {
            Map<String, Object> linha = linhas.get(id);
            jdbc.update("DELETE FROM ponto_coleta WHERE id_ponto = ?", id);
            jdbc.update("INSERT INTO ponto_coleta (id_ponto, nome, endereco, id_bairro, horario, contato, status,"
                    + " data_cadastro, id_usuario) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", id, linha.get("nome"),
                    linha.get("endereco"), linha.get("id_bairro"), linha.get("horario"), linha.get("contato"),
                    linha.get("status"), linha.get("data_cadastro"), linha.get("id_usuario"));
            for (int categoria : vinculos.get(id)) {
                jdbc.update("INSERT INTO ponto_categoria (id_ponto, id_categoria) VALUES (?, ?)", id, categoria);
            }
        }
        assertThat(contar("ponto_categoria")).as("os vínculos do script").isEqualTo(vinculosAntes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin-editar-ponto", "admin-editar-ponto-dados-invalidos", "admin-excluir-ponto",
        "admin-ponto-nao-encontrado", "admin-ponto-id-invalido"})
    void respondeComoOsExemplosDoContrato(String nome) throws Exception {
        Caso caso = Exemplos.caso(nome);
        int conferidos = 0;
        for (Pedido pedido : caso.pedidos()) {
            if (!Set.of("PUT", "DELETE").contains(pedido.metodo())) {
                continue;
            }
            String corpo = pedido.corpo() == null ? null : pedido.corpo().toString();
            Resposta resposta = cliente(pedido.sessao()).enviar(pedido.metodo(), pedido.caminho(), corpo);

            assertThat(resposta.status()).as("%s %s", pedido.metodo(), pedido.caminho()).isEqualTo(caso.status());
            if (caso.status() == 204) {
                assertThat(resposta.corpo()).isEmpty();
            } else {
                JSONAssert.assertEquals(caso.corpo().toString(), resposta.corpo(), JSONCompareMode.STRICT);
            }
            conferidos++;
        }
        assertThat(conferidos).isPositive();
        if (caso.status() == 400) {
            assertThat(jdbc.queryForMap("SELECT * FROM ponto_coleta WHERE id_ponto = 4")).isEqualTo(linhas.get(4));
            assertThat(materiaisNoBanco(4)).isEqualTo(vinculos.get(4));
        }
    }

    @Test
    void aEdicaoApareceNoDetalheENasListas() throws Exception {
        ClienteDaApi visitante = cliente(null);
        assertThat(ids(lista(visitante, "/api/pontos?categoria=10&tamanho=50"))).doesNotContain(4);
        assertThat(materiaisNoBanco(4)).doesNotContain(10);
        ObjectNode corpo = corpo("Bretas Supermercados", "Av. Afonso Pena, 100", 8, List.of(10, 1));

        Resposta editado = cliente(ContasDoScript.ADMIN).enviar("PUT", BRETAS, corpo.toString());

        assertThat(editado.status()).as(editado.corpo()).isEqualTo(200);
        JsonNode detalhe = JSON.readTree(visitante.get("/api/pontos/4").corpo());
        assertThat(detalhe.path("nome").asText()).isEqualTo("Bretas Supermercados");
        assertThat(detalhe.path("endereco").asText()).isEqualTo("Av. Afonso Pena, 100");
        assertThat(detalhe.path("bairro").path("nome").asText()).isEqualTo("Fundinho");
        assertThat(materiais(detalhe)).containsExactly(1, 10);
        assertThat(materiaisNoBanco(4)).containsExactly(1, 10);

        assertThat(nomesNaLista(lista(visitante, "/api/pontos?bairro=8"), 4)).containsExactly("Bretas Supermercados");
        assertThat(ids(lista(visitante, "/api/pontos?bairro=4&tamanho=50"))).doesNotContain(4);
        assertThat(ids(lista(visitante, "/api/pontos?categoria=10&tamanho=50"))).contains(4);
        assertThat(ids(lista(visitante, "/api/pontos?categoria=" + vinculos.get(4).get(0) + "&tamanho=50")))
                .as("um material que ele não aceita mais").doesNotContain(4);
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        assertThat(nomesNaLista(lista(admin, "/api/admin/pontos?status=APROVADO&tamanho=50"), 4))
                .containsExactly("Bretas Supermercados");
        assertThat(nomesNaLista(lista(admin, "/api/minhas-sugestoes?tamanho=50"), 4))
                .containsExactly("Bretas Supermercados");
    }

    @Test
    void excluirOBretasDeixa12AprovadosESemVinculoOrfao() throws Exception {
        int bairros = contar("bairro");
        int categoriasAntes = contar("categoria_residuo");
        int usuarios = contar("usuario");

        Resposta excluido = cliente(ContasDoScript.ADMIN).enviar("DELETE", BRETAS, null);

        assertThat(excluido.status()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta WHERE status = 'APROVADO'", Integer.class))
                .isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta WHERE id_ponto = 4", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_categoria pc LEFT JOIN ponto_coleta p"
                + " ON p.id_ponto = pc.id_ponto WHERE p.id_ponto IS NULL", Integer.class))
                .as("vínculos órfãos").isZero();
        assertThat(contar("ponto_categoria")).isEqualTo(vinculosAntes - vinculos.get(4).size());
        assertThat(contar("bairro")).isEqualTo(bairros);
        assertThat(contar("categoria_residuo")).isEqualTo(categoriasAntes);
        assertThat(contar("usuario")).isEqualTo(usuarios);

        ClienteDaApi visitante = cliente(null);
        assertThat(visitante.get("/api/pontos/4").status()).isEqualTo(404);
        assertThat(lista(visitante, "/api/pontos").path("total").asInt()).isEqualTo(12);
        assertThat(JSON.readTree(cliente(ContasDoScript.ADMIN).get("/api/admin/resumo").corpo()).path("pontos").asInt())
                .isEqualTo(12);
        assertThat(cliente(ContasDoScript.ADMIN).enviar("DELETE", BRETAS, null).status()).as("de novo").isEqualTo(404);
    }

    @Test
    void oCorpoDoPutNaoMudaOStatusNemOAutor() throws Exception {
        Map<String, Object> pontoUmAntes = jdbc.queryForMap("SELECT * FROM ponto_coleta WHERE id_ponto = 1");
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        for (int id : GUARDADOS) {
            ObjectNode corpo = corpo("Ponto editado " + id, "Rua das Flores, 50", 20, List.of(2))
                    .put("status", "RECUSADO").put("idUsuario", 2).put("idPonto", 1)
                    .put("dataCadastro", "2000-01-01T00:00:00");
            corpo.putObject("autor").put("idUsuario", 2);

            Resposta editado = admin.enviar("PUT", "/api/admin/pontos/" + id, corpo.toString());

            assertThat(editado.status()).as(editado.corpo()).isEqualTo(200);
            assertThat(JSON.readTree(editado.corpo()).has("status")).isFalse();
            Map<String, Object> linha = jdbc.queryForMap("SELECT * FROM ponto_coleta WHERE id_ponto = ?", id);
            Map<String, Object> antes = linhas.get(id);
            assertThat(linha.get("nome")).isEqualTo("Ponto editado " + id);
            assertThat(linha.get("status")).as("ponto %d", id).isEqualTo(antes.get("status"));
            assertThat(linha.get("id_usuario")).as("ponto %d", id).isEqualTo(antes.get("id_usuario"));
            assertThat(linha.get("data_cadastro")).as("ponto %d", id).isEqualTo(antes.get("data_cadastro"));
        }
        assertThat(jdbc.queryForMap("SELECT * FROM ponto_coleta WHERE id_ponto = 1")).isEqualTo(pontoUmAntes);
        assertThat(jdbc.queryForObject("SELECT status FROM ponto_coleta WHERE id_ponto = 14", String.class))
                .isEqualTo("PENDENTE");
    }

    @Test
    void seAEdicaoFalharNoMeioOPontoFicaComoEstava(CapturedOutput saida) throws Exception {
        doAnswer(pedido -> List.of(gerente.find(Categoria.class, 10), gerente.getReference(Categoria.class, 999)))
                .when(categorias).findAllById(any());
        ObjectNode corpo = corpo("Bretas que não pode ficar", "Rua que não pode ficar, 1", 8, List.of(10, 9));

        Resposta resposta = cliente(ContasDoScript.ADMIN).enviar("PUT", BRETAS, corpo.toString());

        assertThat(resposta.status()).as(resposta.corpo()).isEqualTo(500);
        assertThat(JSON.readTree(resposta.corpo()).path("codigo").asText()).isEqualTo("ERRO_INTERNO");
        assertThat(saida.getOut()).as("quem recusou foi o banco").contains("fk_pc_categoria");
        assertThat(jdbc.queryForMap("SELECT * FROM ponto_coleta WHERE id_ponto = 4")).isEqualTo(linhas.get(4));
        assertThat(materiaisNoBanco(4)).isEqualTo(vinculos.get(4));
        assertThat(contar("ponto_categoria")).isEqualTo(vinculosAntes);
    }

    @Test
    void editarEExcluirAoMesmoTempoNuncaDa500() throws Exception {
        List<ClienteDaApi> admins = List.of(cliente(ContasDoScript.ADMIN), cliente(ContasDoScript.ADMIN));
        String corpo = corpo("Bretas editado", "Av. João Pinheiro, 77", 4, List.of(6, 7)).toString();
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            for (int rodada = 1; rodada <= 10; rodada++) {
                devolverOsPontos();
                CountDownLatch largada = new CountDownLatch(1);
                Future<Resposta> edicao = threads.submit(() -> {
                    largada.await();
                    return admins.get(0).enviar("PUT", BRETAS, corpo);
                });
                Future<Resposta> exclusao = threads.submit(() -> {
                    largada.await();
                    return admins.get(1).enviar("DELETE", BRETAS, null);
                });
                largada.countDown();
                int statusDaEdicao = edicao.get(30, TimeUnit.SECONDS).status();
                int statusDaExclusao = exclusao.get(30, TimeUnit.SECONDS).status();

                assertThat(statusDaExclusao).as("rodada %d", rodada).isEqualTo(204);
                assertThat(statusDaEdicao).as("rodada %d", rodada).isIn(200, 404);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta WHERE id_ponto = 4", Integer.class))
                        .isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ponto_categoria WHERE id_ponto = 4",
                        Integer.class)).isZero();
            }
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void excluirUmPontoEEditarOutroAoMesmoTempoOsDoisPassam() throws Exception {
        List<ClienteDaApi> admins = List.of(cliente(ContasDoScript.ADMIN), cliente(ContasDoScript.ADMIN));
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            for (int rodada = 1; rodada <= 10; rodada++) {
                devolverOsPontos();
                List<Integer> novos = rodada % 2 == 0 ? List.of(1, 2) : List.of(3, 4, 9);
                String corpo = corpo("Loja Elétrica", "Av. João Naves de Ávila, 6300", 16, novos).toString();
                CountDownLatch largada = new CountDownLatch(1);
                Future<Resposta> exclusao = threads.submit(() -> {
                    largada.await();
                    return admins.get(0).enviar("DELETE", BRETAS, null);
                });
                Future<Resposta> edicao = threads.submit(() -> {
                    largada.await();
                    return admins.get(1).enviar("PUT", "/api/admin/pontos/5", corpo);
                });
                largada.countDown();
                Resposta excluido = exclusao.get(30, TimeUnit.SECONDS);
                Resposta editado = edicao.get(30, TimeUnit.SECONDS);

                assertThat(excluido.status()).as("rodada %d: %s", rodada, excluido.corpo()).isEqualTo(204);
                assertThat(editado.status()).as("rodada %d: %s", rodada, editado.corpo()).isEqualTo(200);
                assertThat(materiaisNoBanco(5)).as("rodada %d", rodada).isEqualTo(novos);
            }
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void aEdicaoEAExclusaoVaoParaOLog(CapturedOutput saida) {
        Integer idDoAdmin = jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE email = ?", Integer.class,
                ContasDoScript.ADMIN);
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);

        assertThat(admin.enviar("PUT", BRETAS, corpo("Bretas", "Av. João Pinheiro, 77", 4, List.of(6)).toString())
                .status()).isEqualTo(200);
        assertThat(admin.enviar("DELETE", BRETAS, null).status()).isEqualTo(204);

        assertThat(saida.getOut()).contains("Ponto 4 editado pela conta " + idDoAdmin + ".")
                .contains("Ponto 4 excluído pela conta " + idDoAdmin + ".");
    }

    @Test
    void visitanteEUsuarioComumNaoEditamNemExcluem() throws Exception {
        String corpo = corpo("Bretas", "Av. João Pinheiro, 77", 4, List.of(6)).toString();
        for (String quem : new String[] {null, ContasDoScript.MARIA, ContasDoScript.JOAO}) {
            int esperado = quem == null ? 401 : 403;
            assertThat(cliente(quem).enviar("PUT", BRETAS, corpo).status()).as("%s PUT", quem).isEqualTo(esperado);
            assertThat(cliente(quem).enviar("DELETE", BRETAS, null).status()).as("%s DELETE", quem)
                    .isEqualTo(esperado);
        }
        assertThat(cliente(ContasDoScript.ADMIN).semTokenCsrf().enviar("DELETE", BRETAS, null).status())
                .as("ADMIN sem o token CSRF").isEqualTo(403);

        assertThat(jdbc.queryForMap("SELECT * FROM ponto_coleta WHERE id_ponto = 4")).isEqualTo(linhas.get(4));
        assertThat(materiaisNoBanco(4)).isEqualTo(vinculos.get(4));
    }

    private static ObjectNode corpo(String nome, String endereco, int idBairro, List<Integer> categorias) {
        ObjectNode corpo = JSON.createObjectNode().put("nome", nome).put("endereco", endereco)
                .put("idBairro", idBairro).put("horario", "8h às 22h").putNull("contato");
        categorias.forEach(corpo.putArray("categorias")::add);
        return corpo;
    }

    private int contar(String tabela) {
        assertThat(TABELAS).contains(tabela);
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela, Integer.class);
    }

    private List<Integer> materiaisNoBanco(int idPonto) {
        return jdbc.queryForList("SELECT id_categoria FROM ponto_categoria WHERE id_ponto = ? ORDER BY id_categoria",
                Integer.class, idPonto);
    }

    private static List<Integer> materiais(JsonNode ponto) {
        List<Integer> materiais = new ArrayList<>();
        ponto.path("materiais").forEach(m -> materiais.add(m.path("idCategoria").asInt()));
        return materiais;
    }

    private static List<String> nomesNaLista(JsonNode pagina, int idPonto) {
        List<String> nomes = new ArrayList<>();
        pagina.path("itens").forEach(item -> {
            if (item.path("idPonto").asInt() == idPonto) {
                nomes.add(item.path("nome").asText());
            }
        });
        return nomes;
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

    private ClienteDaApi cliente(String email) {
        ClienteDaApi cliente = new ClienteDaApi(porta);
        if (email != null) {
            Resposta login = cliente.post("/api/auth/login", ContasDoScript.corpoDoLogin(email));
            assertThat(login.status()).as("login de %s", email).isEqualTo(200);
        }
        return cliente;
    }
}
