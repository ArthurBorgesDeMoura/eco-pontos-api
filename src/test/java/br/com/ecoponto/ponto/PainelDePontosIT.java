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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=warn"
})
@ExtendWith(OutputCaptureExtension.class)
class PainelDePontosIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> ROTAS_DE_DECISAO = List.of("/aprovar", "/recusar");

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
    void devolverOScript() {
        jdbc.update("DELETE FROM ponto_coleta WHERE id_ponto > ?", maiorIdAntes);
        jdbc.update("UPDATE ponto_coleta SET status = 'PENDENTE' WHERE id_ponto IN (14, 15)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin-resumo", "admin-pontos-pendentes", "admin-pontos-aprovados",
        "admin-pontos-lista-vazia", "admin-pontos-filtros-invalidos", "admin-aprovar", "admin-recusar",
        "admin-decisao-repetida", "admin-ponto-nao-encontrado", "admin-ponto-id-invalido"})
    void respondeComoOsExemplosDoContrato(String nome) throws Exception {
        Caso caso = Exemplos.caso(nome);
        int conferidos = 0;
        for (Pedido pedido : caso.pedidos()) {
            boolean doBe13 = pedido.metodo().equals("PUT") || pedido.metodo().equals("DELETE");
            boolean outroPost = pedido.metodo().equals("POST")
                    && ROTAS_DE_DECISAO.stream().noneMatch(pedido.caminho()::endsWith);
            if (doBe13 || outroPost) {
                continue;
            }
            Resposta resposta = cliente(pedido.sessao()).enviar(pedido.metodo(), pedido.caminho(), null);

            assertThat(resposta.status()).as("%s %s", pedido.metodo(), pedido.caminho()).isEqualTo(caso.status());
            JSONAssert.assertEquals(caso.corpo().toString(), resposta.corpo(), JSONCompareMode.STRICT);
            conferidos++;
        }
        assertThat(conferidos).isPositive();
    }

    @Test
    void aprovarPublicaOPontoEMudaOResumo() throws Exception {
        ClienteDaApi visitante = cliente(null);
        assertThat(ids(lista(visitante, "/api/pontos?tamanho=50"))).doesNotContain(14);

        Resposta aprovado = cliente(ContasDoScript.ADMIN).post("/api/admin/pontos/14/aprovar", null);

        assertThat(aprovado.status()).isEqualTo(200);
        assertThat(JSON.readTree(aprovado.corpo()).path("status").asText()).isEqualTo("APROVADO");
        assertThat(statusNoBanco(14)).isEqualTo("APROVADO");
        JsonNode busca = lista(visitante, "/api/pontos?tamanho=50");
        assertThat(ids(busca)).contains(14);
        assertThat(busca.path("total").asInt()).isEqualTo(14);
        assertThat(ids(lista(visitante, "/api/pontos?bairro=7"))).containsExactly(14);
        assertThat(visitante.get("/api/pontos/14").status()).isEqualTo(200);
        JSONAssert.assertEquals("{\"pendentes\":1,\"pontos\":14,\"categorias\":10}", resumo(), JSONCompareMode.STRICT);
        assertThat(ids(lista(cliente(ContasDoScript.ADMIN), "/api/admin/pontos"))).containsExactly(15);
        assertThat(statusDasSugestoes(ContasDoScript.JOAO)).containsExactly("APROVADO");
    }

    @Test
    void recusarTiraDaFilaNaoPublicaEAAutoraVeRecusado() throws Exception {
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);

        Resposta recusado = admin.post("/api/admin/pontos/15/recusar", null);

        assertThat(recusado.status()).isEqualTo(200);
        assertThat(statusNoBanco(15)).isEqualTo("RECUSADO");
        assertThat(ids(lista(admin, "/api/admin/pontos"))).containsExactly(14);
        assertThat(ids(lista(admin, "/api/admin/pontos?status=RECUSADO"))).containsExactly(15);
        ClienteDaApi visitante = cliente(null);
        assertThat(ids(lista(visitante, "/api/pontos?tamanho=50"))).doesNotContain(15).hasSize(13);
        assertThat(visitante.get("/api/pontos/15").status()).isEqualTo(404);
        JSONAssert.assertEquals("{\"pendentes\":1,\"pontos\":13,\"categorias\":10}", resumo(), JSONCompareMode.STRICT);
        ClienteDaApi maria = cliente(ContasDoScript.MARIA);
        assertThat(ids(lista(maria, "/api/minhas-sugestoes?status=RECUSADO"))).containsExactly(15);
        assertThat(lista(maria, "/api/minhas-sugestoes?status=PENDENTE").path("total").asInt()).isZero();
    }

    @Test
    void decidirOJaDecididoDa409ENaoMudaOStatus() throws Exception {
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        String jaAnalisado = Exemplos.caso("admin-decisao-repetida").corpo().toString();
        assertThat(admin.post("/api/admin/pontos/14/aprovar", null).status()).isEqualTo(200);
        assertThat(admin.post("/api/admin/pontos/15/recusar", null).status()).isEqualTo(200);

        for (int id : new int[] {13, 14, 15}) {
            String antes = statusNoBanco(id);
            for (String decisao : ROTAS_DE_DECISAO) {
                Resposta resposta = admin.post("/api/admin/pontos/" + id + decisao, null);

                assertThat(resposta.status()).as("ponto %d%s", id, decisao).isEqualTo(409);
                JSONAssert.assertEquals(jaAnalisado, resposta.corpo(), JSONCompareMode.STRICT);
            }
            assertThat(statusNoBanco(id)).as("ponto %d", id).isEqualTo(antes);
        }
        JSONAssert.assertEquals("{\"pendentes\":0,\"pontos\":14,\"categorias\":10}", resumo(), JSONCompareMode.STRICT);
    }

    @Test
    void decisoesAoMesmoTempoSoUmaPassa() throws Exception {
        List<ClienteDaApi> admins = List.of(cliente(ContasDoScript.ADMIN), cliente(ContasDoScript.ADMIN));
        int pedidos = 8;
        ExecutorService threads = Executors.newFixedThreadPool(pedidos);
        try {
            for (int rodada = 1; rodada <= 10; rodada++) {
                jdbc.update("UPDATE ponto_coleta SET status = 'PENDENTE' WHERE id_ponto = 14");
                CountDownLatch largada = new CountDownLatch(1);
                List<Future<Resposta>> respostas = new ArrayList<>();
                for (int i = 0; i < pedidos; i++) {
                    ClienteDaApi admin = admins.get(i % 2);
                    String rota = "/api/admin/pontos/14" + ROTAS_DE_DECISAO.get(i % 2);
                    respostas.add(threads.submit(() -> {
                        largada.await();
                        return admin.post(rota, null);
                    }));
                }
                largada.countDown();
                List<Integer> status = new ArrayList<>();
                String decisaoQuePassou = null;
                for (Future<Resposta> resposta : respostas) {
                    Resposta r = resposta.get(30, TimeUnit.SECONDS);
                    status.add(r.status());
                    if (r.status() == 200) {
                        decisaoQuePassou = JSON.readTree(r.corpo()).path("status").asText();
                    }
                }

                assertThat(status).as("rodada %d: %s", rodada, status).containsOnly(200, 409)
                        .filteredOn(s -> s == 200).hasSize(1);
                assertThat(statusNoBanco(14)).as("rodada %d", rodada).isEqualTo(decisaoQuePassou);
            }
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void decisoesAoMesmoTempoEmPontosDiferentesTodasPassam() throws Exception {
        ClienteDaApi maria = cliente(ContasDoScript.MARIA);
        List<Integer> pendentes = new ArrayList<>(List.of(14, 15));
        for (int i = 0; i < 4; i++) {
            Resposta criada = maria.post("/api/pontos", "{\"nome\":\"Pendente " + i + "\","
                    + "\"endereco\":\"Rua das Flores, 50\",\"idBairro\":20,\"categorias\":[2]}");
            pendentes.add(JSON.readTree(criada.corpo()).path("idPonto").asInt());
        }
        List<ClienteDaApi> admins = new ArrayList<>();
        for (int i = 0; i < pendentes.size(); i++) {
            admins.add(cliente(ContasDoScript.ADMIN));
        }
        ExecutorService threads = Executors.newFixedThreadPool(pendentes.size());
        try {
            for (int rodada = 1; rodada <= 15; rodada++) {
                jdbc.update("UPDATE ponto_coleta SET status = 'PENDENTE' WHERE id_ponto IN (14, 15) OR id_ponto > ?",
                        maiorIdAntes);
                CountDownLatch largada = new CountDownLatch(1);
                List<Future<Resposta>> respostas = new ArrayList<>();
                for (int i = 0; i < pendentes.size(); i++) {
                    ClienteDaApi admin = admins.get(i);
                    String rota = "/api/admin/pontos/" + pendentes.get(i) + ROTAS_DE_DECISAO.get((i + rodada) % 2);
                    respostas.add(threads.submit(() -> {
                        largada.await();
                        return admin.post(rota, null);
                    }));
                }
                largada.countDown();
                List<Integer> status = new ArrayList<>();
                for (Future<Resposta> resposta : respostas) {
                    status.add(resposta.get(30, TimeUnit.SECONDS).status());
                }

                assertThat(status).as("rodada %d: %s", rodada, status).containsOnly(200);
            }
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void visitanteEUsuarioComumNaoAcessamNenhumaRota() throws Exception {
        String semLogin = Exemplos.caso("sem-login").corpo().toString();
        String semPermissao = Exemplos.caso("sem-permissao").corpo().toString();
        List<String[]> rotas = List.of(new String[] {"GET", "/api/admin/resumo"},
                new String[] {"GET", "/api/admin/pontos"}, new String[] {"GET", "/api/admin/pontos?status=APROVADO"},
                new String[] {"POST", "/api/admin/pontos/14/aprovar"},
                new String[] {"POST", "/api/admin/pontos/15/recusar"});
        for (String[] rota : rotas) {
            Resposta visitante = cliente(null).enviar(rota[0], rota[1], null);
            assertThat(visitante.status()).as("visitante %s", rota[1]).isEqualTo(401);
            JSONAssert.assertEquals(semLogin, visitante.corpo(), JSONCompareMode.STRICT);
            for (String usuario : List.of(ContasDoScript.MARIA, ContasDoScript.JOAO)) {
                Resposta comum = cliente(usuario).enviar(rota[0], rota[1], null);
                assertThat(comum.status()).as("%s %s", usuario, rota[1]).isEqualTo(403);
                JSONAssert.assertEquals(semPermissao, comum.corpo(), JSONCompareMode.STRICT);
            }
        }
        Resposta semToken = cliente(ContasDoScript.ADMIN).semTokenCsrf().post("/api/admin/pontos/14/aprovar", null);
        assertThat(semToken.status()).as("ADMIN sem o token CSRF").isEqualTo(403);

        assertThat(statusNoBanco(14)).isEqualTo("PENDENTE");
        assertThat(statusNoBanco(15)).isEqualTo("PENDENTE");
    }

    @Test
    void aFilaVaiDoMaisAntigoParaOMaisNovo() throws Exception {
        Resposta criada = cliente(ContasDoScript.MARIA).post("/api/pontos", "{\"nome\":\"Mercado do Bairro\","
                + "\"endereco\":\"Rua das Flores, 50\",\"idBairro\":20,\"categorias\":[2]}");
        assertThat(criada.status()).isEqualTo(201);
        int novo = JSON.readTree(criada.corpo()).path("idPonto").asInt();
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);

        JsonNode fila = lista(admin, "/api/admin/pontos");

        assertThat(ids(fila)).containsExactly(14, 15, novo);
        assertThat(fila.path("itens").get(2).path("sugeridoPor").toString())
                .isEqualTo("{\"idUsuario\":2,\"nome\":\"Maria Souza\"}");
        assertThat(ids(lista(admin, "/api/admin/pontos?tamanho=2&pagina=2"))).containsExactly(novo);
        JSONAssert.assertEquals("{\"pendentes\":3,\"pontos\":13,\"categorias\":10}", resumo(), JSONCompareMode.STRICT);
    }

    @Test
    void osAprovadosVemNaOrdemDaBuscaEmQualquerTamanhoDePagina() throws Exception {
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        List<Integer> daBusca = ids(lista(cliente(null), "/api/pontos?tamanho=50"));

        for (int tamanho = 1; tamanho <= 13; tamanho++) {
            List<Integer> juntados = new ArrayList<>();
            for (int pagina = 1; pagina <= 14; pagina++) {
                JsonNode corpo = lista(admin,
                        "/api/admin/pontos?status=APROVADO&pagina=" + pagina + "&tamanho=" + tamanho);
                assertThat(corpo.path("total").asInt()).isEqualTo(13);
                juntados.addAll(ids(corpo));
            }
            assertThat(juntados).as("tamanho %d", tamanho).isEqualTo(daBusca);
        }
    }

    @Test
    void aDecisaoVaiParaOLog(CapturedOutput saida) {
        Integer idDoAdmin = jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE email = ?", Integer.class,
                ContasDoScript.ADMIN);
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);

        assertThat(admin.post("/api/admin/pontos/14/aprovar", null).status()).isEqualTo(200);
        assertThat(admin.post("/api/admin/pontos/15/recusar", null).status()).isEqualTo(200);

        assertThat(saida.getOut())
                .contains("Ponto 14 aprovado pela conta " + idDoAdmin + ".")
                .contains("Ponto 15 recusado pela conta " + idDoAdmin + ".");
    }

    @Test
    void osComandosSqlDeCadaRota() {
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        Statistics estatisticas = fabrica.unwrap(SessionFactory.class).getStatistics();

        estatisticas.clear();
        assertThat(admin.get("/api/admin/pontos").status()).isEqualTo(200);
        assertThat(estatisticas.getPrepareStatementCount()).as("lista").isEqualTo(3);

        estatisticas.clear();
        assertThat(admin.get("/api/admin/resumo").status()).isEqualTo(200);
        assertThat(estatisticas.getPrepareStatementCount()).as("resumo").isEqualTo(3);

        estatisticas.clear();
        assertThat(admin.post("/api/admin/pontos/14/aprovar", null).status()).isEqualTo(200);
        assertThat(estatisticas.getPrepareStatementCount()).as("decisão").isEqualTo(4);

        estatisticas.clear();
        assertThat(admin.post("/api/admin/pontos/14/recusar", null).status()).isEqualTo(409);
        assertThat(estatisticas.getPrepareStatementCount()).as("409").isEqualTo(2);
    }

    private String resumo() {
        Resposta resposta = cliente(ContasDoScript.ADMIN).get("/api/admin/resumo");
        assertThat(resposta.status()).isEqualTo(200);
        return resposta.corpo();
    }

    private String statusNoBanco(int idPonto) {
        return jdbc.queryForObject("SELECT status FROM ponto_coleta WHERE id_ponto = ?", String.class, idPonto);
    }

    private List<String> statusDasSugestoes(String email) throws Exception {
        List<String> status = new ArrayList<>();
        lista(cliente(email), "/api/minhas-sugestoes").path("itens")
                .forEach(item -> status.add(item.path("status").asText()));
        return status;
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
