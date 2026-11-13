package br.com.ecoponto.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ecoponto.contrato.ClienteDaApi;
import br.com.ecoponto.contrato.ClienteDaApi.Resposta;
import br.com.ecoponto.preparacao.ContasTesteService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class EntradasDeAtaqueIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> TABELAS =
            List.of("usuario", "bairro", "categoria_residuo", "ponto_coleta", "ponto_categoria");

    private static final List<String> ATAQUES = List.of(
            "'", "' OR 1=1 --", "1' OR '1'='1", "1; DROP TABLE ponto_coleta", "1 UNION SELECT senha_hash FROM usuario",
            "<script>alert(1)</script>", "\"><img src=x onerror=alert(1)>", "%00", "\u0000", "../../etc/passwd",
            "${jndi:ldap://x/y}", "‮abc", "😀🌱", "a".repeat(10_000));

    private static final List<String> VAZAMENTOS = List.of("exception", "at br.", "java.", "org.", "hibernate",
            "sql", "select ", "ponto_coleta", "categoria_residuo", "senha_hash", "mariadb", "jdbc");

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContasTesteService contasTeste;

    private Map<String, Long> conteudoAntes;
    private int maiorPontoAntes;
    private final List<String> emailsCriados = new ArrayList<>();

    private static boolean contasProntas;

    @BeforeEach
    void guardarOConteudo() {
        if (!contasProntas) {
            contasTeste.refazerHashes(ContasDoScript.todas());
            contasProntas = true;
        }
        conteudoAntes = conteudo();
        maiorPontoAntes = jdbc.queryForObject("SELECT COALESCE(MAX(id_ponto), 0) FROM ponto_coleta", Integer.class);
    }

    @AfterEach
    void apagarOQueFoiGravadoEConferirOConteudo() {
        jdbc.update("DELETE FROM ponto_coleta WHERE id_ponto > ?", maiorPontoAntes);
        jdbc.update("DELETE FROM categoria_residuo WHERE id_categoria > 10");
        emailsCriados.forEach(email -> jdbc.update("DELETE FROM usuario WHERE email = ?", email));
        assertThat(conteudo()).as("o conteúdo das 5 tabelas").isEqualTo(conteudoAntes);
    }

    @Test
    void ataquesNosFiltrosENoIdDoCaminho() throws Exception {
        ClienteDaApi visitante = new ClienteDaApi(porta);
        ClienteDaApi maria = cliente(ContasDoScript.MARIA);
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        for (String ataque : ATAQUES) {
            String valor = URLEncoder.encode(ataque, StandardCharsets.UTF_8);
            String caminho = caminhoCodificado(ataque);
            Map<String, Resposta> respostas = new LinkedHashMap<>();
            for (String parametro : List.of("bairro", "categoria", "pagina", "tamanho")) {
                respostas.put("busca " + parametro, visitante.get("/api/pontos?" + parametro + "=" + valor));
            }
            respostas.put("contagem", visitante.get("/api/pontos/contagem?bairro=" + valor));
            respostas.put("detalhe", visitante.get("/api/pontos/" + caminho));
            respostas.put("minhas sugestões", maria.get("/api/minhas-sugestoes?status=" + valor));
            respostas.put("painel status", admin.get("/api/admin/pontos?status=" + valor));
            respostas.put("painel página", admin.get("/api/admin/pontos?pagina=" + valor));
            respostas.put("categorias", admin.get("/api/admin/categorias?tamanho=" + valor));
            respostas.put("aprovar", admin.post("/api/admin/pontos/" + caminho + "/aprovar", null));
            respostas.put("excluir", admin.enviar("DELETE", "/api/admin/pontos/" + caminho, null));
            respostas.put("editar categoria", admin.enviar("PUT", "/api/admin/categorias/" + caminho,
                    "{\"nome\":\"Qualquer\"}"));

            respostas.forEach((onde, resposta) -> {
                String descricao = onde + " com " + resumo(ataque) + ": " + resposta.status();
                assertThat(resposta.status()).as(descricao).isIn(400, 404);
                conferirErroLimpo(descricao, resposta);
            });
        }
    }

    @Test
    void ataquesNoLoginNaoAbremSessao() throws Exception {
        String erradoDeVerdade = new ClienteDaApi(porta).post("/api/auth/login",
                ContasDoScript.corpo("ninguem@example.com", "senha-que-nao-existe")).corpo();
        for (String ataque : List.of("' OR 1=1 --", "admin@example.com' --", "admin@example.com' OR '1'='1",
                "\" OR \"\"=\"", "<script>alert(1)</script>")) {
            for (boolean noEmail : new boolean[] {true, false}) {
                ClienteDaApi cliente = new ClienteDaApi(porta);
                ObjectNode corpo = JSON.createObjectNode().put("email", noEmail ? ataque : ContasDoScript.ADMIN + "x")
                        .put("senha", noEmail ? "qualquer-senha-1" : ataque);

                Resposta resposta = cliente.post("/api/auth/login", corpo.toString());

                String descricao = (noEmail ? "e-mail " : "senha ") + ataque;
                assertThat(resposta.status()).as(descricao).isEqualTo(401);
                assertThat(resposta.corpo()).as(descricao).isEqualTo(erradoDeVerdade);
                assertThat(cliente.get("/api/auth/eu").status()).as("%s: sem sessão", descricao).isEqualTo(401);
            }
        }
    }

    @Test
    void ataquesNosFormulariosViramTextoOuDao400() throws Exception {
        ClienteDaApi maria = cliente(ContasDoScript.MARIA);
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        for (String ataque : ATAQUES) {
            boolean longoDemais = ataque.length() > 100;

            String email = "ataque-" + System.nanoTime() + "@example.com";
            emailsCriados.add(email);
            Resposta cadastro = new ClienteDaApi(porta).post("/api/auth/cadastro", JSON.createObjectNode()
                    .put("nome", ataque).put("email", email).put("senha", "senha-do-teste-1").toString());
            conferirTextoOuErro("cadastro, nome " + resumo(ataque), cadastro, longoDemais, "nome", ataque,
                    () -> jdbc.queryForObject("SELECT nome FROM usuario WHERE email = ?", String.class, email));
            Resposta emailRuim = new ClienteDaApi(porta).post("/api/auth/cadastro", JSON.createObjectNode()
                    .put("nome", "Nome").put("email", ataque).put("senha", "senha-do-teste-1").toString());
            assertThat(emailRuim.status()).as("cadastro, e-mail %s", resumo(ataque)).isEqualTo(400);
            conferirErroLimpo("cadastro, e-mail " + resumo(ataque), emailRuim);

            ObjectNode ponto = JSON.createObjectNode().put("nome", ataque).put("endereco", ataque).put("idBairro", 4)
                    .put("horario", ataque).putNull("contato");
            ponto.putArray("categorias").add(7);
            Resposta sugerida = maria.post("/api/pontos", ponto.toString());
            assertThat(sugerida.status()).as("sugerir %s", resumo(ataque)).isIn(201, 400);
            if (longoDemais) {
                assertThat(sugerida.status()).as("sugerir %s", resumo(ataque)).isEqualTo(400);
            }
            if (sugerida.status() == 201) {
                int id = JSON.readTree(sugerida.corpo()).path("idPonto").asInt();
                Map<String, Object> linha = jdbc.queryForMap(
                        "SELECT nome, endereco, horario FROM ponto_coleta WHERE id_ponto = ?", id);
                assertThat(linha).as("gravado como texto").containsEntry("nome", ataque)
                        .containsEntry("endereco", ataque).containsEntry("horario", ataque);
                JsonNode detalhe = JSON.readTree(admin.get("/api/pontos/" + id).corpo());
                assertThat(detalhe.path("nome").asText()).as("devolvido como texto").isEqualTo(ataque);
                Resposta editada = admin.enviar("PUT", "/api/admin/pontos/" + id, ponto.put("nome", "x" + ataque)
                        .toString());
                assertThat(editada.status()).as("editar %s", resumo(ataque)).isEqualTo(200);
                assertThat(JSON.readTree(editada.corpo()).path("nome").asText()).isEqualTo("x" + ataque);
            } else {
                conferirErroLimpo("sugerir " + resumo(ataque), sugerida);
            }

            Resposta categoria = admin.post("/api/admin/categorias",
                    JSON.createObjectNode().put("nome", ataque).putNull("descricao").toString());
            if (categoria.status() == 201) {
                int id = JSON.readTree(categoria.corpo()).path("idCategoria").asInt();
                assertThat(jdbc.queryForObject("SELECT nome FROM categoria_residuo WHERE id_categoria = ?",
                        String.class, id)).as("categoria %s gravada como texto", resumo(ataque)).isEqualTo(ataque);
                jdbc.update("DELETE FROM categoria_residuo WHERE id_categoria = ?", id);
            } else {
                assertThat(categoria.status()).as("categoria %s", resumo(ataque)).isIn(400, 409);
                conferirErroLimpo("categoria " + resumo(ataque), categoria);
            }
        }
    }

    @Test
    void corposGrandesAninhadosOuDeOutroTipo() throws Exception {
        String limite = "{\"codigo\":\"DADOS_INVALIDOS\",\"mensagem\":\"O corpo do pedido passa de 64 KB.\"}";
        ClienteDaApi visitante = new ClienteDaApi(porta);
        ClienteDaApi maria = cliente(ContasDoScript.MARIA);

        String umPoucoAcima = "{\"nome\":\"" + "a".repeat(64 * 1024) + "\"}";
        Resposta cadastroGrande = visitante.post("/api/auth/cadastro", umPoucoAcima);
        assertThat(cadastroGrande.corpo()).isEqualTo(limite);
        conferirErroLimpo("cadastro acima de 64 KB", cadastroGrande);
        assertThat(maria.post("/api/pontos", umPoucoAcima).corpo()).isEqualTo(limite);
        long inicio = System.nanoTime();
        Resposta enorme = visitante.post("/api/auth/login", "{\"email\":\"" + "a".repeat(25 * 1024 * 1024) + "\"}");
        assertThat(enorme.corpo()).isEqualTo(limite);
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).as("25 MB").isLessThan(Duration.ofSeconds(10));
        Resposta emPartes = maria.enviarEmPartes("POST", "/api/pontos",
                umPoucoAcima.getBytes(StandardCharsets.UTF_8));
        assertThat(emPartes.status()).as("em partes").isEqualTo(400);
        assertThat(emPartes.corpo()).as("em partes").isEqualTo(limite);
        Resposta pequenoEmPartes = maria.enviarEmPartes("POST", "/api/pontos",
                "{}".getBytes(StandardCharsets.UTF_8));
        assertThat(pequenoEmPartes.status()).as("pequeno em partes chega à validação").isEqualTo(400);
        assertThat(pequenoEmPartes.corpo()).contains("Confira os campos destacados.");

        byte[] formulario = ("nome=" + "a".repeat(70 * 1024)).getBytes(StandardCharsets.UTF_8);
        ClienteDaApi admin = cliente(ContasDoScript.ADMIN);
        for (ClienteDaApi quem : List.of(visitante, admin)) {
            for (String metodo : List.of("PUT", "DELETE")) {
                Resposta resposta = quem.enviarEmPartes(metodo, "/api/admin/pontos/4", formulario,
                        "application/x-www-form-urlencoded");
                assertThat(resposta.status()).as("formulário em partes, %s", metodo).isEqualTo(400);
                assertThat(resposta.corpo()).as("formulário em partes, %s", metodo).isEqualTo(limite);
                conferirErroLimpo("formulário em partes, " + metodo, resposta);
            }
        }
        String partes = "--x\r\nContent-Disposition: form-data; name=\"nome\"\r\n\r\n" + "a".repeat(70 * 1024)
                + "\r\n--x--\r\n";
        Resposta multipart = maria.enviarEmPartes("POST", "/api/pontos", partes.getBytes(StandardCharsets.UTF_8),
                "multipart/form-data; boundary=x");
        assertThat(multipart.status()).as("multipart em partes").isIn(400, 415);
        conferirErroLimpo("multipart em partes", multipart);

        String aninhado = "{\"nome\":" + "[".repeat(2_000) + "]".repeat(2_000) + "}";
        Resposta muitoAninhado = maria.post("/api/pontos", aninhado);
        assertThat(muitoAninhado.status()).as("aninhado").isEqualTo(400);
        conferirErroLimpo("aninhado", muitoAninhado);

        Resposta texto = maria.enviarComTipo("POST", "/api/pontos", "nome=x", "text/plain");
        assertThat(texto.status()).as("text/plain").isEqualTo(415);
        conferirErroLimpo("text/plain", texto);
        Resposta xml = maria.enviarComTipo("POST", "/api/pontos", "<ponto><nome>x</nome></ponto>", "application/xml");
        assertThat(xml.status()).as("xml").isEqualTo(415);
        conferirErroLimpo("xml", xml);
    }

    private void conferirTextoOuErro(String descricao, Resposta resposta, boolean longoDemais, String campo,
            String enviado, Supplier<String> gravado) throws Exception {
        assertThat(resposta.status()).as(descricao).isIn(201, 400);
        if (longoDemais) {
            assertThat(resposta.status()).as(descricao).isEqualTo(400);
        }
        if (resposta.status() == 201) {
            assertThat(JSON.readTree(resposta.corpo()).path(campo).asText()).as(descricao).isEqualTo(enviado);
            assertThat(gravado.get()).as("%s, gravado", descricao).isEqualTo(enviado);
        } else {
            conferirErroLimpo(descricao, resposta);
        }
    }

    private static void conferirErroLimpo(String descricao, Resposta resposta) {
        assertThat(resposta.status()).as(descricao).isNotEqualTo(500);
        assertThat(resposta.cabecalho("content-type")).as(descricao).hasValueSatisfying(
                tipo -> assertThat(tipo).startsWith("application/json"));
        assertThat(resposta.cabecalho("x-content-type-options")).as(descricao).hasValue("nosniff");
        JsonNode corpo;
        try {
            corpo = JSON.readTree(resposta.corpo());
        } catch (Exception erro) {
            throw new AssertionError(descricao + ": o corpo não é JSON", erro);
        }
        List<String> chaves = new ArrayList<>();
        corpo.fieldNames().forEachRemaining(chaves::add);
        assertThat(chaves).as(descricao).containsAnyOf("codigo").isSubsetOf(Set.of("codigo", "mensagem", "campos"));
        String texto = corpo.path("mensagem").asText().toLowerCase(Locale.ROOT) + " "
                + corpo.path("campos").toString().toLowerCase(Locale.ROOT);
        assertThat(texto).as(descricao).doesNotContain(VAZAMENTOS.toArray(String[]::new));
    }

    private Map<String, Long> conteudo() {
        Map<String, Long> conteudo = new LinkedHashMap<>();
        for (String tabela : TABELAS) {
            conteudo.put(tabela, jdbc.queryForObject("CHECKSUM TABLE " + tabela,
                    (linha, numero) -> linha.getLong("Checksum")));
        }
        return conteudo;
    }

    private static String caminhoCodificado(String ataque) {
        StringBuilder caminho = new StringBuilder();
        for (byte b : ataque.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if (Character.isLetterOrDigit(c) && c < 128) {
                caminho.append(c);
            } else {
                caminho.append(String.format("%%%02X", b & 0xFF));
            }
        }
        return caminho.toString();
    }

    private static String resumo(String ataque) {
        return ataque.length() > 30 ? ataque.substring(0, 10) + "… (" + ataque.length() + " caracteres)" : ataque;
    }

    private ClienteDaApi cliente(String email) {
        ClienteDaApi cliente = new ClienteDaApi(porta);
        Resposta login = cliente.post("/api/auth/login", ContasDoScript.corpoDoLogin(email));
        assertThat(login.status()).as("login de %s", email).isEqualTo(200);
        return cliente;
    }
}
