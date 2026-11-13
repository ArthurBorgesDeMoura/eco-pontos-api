package br.com.ecoponto.desempenho;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ecoponto.contrato.ClienteDaApi;
import br.com.ecoponto.contrato.ClienteDaApi.Resposta;
import br.com.ecoponto.preparacao.ContasTesteService;
import br.com.ecoponto.seguranca.ContasDoScript;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DesempenhoIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BANCO = "ecoponto_desempenho";
    private static final int AQUECIMENTO = 5;
    private static final int MEDIDOS = 30;
    private static final long LIMITE_EM_MS = 2_000;

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContasTesteService contasTeste;

    private record Cenario(String nome, String quem, String caminho) {
    }

    private record Tempos(Cenario cenario, double mediana, double p95, double maximo) {
    }

    @Test
    void listagemEFiltroRespondemEmAte2Segundos() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class)).as("o banco medido").isEqualTo(BANCO);
        int pontos = jdbc.queryForObject("SELECT COUNT(*) FROM ponto_coleta", Integer.class);
        assertThat(pontos).as("pontos no %s; gere o banco com o db/desempenho.sql (README, Desempenho)", BANCO)
                .isGreaterThanOrEqualTo(5_000);
        contasTeste.refazerHashes(ContasDoScript.todas());

        ClienteDaApi visitante = new ClienteDaApi(porta);
        ClienteDaApi admin = new ClienteDaApi(porta);
        assertThat(admin.post("/api/auth/login", ContasDoScript.corpoDoLogin(ContasDoScript.ADMIN)).status())
                .isEqualTo(200);

        List<Tempos> tempos = new ArrayList<>();
        for (Cenario cenario : cenarios(visitante, admin)) {
            tempos.add(medir(cenario.quem() == null ? visitante : admin, cenario));
        }

        String tabela = tabela(tempos, pontos);
        System.out.println(tabela);
        Files.writeString(Path.of("target", "desempenho.md"), tabela, StandardCharsets.UTF_8);
        assertThat(tempos).allSatisfy(t -> assertThat(t.maximo()).as("%s, máximo em ms", t.cenario().nome())
                .isLessThan(LIMITE_EM_MS));
    }

    private List<Cenario> cenarios(ClienteDaApi visitante, ClienteDaApi admin) throws IOException {
        int busca = ultimaPagina(visitante, "/api/pontos");
        int bairro = ultimaPagina(visitante, "/api/pontos?bairro=8");
        int detalhe = primeiroId(visitante, "/api/pontos?pagina=" + busca / 2);
        int minhas = ultimaPagina(admin, "/api/minhas-sugestoes");
        int pendentes = ultimaPagina(admin, "/api/admin/pontos");
        int aprovados = ultimaPagina(admin, "/api/admin/pontos?status=APROVADO");
        String a = ContasDoScript.ADMIN;
        return List.of(
                new Cenario("Busca, 1ª página", null, "/api/pontos"),
                new Cenario("Busca, página do meio (" + busca / 2 + ")", null, "/api/pontos?pagina=" + busca / 2),
                new Cenario("Busca, última página (" + busca + ")", null, "/api/pontos?pagina=" + busca),
                new Cenario("Busca, depois do fim", null, "/api/pontos?pagina=999999"),
                new Cenario("Busca, 50 por página", null, "/api/pontos?tamanho=50"),
                new Cenario("Busca por bairro", null, "/api/pontos?bairro=8"),
                new Cenario("Busca por bairro, última página (" + bairro + ")", null,
                        "/api/pontos?bairro=8&pagina=" + bairro),
                new Cenario("Busca por material", null, "/api/pontos?categoria=6"),
                new Cenario("Busca por bairro e material", null, "/api/pontos?bairro=8&categoria=6"),
                new Cenario("Contagem por material", null, "/api/pontos/contagem"),
                new Cenario("Contagem por material, com bairro", null, "/api/pontos/contagem?bairro=8"),
                new Cenario("Detalhe de um ponto", null, "/api/pontos/" + detalhe),
                new Cenario("Minhas sugestões (admin), 1ª página", a, "/api/minhas-sugestoes"),
                new Cenario("Minhas sugestões (admin), última página (" + minhas + ")", a,
                        "/api/minhas-sugestoes?pagina=" + minhas),
                new Cenario("Minhas sugestões (admin), só pendentes", a, "/api/minhas-sugestoes?status=PENDENTE"),
                new Cenario("Painel, pendentes, 1ª página", a, "/api/admin/pontos"),
                new Cenario("Painel, pendentes, última página (" + pendentes + ")", a,
                        "/api/admin/pontos?pagina=" + pendentes),
                new Cenario("Painel, aprovados, última página (" + aprovados + ")", a,
                        "/api/admin/pontos?status=APROVADO&pagina=" + aprovados),
                new Cenario("Painel, categorias", a, "/api/admin/categorias?tamanho=50"),
                new Cenario("Painel, resumo", a, "/api/admin/resumo"));
    }

    private static int ultimaPagina(ClienteDaApi cliente, String caminho) throws IOException {
        Resposta resposta = cliente.get(caminho);
        assertThat(resposta.status()).as(caminho).isEqualTo(200);
        long total = JSON.readTree(resposta.corpo()).path("total").asLong();
        return (int) Math.max(1, (total + 6) / 7);
    }

    private static int primeiroId(ClienteDaApi cliente, String caminho) throws IOException {
        Resposta resposta = cliente.get(caminho);
        assertThat(resposta.status()).as(caminho).isEqualTo(200);
        return JSON.readTree(resposta.corpo()).path("itens").get(0).path("idPonto").asInt();
    }

    private static Tempos medir(ClienteDaApi cliente, Cenario cenario) {
        for (int i = 0; i < AQUECIMENTO; i++) {
            pedir(cliente, cenario);
        }
        List<Double> medidas = new ArrayList<>();
        for (int i = 0; i < MEDIDOS; i++) {
            long inicio = System.nanoTime();
            pedir(cliente, cenario);
            medidas.add((System.nanoTime() - inicio) / 1_000_000.0);
        }
        Collections.sort(medidas);
        return new Tempos(cenario, medidas.get(MEDIDOS / 2), medidas.get((int) Math.ceil(MEDIDOS * 0.95) - 1),
                medidas.get(MEDIDOS - 1));
    }

    private static void pedir(ClienteDaApi cliente, Cenario cenario) {
        Resposta resposta = cliente.get(cenario.caminho());
        assertThat(resposta.status()).as("%s: %s", cenario.nome(), resposta.corpo()).isEqualTo(200);
    }

    private static String tabela(List<Tempos> tempos, int pontos) {
        StringBuilder texto = new StringBuilder();
        texto.append("Medição do BE-15 em ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern(
                "dd/MM/yyyy HH:mm"))).append(", no ").append(BANCO).append(", com ").append(pontos)
                .append(" pontos. Em ms, de ").append(MEDIDOS).append(" pedidos HTTP depois de ").append(AQUECIMENTO)
                .append(" de aquecimento.\n\n")
                .append("| Cenário | Caminho | Mediana | p95 | Máximo |\n| --- | --- | --- | --- | --- |\n");
        for (Tempos t : tempos) {
            texto.append(String.format(Locale.ROOT, "| %s | `%s` | %.1f | %.1f | %.1f |%n",
                    t.cenario().nome(), t.cenario().caminho(), t.mediana(), t.p95(), t.maximo()));
        }
        return texto.toString();
    }
}
