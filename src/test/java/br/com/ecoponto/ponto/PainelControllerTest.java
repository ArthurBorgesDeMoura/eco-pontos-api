package br.com.ecoponto.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.net.URI;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(PainelController.class)
@Import({SegurancaConfig.class, RespostasDeSeguranca.class})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class PainelControllerTest {

    private static final String CORPO_DA_EDICAO =
            Exemplos.caso("admin-editar-ponto").pedidos().get(0).corpo().toString();

    @Autowired
    private MockMvc mvc;

    @MockBean
    private BuscaDePontos busca;

    @MockBean
    private PainelDePontos painel;

    @Test
    void visitanteEUsuarioComumNaoAcessamNenhumaRota() throws Exception {
        String semLogin = Exemplos.caso("sem-login").corpo().toString();
        String semPermissao = Exemplos.caso("sem-permissao").corpo().toString();
        List<Supplier<MockHttpServletRequestBuilder>> rotas = List.of(() -> get("/api/admin/resumo"),
                () -> get("/api/admin/pontos"), () -> post("/api/admin/pontos/14/aprovar"),
                () -> post("/api/admin/pontos/14/recusar"),
                () -> put("/api/admin/pontos/4").contentType(MediaType.APPLICATION_JSON).content(CORPO_DA_EDICAO),
                () -> delete("/api/admin/pontos/4"));
        for (Supplier<MockHttpServletRequestBuilder> rota : rotas) {
            mvc.perform(rota.get().with(ContasDoScript.sessaoDe(null)).with(csrf()))
                    .andExpect(status().isUnauthorized()).andExpect(content().json(semLogin, true));
            for (String usuario : List.of(ContasDoScript.MARIA, ContasDoScript.JOAO)) {
                mvc.perform(rota.get().with(ContasDoScript.sessaoDe(usuario)).with(csrf()))
                        .andExpect(status().isForbidden()).andExpect(content().json(semPermissao, true));
            }
        }
        verifyNoInteractions(busca, painel);
    }

    @Test
    void semStatusAListaEADosPendentesNaPagina1De7() throws Exception {
        when(busca.pontosDoPainel(any(), anyInt(), anyInt())).thenReturn(new Pagina<>(List.of(), 0, 1, 7));

        mvc.perform(get("/api/admin/pontos").with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"itens\":[],\"total\":0,\"pagina\":1,\"tamanho\":7}", true));
        mvc.perform(get("/api/admin/pontos?status=APROVADO&pagina=2&tamanho=5")
                        .with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isOk());

        verify(busca).pontosDoPainel(StatusDoPonto.PENDENTE, 1, 7);
        verify(busca).pontosDoPainel(StatusDoPonto.APROVADO, 2, 5);
    }

    @Test
    void filtrosInvalidosDao400ComoOContrato() throws Exception {
        Caso caso = Exemplos.caso("admin-pontos-filtros-invalidos");
        mvc.perform(get(caso.pedidos().get(0).caminho()).with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().is(caso.status()))
                .andExpect(content().json(caso.corpo().toString(), true));

        for (String status : new String[] {"pendente", "", "TODOS", "PENDENTE' OR '1'='1"}) {
            mvc.perform(get("/api/admin/pontos").param("status", status)
                            .with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"campos\":{\"status\":\"Use PENDENTE, APROVADO ou RECUSADO.\"}}"));
        }
        verify(busca, never()).pontosDoPainel(any(), anyInt(), anyInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-14", "0x0E", "+14", "%2014", "14%20", "1e1", "9999999999"})
    void idQueNaoEPositivoDa400SemChegarADecisao(String id) throws Exception {
        String corpo = Exemplos.caso("admin-ponto-id-invalido").corpo().toString();
        for (String decisao : List.of("aprovar", "recusar")) {
            mvc.perform(post(URI.create("/api/admin/pontos/" + id + "/" + decisao)).with(csrf())
                            .with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json(corpo, true));
        }
        verifyNoInteractions(painel);
    }

    @Test
    void aprovarERecusarChamamADecisaoCertaERegistramNoLog(CapturedOutput saida) throws Exception {
        when(painel.decidir(anyInt(), any()))
                .thenAnswer(pedido -> decidido(pedido.getArgument(0), pedido.getArgument(1)));

        mvc.perform(post("/api/admin/pontos/14/aprovar").with(csrf())
                        .with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"idPonto\":14,\"status\":\"APROVADO\"}"));
        mvc.perform(post("/api/admin/pontos/15/recusar").with(csrf())
                        .with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"idPonto\":15,\"status\":\"RECUSADO\"}"));

        verify(painel).decidir(14, StatusDoPonto.APROVADO);
        verify(painel).decidir(15, StatusDoPonto.RECUSADO);
        assertThat(saida.getOut()).contains("Ponto 14 aprovado pela conta 1.")
                .contains("Ponto 15 recusado pela conta 1.")
                .doesNotContain("admin@example.com");
    }

    @Test
    void pontoJaDecididoDa409ComoOContratoENaoVaiParaOLog(CapturedOutput saida) throws Exception {
        Caso caso = Exemplos.caso("admin-decisao-repetida");
        when(painel.decidir(anyInt(), any())).thenThrow(new ErroDaApi(CodigoDeErro.JA_ANALISADO));

        for (var pedido : caso.pedidos()) {
            mvc.perform(post(pedido.caminho()).with(csrf()).with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                    .andExpect(status().isConflict())
                    .andExpect(content().json(caso.corpo().toString(), true));
        }
        assertThat(saida.getOut()).doesNotContain("Ponto 13 aprovado").doesNotContain("Ponto 13 recusado");
    }

    @Test
    void semTokenCsrfNaoDecideNemEditaNemExclui() throws Exception {
        mvc.perform(post("/api/admin/pontos/14/aprovar").with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/pontos/4").contentType(MediaType.APPLICATION_JSON).content(CORPO_DA_EDICAO)
                        .with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/pontos/4").with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(painel);
    }

    @Test
    void editarPassaOCorpoAoServicoERegistraNoLog(CapturedOutput saida) throws Exception {
        when(painel.editar(eq(4), any())).thenReturn(new PontoDetalhe(4, "Bretas - Loja 518", "Av. João Pinheiro, 77",
                "8h às 22h", null, new BairroSaida(4, "Centro"), List.of(new CategoriaSaida(6, "Pilhas e baterias",
                        "Pilhas e baterias usadas, que não podem ir para o lixo comum."), new CategoriaSaida(7,
                        "Lâmpadas",
                        "Lâmpadas queimadas, que contêm metais pesados e não podem ir para o lixo comum."))));

        mvc.perform(put("/api/admin/pontos/4").contentType(MediaType.APPLICATION_JSON).content(CORPO_DA_EDICAO)
                        .with(csrf()).with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().json(Exemplos.caso("admin-editar-ponto").corpo().toString(), true));

        verify(painel).editar(4, new PontoEntrada("Bretas - Loja 518", "Av. João Pinheiro, 77", 4, "8h às 22h", null,
                List.of(7, 6)));
        assertThat(saida.getOut()).contains("Ponto 4 editado pela conta 1.");
    }

    @Test
    void excluirResponde204SemCorpoERegistraNoLog(CapturedOutput saida) throws Exception {
        mvc.perform(delete("/api/admin/pontos/4").with(csrf()).with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(painel).excluir(4);
        assertThat(saida.getOut()).contains("Ponto 4 excluído pela conta 1.");
    }

    @Test
    void editarOuExcluirOQueNaoExisteDa404(CapturedOutput saida) throws Exception {
        String naoEncontrado = Exemplos.caso("admin-ponto-nao-encontrado").corpo().toString();
        ErroDaApi erro = new ErroDaApi(CodigoDeErro.NAO_ENCONTRADO, "Ponto não encontrado.");
        when(painel.editar(eq(999), any())).thenThrow(erro);
        doThrow(erro).when(painel).excluir(999);

        mvc.perform(put("/api/admin/pontos/999").contentType(MediaType.APPLICATION_JSON).content(CORPO_DA_EDICAO)
                        .with(csrf()).with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isNotFound()).andExpect(content().json(naoEncontrado, true));
        mvc.perform(delete("/api/admin/pontos/999").with(csrf()).with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isNotFound()).andExpect(content().json(naoEncontrado, true));

        assertThat(saida.getOut()).doesNotContain("Ponto 999");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-4", "0x04", "+4", "%204", "4.0", "9999999999"})
    void idDeEditarOuExcluirQueNaoEPositivoDa400(String id) throws Exception {
        String corpo = Exemplos.caso("admin-ponto-id-invalido").corpo().toString();
        mvc.perform(put(URI.create("/api/admin/pontos/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_DA_EDICAO).with(csrf()).with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isBadRequest()).andExpect(content().json(corpo, true));
        mvc.perform(delete(URI.create("/api/admin/pontos/" + id)).with(csrf())
                        .with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isBadRequest()).andExpect(content().json(corpo, true));

        verifyNoInteractions(painel);
    }

    @Test
    void oResumoSaiComoOContrato() throws Exception {
        when(painel.resumo()).thenReturn(new ResumoDoPainel(2, 13, 10));

        mvc.perform(get("/api/admin/resumo").with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().json(Exemplos.caso("admin-resumo").corpo().toString(), true));
    }

    private static PontoDoPainel decidido(int idPonto, StatusDoPonto status) {
        return new PontoDoPainel(idPonto, "Ponto " + idPonto, "Endereço", new BairroSaida(7, "Daniel Fonseca"),
                List.of(), null, null, status, new PontoDoPainel.SugeridoPor(3, "João Pereira"));
    }
}
