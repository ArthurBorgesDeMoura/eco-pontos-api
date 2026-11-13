package br.com.ecoponto.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SugestaoController.class)
@Import({SegurancaConfig.class, RespostasDeSeguranca.class})
@ActiveProfiles("test")
class SugestaoControllerTest {

    private static final String CORPO = Exemplos.caso("sugerir-criado").pedidos().get(0).corpo().toString();

    @Autowired
    private MockMvc mvc;

    @MockBean
    private SugestaoDePontos sugestoes;

    @MockBean
    private BuscaDePontos busca;

    @Test
    void oAutorEODaSessaoEARespostaE201ComoOContrato() throws Exception {
        Caso caso = Exemplos.caso("sugerir-criado");
        when(sugestoes.sugerir(any(), anyInt())).thenReturn(mercado());

        mvc.perform(post("/api/pontos").with(ContasDoScript.sessaoDe(ContasDoScript.MARIA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isCreated())
                .andExpect(content().json(caso.corpo().toString(), true));

        ArgumentCaptor<PontoEntrada> entrada = ArgumentCaptor.forClass(PontoEntrada.class);
        verify(sugestoes).sugerir(entrada.capture(), eq(2));
        assertThat(entrada.getValue()).isEqualTo(
                new PontoEntrada("Mercado do Bairro", "Rua das Flores, 50", 20, "8h às 18h", null, List.of(6, 2)));
    }

    @Test
    void oAdminTambemSugereComoEleMesmo() throws Exception {
        when(sugestoes.sugerir(any(), anyInt())).thenReturn(mercado());

        mvc.perform(post("/api/pontos").with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isCreated());

        verify(sugestoes).sugerir(any(), eq(1));
    }

    @Test
    void semLoginDa401ComoOContrato() throws Exception {
        mvc.perform(post("/api/pontos").with(ContasDoScript.sessaoDe(null)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(Exemplos.caso("sem-login").corpo().toString(), true));

        verify(sugestoes, never()).sugerir(any(), anyInt());
    }

    @Test
    void semTokenCsrfNaoChegaASugestao() throws Exception {
        mvc.perform(post("/api/pontos").with(ContasDoScript.sessaoDe(ContasDoScript.MARIA))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isForbidden());

        verify(sugestoes, never()).sugerir(any(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{", "[]", "\"texto\"", "{\"nome\":"})
    void corpoQueNaoEUmObjetoJsonDa400(String corpo) throws Exception {
        mvc.perform(post("/api/pontos").with(ContasDoScript.sessaoDe(ContasDoScript.MARIA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"codigo\":\"DADOS_INVALIDOS\"}"));

        verify(sugestoes, never()).sugerir(any(), anyInt());
    }

    @Test
    void campoComOTipoErradoDa400NoCampo() throws Exception {
        Map<String, String> corpos = Map.of(
                "idBairro", "{\"nome\":\"Mercado\",\"endereco\":\"Rua 1\",\"idBairro\":\"Tibery\",\"categorias\":[2]}",
                "categorias", "{\"nome\":\"Mercado\",\"endereco\":\"Rua 1\",\"idBairro\":20,\"categorias\":\"2\"}");
        for (Map.Entry<String, String> caso : corpos.entrySet()) {
            mvc.perform(post("/api/pontos").with(ContasDoScript.sessaoDe(ContasDoScript.MARIA)).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(caso.getValue()))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"codigo\":\"DADOS_INVALIDOS\",\"mensagem\":"
                            + "\"Confira os campos destacados.\",\"campos\":{\"" + caso.getKey()
                            + "\":\"Valor inválido.\"}}", true));
        }
        verify(sugestoes, never()).sugerir(any(), anyInt());
    }

    @Test
    void oErroDaConferenciaSaiComoOContrato() throws Exception {
        Caso caso = Exemplos.caso("sugerir-bairro-e-materiais-invalidos");
        when(sugestoes.sugerir(any(), anyInt())).thenThrow(new ErroDaApi(CodigoDeErro.DADOS_INVALIDOS,
                "Confira os campos destacados.", Map.of("nome", "Informe o nome do ponto.",
                        "idBairro", "Escolha um bairro da lista.",
                        "categorias", "Escolha só materiais da lista, sem repetir.")));

        mvc.perform(post("/api/pontos").with(ContasDoScript.sessaoDe(ContasDoScript.MARIA)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(caso.pedidos().get(0).corpo().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(caso.corpo().toString(), true));
    }

    @Test
    void minhasSugestoesSaoAsDaSessaoMesmoComIdNaUrl() throws Exception {
        when(busca.minhasSugestoes(anyInt(), any(), anyInt(), anyInt())).thenReturn(new Pagina<>(List.of(), 0, 1, 7));

        mvc.perform(get("/api/minhas-sugestoes?idUsuario=3&autor=3&usuario=3")
                        .with(ContasDoScript.sessaoDe(ContasDoScript.MARIA)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"itens\":[],\"total\":0,\"pagina\":1,\"tamanho\":7}", true));
        mvc.perform(get("/api/minhas-sugestoes").with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isOk());

        verify(busca).minhasSugestoes(2, null, 1, 7);
        verify(busca).minhasSugestoes(1, null, 1, 7);
    }

    @Test
    void oStatusEAPaginaChegamAoServico() throws Exception {
        when(busca.minhasSugestoes(anyInt(), any(), anyInt(), anyInt())).thenReturn(new Pagina<>(List.of(), 0, 2, 3));
        for (StatusDoPonto status : StatusDoPonto.values()) {
            mvc.perform(get("/api/minhas-sugestoes").param("status", status.name()).param("pagina", "2")
                            .param("tamanho", "3").with(ContasDoScript.sessaoDe(ContasDoScript.JOAO)))
                    .andExpect(status().isOk());

            verify(busca).minhasSugestoes(3, status, 2, 3);
        }
    }

    @Test
    void statusForaDaListaDa400ComoOContrato() throws Exception {
        Caso caso = Exemplos.caso("minhas-sugestoes-status-invalido");
        mvc.perform(get(caso.pedidos().get(0).caminho()).with(ContasDoScript.sessaoDe(ContasDoScript.MARIA)))
                .andExpect(status().is(caso.status()))
                .andExpect(content().json(caso.corpo().toString(), true));

        for (String status : new String[] {"pendente", "Aprovado", " PENDENTE", "TODOS", "", "PENDENTE' OR '1'='1"}) {
            mvc.perform(get("/api/minhas-sugestoes").param("status", status)
                            .with(ContasDoScript.sessaoDe(ContasDoScript.MARIA)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json(caso.corpo().toString(), true));
        }
        mvc.perform(get("/api/minhas-sugestoes").param("status", "PENDENTE", "APROVADO")
                        .with(ContasDoScript.sessaoDe(ContasDoScript.MARIA)))
                .andExpect(status().isBadRequest());
        verify(busca, never()).minhasSugestoes(anyInt(), any(), anyInt(), anyInt());
    }

    @Test
    void paginaETamanhoInvalidosDao400JuntoComOStatus() throws Exception {
        mvc.perform(get("/api/minhas-sugestoes?status=X&pagina=0&tamanho=51")
                        .with(ContasDoScript.sessaoDe(ContasDoScript.MARIA)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"codigo\":\"DADOS_INVALIDOS\",\"mensagem\":\"Confira os filtros.\","
                        + "\"campos\":{\"status\":\"Use PENDENTE, APROVADO ou RECUSADO.\","
                        + "\"pagina\":\"A página começa em 1.\",\"tamanho\":\"O tamanho vai de 1 a 50.\"}}", true));

        verify(busca, never()).minhasSugestoes(anyInt(), any(), anyInt(), anyInt());
    }

    @Test
    void minhasSugestoesSemLoginDa401() throws Exception {
        for (String caminho : new String[] {"/api/minhas-sugestoes", "/api/minhas-sugestoes?status=X"}) {
            mvc.perform(get(caminho).with(ContasDoScript.sessaoDe(null)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json(Exemplos.caso("sem-login").corpo().toString(), true));
        }
        verify(busca, never()).minhasSugestoes(anyInt(), any(), anyInt(), anyInt());
    }

    private static PontoCriado mercado() {
        return new PontoCriado(16, "Mercado do Bairro", "Rua das Flores, 50", "8h às 18h", null,
                new BairroSaida(20, "Tibery"), List.of(
                        new CategoriaSaida(2, "Plástico",
                                "Garrafas PET, potes, baldes e frascos de limpeza e higiene."),
                        new CategoriaSaida(6, "Pilhas e baterias",
                                "Pilhas e baterias usadas, que não podem ir para o lixo comum.")),
                StatusDoPonto.PENDENTE);
    }
}
