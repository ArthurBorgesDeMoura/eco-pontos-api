package br.com.ecoponto.ponto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ecoponto.bairro.BairroSaida;
import br.com.ecoponto.categoria.CategoriaSaida;
import br.com.ecoponto.contrato.Exemplos;
import br.com.ecoponto.contrato.Exemplos.Caso;
import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import br.com.ecoponto.lista.Pagina;
import br.com.ecoponto.seguranca.ContasDoScript;
import br.com.ecoponto.seguranca.RespostasDeSeguranca;
import br.com.ecoponto.seguranca.SegurancaConfig;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PontoController.class)
@Import({SegurancaConfig.class, RespostasDeSeguranca.class})
@ActiveProfiles("test")
class PontoControllerTest {

    private static final String[] NAO_SAO_ID = {
        "1 OR 1=1", "1;DROP TABLE ponto_coleta", "'", "1'--", "-1", "0", "9999999999", "1e3", " 1", "",
        "١"
    };

    @Autowired
    private MockMvc mvc;

    @MockBean
    private BuscaDePontos busca;

    @Test
    void parametrosInvalidosDao400ComoOContratoSemChegarABusca() throws Exception {
        Caso caso = Exemplos.caso("pontos-filtros-invalidos");

        mvc.perform(get(caso.pedidos().get(0).caminho()))
                .andExpect(status().is(caso.status()))
                .andExpect(content().json(caso.corpo().toString(), true));
        verify(busca, never()).buscar(any(), any(), anyInt(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bairro", "categoria"})
    void textoQueNaoEIdDa400NoParametroSemChegarABusca(String parametro) throws Exception {
        String mensagem = parametro.equals("bairro") ? "Use o id de um bairro." : "Use o id de uma categoria.";
        for (String valor : NAO_SAO_ID) {
            mvc.perform(get("/api/pontos").param(parametro, valor))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                    .andExpect(jsonPath("$.campos." + parametro).value(mensagem));
        }
        mvc.perform(get("/api/pontos").param(parametro, "1", "2"))
                .andExpect(status().isBadRequest());

        verify(busca, never()).buscar(any(), any(), anyInt(), anyInt());
    }

    @Test
    void paginaETamanhoForaDosLimitesDao400() throws Exception {
        mvc.perform(get("/api/pontos").param("pagina", "0").param("tamanho", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.pagina").value("A página começa em 1."))
                .andExpect(jsonPath("$.campos.tamanho").value("O tamanho vai de 1 a 50."));
        verify(busca, never()).buscar(any(), any(), anyInt(), anyInt());
    }

    @Test
    void semParametrosBuscaAPrimeiraPaginaDe7SemFiltros() throws Exception {
        when(busca.buscar(null, null, 1, 7)).thenReturn(new Pagina<>(List.of(), 0, 1, 7));

        mvc.perform(get("/api/pontos"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"itens\":[],\"total\":0,\"pagina\":1,\"tamanho\":7}", true));
    }

    @Test
    void osFiltrosChegamABuscaEAPaginaSaiNoFormatoDoContrato() throws Exception {
        PontoResumo ponto = new PontoResumo(4, "Bretas - Loja 518", "Av. João Pinheiro, 77",
                new BairroSaida(4, "Centro"), List.of(new MaterialSaida(7, "Lâmpadas")));
        when(busca.buscar(4, 7, 2, 50)).thenReturn(new Pagina<>(List.of(ponto), 51, 2, 50));

        mvc.perform(get("/api/pontos?bairro=4&categoria=7&pagina=2&tamanho=50"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"itens\":[{\"idPonto\":4,\"nome\":\"Bretas - Loja 518\","
                        + "\"endereco\":\"Av. João Pinheiro, 77\",\"bairro\":{\"idBairro\":4,\"nome\":\"Centro\"},"
                        + "\"materiais\":[{\"idCategoria\":7,\"nome\":\"Lâmpadas\"}]}],"
                        + "\"total\":51,\"pagina\":2,\"tamanho\":50}", true));
    }

    @Test
    void contagemComBairroInvalidoDa400ComoOContratoSemChegarABusca() throws Exception {
        Caso caso = Exemplos.caso("contagem-bairro-invalido");

        mvc.perform(get(caso.pedidos().get(0).caminho()))
                .andExpect(status().is(caso.status()))
                .andExpect(content().json(caso.corpo().toString(), true));
        verify(busca, never()).contar(any());
    }

    @Test
    void contagemComTextoQueNaoEIdDa400SemChegarABusca() throws Exception {
        for (String valor : NAO_SAO_ID) {
            mvc.perform(get("/api/pontos/contagem").param("bairro", valor))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DADOS_INVALIDOS"))
                    .andExpect(jsonPath("$.mensagem").value("Confira os filtros da busca."))
                    .andExpect(jsonPath("$.campos.bairro").value("Use o id de um bairro."));
        }
        mvc.perform(get("/api/pontos/contagem").param("bairro", "1", "2"))
                .andExpect(status().isBadRequest());

        verify(busca, never()).contar(any());
    }

    @Test
    void contagemSemBairroContaTodosESaiNoFormatoDoContrato() throws Exception {
        when(busca.contar(null)).thenReturn(new ContagemDePontos(13, List.of(
                new ContagemDoMaterial(1, 3), new ContagemDoMaterial(2, 0))));

        mvc.perform(get("/api/pontos/contagem"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"total\":13,\"categorias\":["
                        + "{\"idCategoria\":1,\"quantidade\":3},{\"idCategoria\":2,\"quantidade\":0}]}", true));
    }

    @Test
    void contagemLevaSoOBairroABusca() throws Exception {
        when(busca.contar(4)).thenReturn(new ContagemDePontos(2, List.of()));

        mvc.perform(get("/api/pontos/contagem?bairro=4&categoria=x&pagina=0"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"total\":2,\"categorias\":[]}", true));
        verify(busca).contar(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "9999999999", "1%20OR%201=1", "%27", "1'--", "13abc",
        "0x0D", "%230D", "+13", "%2013", "13%20", "1e1", "%D9%A1%D9%A3"})
    void idQueNaoEPositivoDa400ComAMensagemDoContratoSemChegarABusca(String id) throws Exception {
        mvc.perform(get(URI.create("/api/pontos/" + id)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(Exemplos.caso("ponto-id-invalido").corpo().toString(), true));
        verify(busca, never()).detalhar(anyInt(), anyBoolean());
    }

    @Test
    void idComPontoEVirgulaNemChegaARota() throws Exception {
        mvc.perform(get(URI.create("/api/pontos/1;DROP%20TABLE%20ponto_coleta")))
                .andExpect(status().isBadRequest());
        verify(busca, never()).detalhar(anyInt(), anyBoolean());
    }

    @Test
    void idInvalidoSaiComoOContrato() throws Exception {
        Caso caso = Exemplos.caso("ponto-id-invalido");

        mvc.perform(get(caso.pedidos().get(0).caminho()))
                .andExpect(status().is(caso.status()))
                .andExpect(content().json(caso.corpo().toString(), true));
    }

    @Test
    void soOAdminDaSessaoPedeOPontoEmQualquerStatus() throws Exception {
        when(busca.detalhar(anyInt(), anyBoolean())).thenReturn(luizote());

        mvc.perform(get("/api/pontos/13")).andExpect(status().isOk());
        verify(busca).detalhar(13, false);
        mvc.perform(get("/api/pontos/14").with(ContasDoScript.sessaoDe(ContasDoScript.MARIA)))
                .andExpect(status().isOk());
        verify(busca).detalhar(14, false);
        mvc.perform(get("/api/pontos/15").with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isOk());
        verify(busca).detalhar(15, true);
    }

    @Test
    void perfilEnviadoNoPedidoNaoFazNinguemAdmin() throws Exception {
        when(busca.detalhar(anyInt(), anyBoolean())).thenReturn(luizote());

        mvc.perform(get("/api/pontos/14?perfil=ADMIN").header("X-Perfil", "ADMIN")
                        .with(ContasDoScript.sessaoDe(ContasDoScript.JOAO)))
                .andExpect(status().isOk());
        verify(busca).detalhar(14, false);
    }

    @Test
    void oDetalheSaiNoFormatoDoContratoComOsVaziosComoNull() throws Exception {
        when(busca.detalhar(13, false)).thenReturn(luizote());

        mvc.perform(get("/api/pontos/13"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"idPonto\":13,\"nome\":\"Ecoponto Luizote de Freitas\","
                        + "\"endereco\":\"Rua Wilson Gonçalves de Souza, 10\","
                        + "\"horario\":\"7h às 19h, todos os dias\",\"contato\":null,"
                        + "\"bairro\":{\"idBairro\":11,\"nome\":\"Luizote de Freitas\"},"
                        + "\"materiais\":[{\"idCategoria\":1,\"nome\":\"Papel e papelão\",\"descricao\":null}]}",
                        true));
    }

    @Test
    void pontoQueNaoAchaDa404ComoOContrato() throws Exception {
        Caso caso = Exemplos.caso("ponto-nao-encontrado");
        when(busca.detalhar(anyInt(), anyBoolean()))
                .thenThrow(new ErroDaApi(CodigoDeErro.NAO_ENCONTRADO, BuscaDePontos.PONTO_NAO_ENCONTRADO));

        mvc.perform(get("/api/pontos/999"))
                .andExpect(status().is(caso.status()))
                .andExpect(content().json(caso.corpo().toString(), true));
    }

    private static PontoDetalhe luizote() {
        return new PontoDetalhe(13, "Ecoponto Luizote de Freitas", "Rua Wilson Gonçalves de Souza, 10",
                "7h às 19h, todos os dias", null, new BairroSaida(11, "Luizote de Freitas"),
                List.of(new CategoriaSaida(1, "Papel e papelão", null)));
    }
}
