package br.com.ecoponto.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ecoponto.contrato.Exemplos.Caso;
import br.com.ecoponto.contrato.Exemplos.Pedido;
import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class ContratoTest {

    private static final Pattern ROTA_DA_TABELA = Pattern.compile("`(GET|POST|PUT|DELETE) (/api/[^`\\s]+)`");
    private static final Set<String> CONTAS_DO_SCRIPT =
            Set.of("admin@example.com", "maria.souza@example.com", "joao.pereira@example.com");

    private static OpenAPI api;
    private static OpenApiInteractionValidator validador;
    private static List<Caso> casos;

    @BeforeAll
    static void carregarOContrato() throws IOException {
        String especificacao = Files.readString(Exemplos.PASTA_DA_API.resolve("openapi.yaml"));
        ParseOptions opcoes = new ParseOptions();
        opcoes.setResolve(true);
        SwaggerParseResult lido = new OpenAPIV3Parser().readContents(especificacao, null, opcoes);
        assertThat(lido.getMessages()).as("problemas no openapi.yaml").isEmpty();
        api = lido.getOpenAPI();
        validador = OpenApiInteractionValidator.createForInlineApiSpecification(especificacao).build();
        casos = Exemplos.todos();
    }

    @Test
    void oContratoTemCadaRotaDaTabelaDoBacklogENadaAlem() throws IOException {
        String backlog = Files.readString(Exemplos.PASTA_DA_API.resolve("../backlog-cartoes.md"));
        int inicio = backlog.indexOf("### Rotas propostas");
        int fim = backlog.indexOf("\n## ", inicio);
        Matcher rota = ROTA_DA_TABELA.matcher(backlog.substring(inicio, fim));
        Set<String> daTabela = new TreeSet<>();
        while (rota.find()) {
            daTabela.add(rota.group(1) + " " + rota.group(2));
        }

        assertThat(daTabela).hasSize(21);
        assertThat(operacoes()).isEqualTo(daTabela);
    }

    @Test
    void cadaCasoTemDescricaoPedidosEContaDoScript() {
        assertThat(casos).isNotEmpty();
        for (Caso caso : casos) {
            assertThat(caso.descricao()).as(caso.arquivo()).isNotBlank();
            assertThat(caso.pedidos()).as(caso.arquivo()).isNotEmpty();
            for (Pedido pedido : caso.pedidos()) {
                assertThat(pedido.caminho()).as(caso.arquivo()).startsWith("/api/");
                assertThat(pedido.sessao() == null || CONTAS_DO_SCRIPT.contains(pedido.sessao()))
                        .as("%s: a sessão é de uma conta do script ou de ninguém", caso.arquivo())
                        .isTrue();
            }
            assertThat(caso.corpo() == null).as("%s: só o 204 vem sem corpo", caso.arquivo())
                    .isEqualTo(caso.status() == 204);
        }
    }

    @TestFactory
    Stream<DynamicTest> cadaRespostaDeExemploSegueOContrato() {
        return casos.stream().flatMap(caso -> caso.pedidos().stream().map(pedido -> DynamicTest.dynamicTest(
                caso.arquivo() + ": " + pedido.metodo() + " " + pedido.caminho(),
                () -> semErros(validador.validateResponse(pedido.caminhoSemConsulta(),
                        Request.Method.valueOf(pedido.metodo()), resposta(caso))))));
    }

    @TestFactory
    Stream<DynamicTest> cadaPedidoDosCasosDeSucessoSegueOContrato() {
        return casos.stream()
                .filter(caso -> caso.status() < 300)
                .flatMap(caso -> caso.pedidos().stream().map(pedido -> DynamicTest.dynamicTest(
                        caso.arquivo() + ": " + pedido.metodo() + " " + pedido.caminho(),
                        () -> semErros(validador.validateRequest(requisicao(pedido))))));
    }

    @Test
    void cadaStatusDeCadaRotaTemPeloMenosUmExemplo() {
        Set<String> documentados = new TreeSet<>();
        api.getPaths().forEach((caminho, rota) -> rota.readOperationsMap().forEach((metodo, operacao) ->
                operacao.getResponses().keySet().forEach(status -> documentados.add(metodo + " " + caminho + " "
                        + status))));
        Set<String> comExemplo = casos.stream()
                .flatMap(caso -> caso.pedidos().stream().map(pedido -> pedido.metodo() + " "
                        + modeloDoCaminho(pedido.caminhoSemConsulta()) + " " + caso.status()))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(documentados).as("status de rota sem exemplo").isSubsetOf(comExemplo);
        assertThat(comExemplo).as("exemplo de status que o contrato não documenta").isSubsetOf(documentados);
    }

    @Test
    void oValidadorRecusaUmaRespostaForaDoContrato() {
        String comHash = "{\"idUsuario\":1,\"nome\":\"Administrador EcoPonto\",\"email\":\"admin@example.com\","
                + "\"perfil\":\"ADMIN\",\"senhaHash\":\"$2a$12$qualquer\"}";
        SimpleResponse resposta = SimpleResponse.Builder.status(200)
                .withContentType("application/json").withBody(comHash).build();
        SimpleResponse statusSemContrato = SimpleResponse.Builder.status(418).build();

        assertThat(validador.validateResponse("/api/auth/eu", Request.Method.GET, resposta).hasErrors()).isTrue();
        assertThat(validador.validateResponse("/api/bairros", Request.Method.GET, statusSemContrato).hasErrors())
                .isTrue();
    }

    @Test
    void temExemploDeListaVazia() {
        assertThat(casos).anySatisfy(caso -> {
            assertThat(caso.status()).isEqualTo(200);
            assertThat(caso.corpo().path("total").asInt(-1)).isZero();
            assertThat(caso.corpo().path("itens").isEmpty()).isTrue();
        });
    }

    private static Set<String> operacoes() {
        Set<String> operacoes = new TreeSet<>();
        api.getPaths().forEach((caminho, rota) ->
                rota.readOperationsMap().keySet().forEach(metodo -> operacoes.add(metodo + " " + caminho)));
        return operacoes;
    }

    private static String modeloDoCaminho(String concreto) {
        return api.getPaths().keySet().stream()
                .filter(modelo -> concreto.matches(modelo.replaceAll("\\{[^/]+}", "[^/]+")))
                .min(Comparator.comparingLong(modelo -> modelo.chars().filter(c -> c == '{').count()))
                .orElse(concreto);
    }

    private static SimpleResponse resposta(Caso caso) {
        SimpleResponse.Builder resposta = SimpleResponse.Builder.status(caso.status());
        if (caso.corpo() != null) {
            resposta.withContentType("application/json").withBody(caso.corpo().toString());
        }
        return resposta.build();
    }

    private static SimpleRequest requisicao(Pedido pedido) {
        SimpleRequest.Builder requisicao =
                new SimpleRequest.Builder(Request.Method.valueOf(pedido.metodo()), pedido.caminhoSemConsulta());
        for (String par : pedido.consulta().split("&")) {
            if (!par.isEmpty()) {
                String[] nomeEValor = par.split("=", 2);
                requisicao.withQueryParam(nomeEValor[0],
                        URLDecoder.decode(nomeEValor.length > 1 ? nomeEValor[1] : "", StandardCharsets.UTF_8));
            }
        }
        if (pedido.sessao() != null) {
            requisicao.withHeader("Cookie", "JSESSIONID=sessao-de-" + pedido.sessao());
        }
        if (pedido.corpo() != null) {
            requisicao.withContentType("application/json").withBody(pedido.corpo().toString());
        }
        return requisicao.build();
    }

    private static void semErros(ValidationReport relatorio) {
        assertThat(relatorio.getMessages())
                .as("o que não bate com o openapi.yaml")
                .filteredOn(mensagem -> mensagem.getLevel() == ValidationReport.Level.ERROR)
                .extracting(ValidationReport.Message::getMessage)
                .isEmpty();
    }
}
