package br.com.ecoponto.erro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ecoponto.categoria.CategoriaEntrada;
import br.com.ecoponto.conta.CadastroEntrada;
import br.com.ecoponto.contrato.Exemplos;
import br.com.ecoponto.contrato.Exemplos.Caso;
import br.com.ecoponto.contrato.Exemplos.Pedido;
import br.com.ecoponto.ponto.PontoEntrada;
import br.com.ecoponto.seguranca.ContasDoScript;
import br.com.ecoponto.seguranca.RespostasDeSeguranca;
import br.com.ecoponto.seguranca.SegurancaConfig;
import br.com.ecoponto.validacao.Filtros;
import br.com.ecoponto.validacao.ValidadorDeEntradas;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(controllers = ErroForaDoMvcController.class)
@Import({TratamentoDeErrosTest.RotasDeTeste.class, ValidadorDeEntradas.class, SegurancaConfig.class,
    RespostasDeSeguranca.class})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class TratamentoDeErrosTest {

    @Autowired
    private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {
        "cadastro-dados-invalidos",
        "sugerir-dados-invalidos",
        "sugerir-bairro-e-materiais-invalidos",
        "admin-editar-ponto-dados-invalidos",
        "admin-categoria-dados-invalidos",
        "admin-categoria-nome-longo",
        "admin-categoria-id-invalido",
        "admin-ponto-id-invalido",
        "ponto-id-invalido",
        "pontos-filtros-invalidos",
        "contagem-bairro-invalido",
        "minhas-sugestoes-status-invalido",
        "admin-pontos-filtros-invalidos",
        "admin-categorias-tamanho-invalido"
    })
    void respondeComoOsExemplosDoContrato(String nome) throws Exception {
        Caso caso = Exemplos.caso(nome);
        for (Pedido pedido : caso.pedidos()) {
            mvc.perform(montar(pedido))
                    .andExpect(status().is(caso.status()))
                    .andExpect(content().json(caso.corpo().toString(), true));
        }
    }

    @Test
    void corpoValidoChegaARotaSemAsPontasEComOpcionalVazioComoNull() throws Exception {
        mvc.perform(post("/api/admin/categorias").with(csrf()).with(comoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"\\u00a0 Cápsulas de café \",\"descricao\":\"   \",\"status\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Cápsulas de café"))
                .andExpect(jsonPath("$.descricao").isEmpty());
    }

    @Test
    void comRestricaoNoIdOErroDoCorpoContinuaNosCampos() throws Exception {
        Caso caso = Exemplos.caso("sugerir-dados-invalidos");
        String corpoInvalido = caso.pedidos().get(0).corpo().toString();

        mvc.perform(put("/api/teste/pontos/4").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpoInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(caso.corpo().toString(), true));
        mvc.perform(put("/api/teste/pontos/0").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpoInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(
                        "{\"codigo\":\"DADOS_INVALIDOS\",\"mensagem\":\"Use o id de um ponto.\"}", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0x0D", "+13", "%2013", "0", "1.0"})
    void idDoCaminhoQueOSpringConverteriaDa400(String id) throws Exception {
        mvc.perform(get(URI.create("/api/pontos/" + id)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(Exemplos.caso("ponto-id-invalido").corpo().toString(), true));
        mvc.perform(put(URI.create("/api/admin/categorias/" + id)).with(csrf()).with(comoAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"   \",\"descricao\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(Exemplos.caso("admin-categoria-id-invalido").corpo().toString(), true));
    }

    @Test
    void comRestricaoNoParametroOCampoTemONomeDaUrl() throws Exception {
        mvc.perform(get("/api/teste/consulta").queryParam("bairro", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"codigo\":\"DADOS_INVALIDOS\",\"mensagem\":\"Confira os filtros.\","
                        + "\"campos\":{\"bairro\":\"Use o id de um bairro.\"}}", true));
    }

    @Test
    void parametroTipadoQueNaoConverteApontaOParametro() throws Exception {
        mvc.perform(get("/api/teste/tipado").queryParam("bairro", "centro"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"codigo\":\"DADOS_INVALIDOS\",\"mensagem\":\"Confira os filtros.\","
                        + "\"campos\":{\"bairro\":\"Use o id de um bairro.\"}}", true));
    }

    @Test
    void jsonMalformadoDa400SemMostrarNemRegistrarOCorpo(CapturedOutput saida) throws Exception {
        mvc.perform(post("/api/auth/cadastro").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"maria@example.com\",\"senha\": SenhaSemAspas123"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"codigo\":\"DADOS_INVALIDOS\","
                        + "\"mensagem\":\"Envie um JSON válido no corpo da requisição.\"}", true));

        assertThat(saida.getAll()).doesNotContain("SenhaSemAspas123");
    }

    @Test
    void campoComTipoErradoApontaOCampo() throws Exception {
        mvc.perform(post("/api/admin/categorias").with(csrf()).with(comoAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":[\"Vidro\"],\"descricao\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                .andExpect(jsonPath("$.campos.nome").value("Valor inválido."));
    }

    @Test
    void rotaQueNaoExisteDa404NoFormatoDoContrato() throws Exception {
        mvc.perform(get("/api/nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(content().json(
                        "{\"codigo\":\"NAO_ENCONTRADO\",\"mensagem\":\"Não encontramos o que você procurou.\"}", true));
    }

    @Test
    void metodoNaoAceitoDa405ComOsMetodosDaRota() throws Exception {
        mvc.perform(delete("/api/auth/cadastro").with(csrf()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.codigo").value("METODO_NAO_PERMITIDO"));
    }

    @Test
    void corpoQueNaoEJsonDa415() throws Exception {
        mvc.perform(post("/api/auth/cadastro").with(csrf()).contentType(MediaType.TEXT_PLAIN).content("nome=Ana"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.codigo").value("TIPO_NAO_SUPORTADO"));
    }

    @Test
    void erroDaApiSaiComOStatusEOsCamposDoCodigo() throws Exception {
        mvc.perform(get("/api/teste/erro-da-api"))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"codigo\":\"NOME_REPETIDO\","
                        + "\"mensagem\":\"Já existe uma categoria com este nome.\","
                        + "\"campos\":{\"nome\":\"Já existe uma categoria com este nome.\"}}", true));
    }

    @Test
    void loginBloqueadoSaiCom429ERetryAfter() throws Exception {
        mvc.perform(get("/api/teste/bloqueado"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "300"))
                .andExpect(content().json(Exemplos.caso("login-bloqueado").corpo().toString(), true));
    }

    @Test
    void erroDoSpringMantemOStatus() throws Exception {
        mvc.perform(get("/api/teste/conflito-do-spring"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLITO"));
    }

    @Test
    void bancoQueNaoRespondeDa503() throws Exception {
        mvc.perform(get("/api/teste/sem-banco"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"codigo\":\"BANCO_INDISPONIVEL\","
                        + "\"mensagem\":\"O banco de dados não respondeu. Tente de novo em instantes.\"}", true));
    }

    @Test
    void transacaoQueNaoAbrePorOutroMotivoDa500() throws Exception {
        mvc.perform(get("/api/teste/transacao-sem-motivo-de-banco"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"));
    }

    @Test
    void erroInesperadoDa500SemDetalheInterno() throws Exception {
        mvc.perform(get("/api/teste/falha"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json(
                        "{\"codigo\":\"ERRO_INTERNO\",\"mensagem\":\"Algo deu errado. Tente de novo em instantes.\"}",
                        true));
    }

    @Test
    void erroForaDoMvcTambemSaiNoFormatoDoContrato() throws Exception {
        mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"));
        mvc.perform(get("/error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    private static RequestPostProcessor comoAdmin() {
        return ContasDoScript.sessaoDe(ContasDoScript.ADMIN);
    }

    private static MockHttpServletRequestBuilder montar(Pedido pedido) {
        MockHttpServletRequestBuilder requisicao = request(HttpMethod.valueOf(pedido.metodo()), pedido.caminho())
                .with(ContasDoScript.sessaoDe(pedido.sessao()));
        if (!"GET".equals(pedido.metodo())) {
            requisicao.with(csrf());
        }
        if (pedido.corpo() != null) {
            requisicao.contentType(MediaType.APPLICATION_JSON).content(pedido.corpo().toString());
        }
        return requisicao;
    }

    @RestController
    static class RotasDeTeste {

        private static final Set<Integer> BAIRROS = IntStream.rangeClosed(1, 20).boxed().collect(Collectors.toSet());
        private static final Set<Integer> CATEGORIAS =
                IntStream.rangeClosed(1, 10).boxed().collect(Collectors.toSet());

        private final ValidadorDeEntradas validador;

        RotasDeTeste(ValidadorDeEntradas validador) {
            this.validador = validador;
        }

        @PostMapping("/api/auth/cadastro")
        CadastroEntrada cadastrar(@Valid @RequestBody CadastroEntrada entrada) {
            return entrada;
        }

        @GetMapping("/api/pontos")
        Map<String, Integer> buscar(@RequestParam(required = false) String bairro,
                @RequestParam(required = false) String categoria, @RequestParam(required = false) String pagina,
                @RequestParam(required = false) String tamanho) {
            Filtros filtros = new Filtros();
            filtros.id("bairro", bairro, Filtros.ID_DE_BAIRRO);
            filtros.id("categoria", categoria, Filtros.ID_DE_CATEGORIA);
            filtros.pagina(pagina);
            filtros.tamanho(tamanho);
            filtros.conferir("Confira os filtros da busca.");
            return Map.of();
        }

        @GetMapping("/api/pontos/contagem")
        Map<String, Integer> contar(@RequestParam(required = false) String bairro) {
            Filtros filtros = new Filtros();
            filtros.id("bairro", bairro, Filtros.ID_DE_BAIRRO);
            filtros.conferir("Confira os filtros da busca.");
            return Map.of();
        }

        @GetMapping("/api/pontos/{idPonto}")
        Map<String, Integer> detalhar(@PathVariable String idPonto) {
            return Map.of("idPonto", Filtros.idDoCaminho(idPonto, Filtros.ID_DE_PONTO));
        }

        @PostMapping("/api/pontos")
        PontoEntrada sugerir(@RequestBody PontoEntrada entrada) {
            return conferirComOBanco(entrada);
        }

        @GetMapping("/api/minhas-sugestoes")
        Map<String, Integer> minhasSugestoes(@RequestParam(required = false) String status,
                @RequestParam(required = false) String pagina, @RequestParam(required = false) String tamanho) {
            Filtros filtros = new Filtros();
            filtros.opcao("status", status, Filtros.STATUS_DO_PONTO, null, Filtros.STATUS);
            filtros.pagina(pagina);
            filtros.tamanho(tamanho);
            filtros.conferir(TratamentoDeErros.CONFIRA_OS_FILTROS);
            return Map.of();
        }

        @GetMapping("/api/admin/pontos")
        Map<String, Integer> pontosDoPainel(@RequestParam(required = false) String status,
                @RequestParam(required = false) String pagina, @RequestParam(required = false) String tamanho) {
            Filtros filtros = new Filtros();
            filtros.opcao("status", status, Filtros.STATUS_DO_PONTO, "PENDENTE", Filtros.STATUS);
            filtros.pagina(pagina);
            filtros.tamanho(tamanho);
            filtros.conferir(TratamentoDeErros.CONFIRA_OS_FILTROS);
            return Map.of();
        }

        @PostMapping("/api/admin/pontos/{idPonto}/aprovar")
        Map<String, Integer> aprovar(@PathVariable String idPonto) {
            return Map.of("idPonto", Filtros.idDoCaminho(idPonto, Filtros.ID_DE_PONTO));
        }

        @PostMapping("/api/admin/pontos/{idPonto}/recusar")
        Map<String, Integer> recusar(@PathVariable String idPonto) {
            return Map.of("idPonto", Filtros.idDoCaminho(idPonto, Filtros.ID_DE_PONTO));
        }

        @PutMapping("/api/admin/pontos/{idPonto}")
        PontoEntrada editarPonto(@PathVariable String idPonto, @RequestBody PontoEntrada entrada) {
            Filtros.idDoCaminho(idPonto, Filtros.ID_DE_PONTO);
            return conferirComOBanco(entrada);
        }

        @DeleteMapping("/api/admin/pontos/{idPonto}")
        Map<String, Integer> excluirPonto(@PathVariable String idPonto) {
            return Map.of("idPonto", Filtros.idDoCaminho(idPonto, Filtros.ID_DE_PONTO));
        }

        @GetMapping("/api/admin/categorias")
        Map<String, Integer> categoriasDoPainel(@RequestParam(required = false) String pagina,
                @RequestParam(required = false) String tamanho) {
            Filtros filtros = new Filtros();
            filtros.pagina(pagina);
            filtros.tamanho(tamanho);
            filtros.conferir(TratamentoDeErros.CONFIRA_OS_FILTROS);
            return Map.of();
        }

        @PostMapping("/api/admin/categorias")
        CategoriaEntrada criarCategoria(@Valid @RequestBody CategoriaEntrada entrada) {
            return entrada;
        }

        @PutMapping("/api/admin/categorias/{idCategoria}")
        CategoriaEntrada editarCategoria(@PathVariable String idCategoria, @RequestBody CategoriaEntrada entrada) {
            Filtros.idDoCaminho(idCategoria, Filtros.ID_DE_CATEGORIA);
            ValidadorDeEntradas.exigirSemErros(validador.erros(entrada));
            return entrada;
        }

        @PutMapping("/api/teste/pontos/{idPonto}")
        PontoEntrada comRestricaoNoId(@PathVariable @Positive(message = "Use o id de um ponto.") Integer idPonto,
                @Valid @RequestBody PontoEntrada entrada) {
            return entrada;
        }

        @GetMapping("/api/teste/consulta")
        Map<String, Integer> comRestricaoNoParametro(
                @RequestParam("bairro") @Positive(message = "Use o id de um bairro.") Integer idBairro) {
            return Map.of("bairro", idBairro);
        }

        @GetMapping("/api/teste/tipado")
        Map<String, Integer> tipado(@RequestParam(required = false) Integer bairro) {
            return Map.of();
        }

        @GetMapping("/api/teste/erro-da-api")
        void erroDaApi() {
            String mensagem = "Já existe uma categoria com este nome.";
            throw new ErroDaApi(CodigoDeErro.NOME_REPETIDO, mensagem, Map.of("nome", mensagem));
        }

        @GetMapping("/api/teste/bloqueado")
        void bloqueado() {
            throw ErroDaApi.muitasTentativas(Duration.ofMinutes(5));
        }

        @GetMapping("/api/teste/conflito-do-spring")
        void conflitoDoSpring() {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        @GetMapping("/api/teste/sem-banco")
        void semBanco() {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection");
        }

        @GetMapping("/api/teste/transacao-sem-motivo-de-banco")
        void transacaoSemMotivoDeBanco() {
            throw new CannotCreateTransactionException("isolamento não suportado",
                    new IllegalStateException("configuração"));
        }

        @GetMapping("/api/teste/falha")
        void falha() {
            throw new IllegalStateException("detalhe interno que não pode sair na resposta");
        }

        private PontoEntrada conferirComOBanco(PontoEntrada entrada) {
            Map<String, String> campos = validador.erros(entrada);
            if (entrada.idBairro() != null && !BAIRROS.contains(entrada.idBairro())) {
                campos.putIfAbsent("idBairro", "Escolha um bairro da lista.");
            }
            if (!campos.containsKey("categorias") && !CATEGORIAS.containsAll(entrada.categorias())) {
                campos.put("categorias", "Escolha só materiais da lista, sem repetir.");
            }
            ValidadorDeEntradas.exigirSemErros(campos);
            return entrada;
        }
    }
}
