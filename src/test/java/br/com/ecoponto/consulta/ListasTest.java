package br.com.ecoponto.consulta;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ecoponto.bairro.Bairro;
import br.com.ecoponto.bairro.BairroController;
import br.com.ecoponto.bairro.BairroRepositorio;
import br.com.ecoponto.categoria.Categoria;
import br.com.ecoponto.categoria.CategoriaController;
import br.com.ecoponto.categoria.CategoriaRepositorio;
import br.com.ecoponto.contrato.Exemplos;
import br.com.ecoponto.contrato.Exemplos.Caso;
import br.com.ecoponto.seguranca.RespostasDeSeguranca;
import br.com.ecoponto.seguranca.SegurancaConfig;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {BairroController.class, CategoriaController.class})
@Import({SegurancaConfig.class, RespostasDeSeguranca.class})
@ActiveProfiles("test")
class ListasTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private BairroRepositorio bairros;

    @MockBean
    private CategoriaRepositorio categorias;

    @Test
    void osBairrosSaemComoNoContratoSemLogin() throws Exception {
        Caso caso = Exemplos.caso("bairros");
        List<Bairro> linhas = new ArrayList<>();
        for (JsonNode item : caso.corpo()) {
            linhas.add(bairro(item.path("idBairro").asInt(), item.path("nome").asText()));
        }
        when(bairros.findAllByOrderByNomeAscIdBairroAsc()).thenReturn(linhas);

        mvc.perform(get("/api/bairros"))
                .andExpect(status().is(caso.status()))
                .andExpect(content().json(caso.corpo().toString(), true));
        verify(bairros).findAllByOrderByNomeAscIdBairroAsc();
    }

    @Test
    void asCategoriasSaemComoNoContratoSemLogin() throws Exception {
        Caso caso = Exemplos.caso("categorias");
        List<Categoria> linhas = new ArrayList<>();
        for (JsonNode item : caso.corpo()) {
            linhas.add(categoria(item.path("idCategoria").asInt(), item.path("nome").asText(),
                    item.path("descricao").asText()));
        }
        when(categorias.findAllByOrderByIdCategoriaAsc()).thenReturn(linhas);

        mvc.perform(get("/api/categorias"))
                .andExpect(status().is(caso.status()))
                .andExpect(content().json(caso.corpo().toString(), true));
        verify(categorias).findAllByOrderByIdCategoriaAsc();
    }

    @Test
    void descricaoVaziaSaiComoNull() throws Exception {
        when(categorias.findAllByOrderByIdCategoriaAsc()).thenReturn(List.of(
                categoria(1, "Vazia", ""),
                categoria(2, "Só espaços", "  \t"),
                categoria(3, "Nula", null),
                categoria(4, "Com texto", "Garrafas PET.")));

        mvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(content().json("["
                        + "{\"idCategoria\":1,\"nome\":\"Vazia\",\"descricao\":null},"
                        + "{\"idCategoria\":2,\"nome\":\"Só espaços\",\"descricao\":null},"
                        + "{\"idCategoria\":3,\"nome\":\"Nula\",\"descricao\":null},"
                        + "{\"idCategoria\":4,\"nome\":\"Com texto\",\"descricao\":\"Garrafas PET.\"}]", true));
    }

    @Test
    void semLinhasAsListasSaemVaziasCom200() throws Exception {
        mvc.perform(get("/api/bairros")).andExpect(status().isOk()).andExpect(content().json("[]", true));
        mvc.perform(get("/api/categorias")).andExpect(status().isOk()).andExpect(content().json("[]", true));
    }

    private static Bairro bairro(int id, String nome) {
        Bairro bairro = BeanUtils.instantiateClass(Bairro.class);
        ReflectionTestUtils.setField(bairro, "idBairro", id);
        ReflectionTestUtils.setField(bairro, "nome", nome);
        return bairro;
    }

    private static Categoria categoria(int id, String nome, String descricao) {
        Categoria categoria = BeanUtils.instantiateClass(Categoria.class);
        ReflectionTestUtils.setField(categoria, "idCategoria", id);
        ReflectionTestUtils.setField(categoria, "nome", nome);
        ReflectionTestUtils.setField(categoria, "descricao", descricao);
        return categoria;
    }
}
