package br.com.ecoponto.categoria;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ecoponto.contrato.Exemplos;
import br.com.ecoponto.contrato.Exemplos.Caso;
import br.com.ecoponto.lista.Pagina;
import br.com.ecoponto.seguranca.ContasDoScript;
import br.com.ecoponto.seguranca.RespostasDeSeguranca;
import br.com.ecoponto.seguranca.SegurancaConfig;
import java.net.URI;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(PainelDeCategoriasController.class)
@Import({SegurancaConfig.class, RespostasDeSeguranca.class})
@ActiveProfiles("test")
class PainelDeCategoriasControllerTest {

    private static final String CORPO = "{\"nome\":\"Qualquer\",\"descricao\":null}";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PainelDeCategorias painel;

    @Test
    void visitanteEUsuarioComumNaoAcessamNenhumaRota() throws Exception {
        String semLogin = Exemplos.caso("sem-login").corpo().toString();
        String semPermissao = Exemplos.caso("sem-permissao").corpo().toString();
        List<Supplier<MockHttpServletRequestBuilder>> rotas = List.of(() -> get("/api/admin/categorias"),
                () -> post("/api/admin/categorias").contentType(MediaType.APPLICATION_JSON).content(CORPO),
                () -> put("/api/admin/categorias/8").contentType(MediaType.APPLICATION_JSON).content(CORPO));
        for (Supplier<MockHttpServletRequestBuilder> rota : rotas) {
            mvc.perform(rota.get().with(ContasDoScript.sessaoDe(null)).with(csrf()))
                    .andExpect(status().isUnauthorized()).andExpect(content().json(semLogin, true));
            for (String usuario : List.of(ContasDoScript.MARIA, ContasDoScript.JOAO)) {
                mvc.perform(rota.get().with(ContasDoScript.sessaoDe(usuario)).with(csrf()))
                        .andExpect(status().isForbidden()).andExpect(content().json(semPermissao, true));
            }
        }
        verifyNoInteractions(painel);
    }

    @Test
    void aListaLeAPaginaEOTamanhoEOsInvalidosDao400ComoOContrato() throws Exception {
        when(painel.listar(anyInt(), anyInt())).thenReturn(new Pagina<>(List.of(), 10, 1, 7));
        mvc.perform(get("/api/admin/categorias").with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/categorias?pagina=2&tamanho=50")
                        .with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isOk());
        verify(painel).listar(1, 7);
        verify(painel).listar(2, 50);

        Caso caso = Exemplos.caso("admin-categorias-tamanho-invalido");
        mvc.perform(get(caso.pedidos().get(0).caminho()).with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().is(caso.status()))
                .andExpect(content().json(caso.corpo().toString(), true));
    }

    @Test
    void criarResponde201ComACategoria() throws Exception {
        when(painel.criar(any())).thenReturn(new CategoriaDoPainel(11, "Cápsulas de café", null, 0));

        mvc.perform(post("/api/admin/categorias").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"  Cápsulas de café \",\"descricao\":\"\"}")
                        .with(csrf()).with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isCreated())
                .andExpect(content().json(Exemplos.caso("admin-criar-categoria").corpo().toString(), true));

        verify(painel).criar(new CategoriaEntrada("Cápsulas de café", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-8", "0x08", "+8", "%208", "8.0", "9999999999"})
    void idQueNaoEPositivoDa400(String id) throws Exception {
        mvc.perform(put(URI.create("/api/admin/categorias/" + id)).contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO).with(csrf()).with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(Exemplos.caso("admin-categoria-id-invalido").corpo().toString(), true));

        verifyNoInteractions(painel);
    }

    @Test
    void editarPassaOIdEOCorpo() throws Exception {
        when(painel.editar(anyInt(), any())).thenReturn(new CategoriaDoPainel(8, "Óleo", null, 2));

        mvc.perform(put("/api/admin/categorias/8").contentType(MediaType.APPLICATION_JSON).content(CORPO)
                        .with(csrf()).with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"idCategoria\":8,\"nome\":\"Óleo\",\"descricao\":null,\"pontos\":2}", true));

        verify(painel).editar(8, new CategoriaEntrada("Qualquer", null));
    }

    @Test
    void semTokenCsrfNaoCriaNemEdita() throws Exception {
        mvc.perform(post("/api/admin/categorias").contentType(MediaType.APPLICATION_JSON).content(CORPO)
                        .with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/categorias/8").contentType(MediaType.APPLICATION_JSON).content(CORPO)
                        .with(ContasDoScript.sessaoDe(ContasDoScript.ADMIN)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(painel);
    }
}
