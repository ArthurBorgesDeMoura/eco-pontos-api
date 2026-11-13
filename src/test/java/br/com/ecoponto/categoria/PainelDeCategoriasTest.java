package br.com.ecoponto.categoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import br.com.ecoponto.lista.Pagina;
import br.com.ecoponto.ponto.StatusDoPonto;
import br.com.ecoponto.validacao.ValidadorDeEntradas;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;

class PainelDeCategoriasTest {

    private static final ValidatorFactory FABRICA = Validation.buildDefaultValidatorFactory();
    private static final String REPETIDO = "Já existe uma categoria com este nome.";

    private final CategoriaRepositorio categorias = mock(CategoriaRepositorio.class);
    private final PainelDeCategorias painel =
            new PainelDeCategorias(categorias, new ValidadorDeEntradas(FABRICA.getValidator()));

    @AfterAll
    static void fecharOValidador() {
        FABRICA.close();
    }

    @Test
    void aListaContaOsAprovadosEmOrdemDeIdEDepoisDoFimNemRodaAPagina() {
        when(categorias.count()).thenReturn(10L);
        List<CategoriaDoPainel> itens = List.of(new CategoriaDoPainel(1, "Papel e papelão", "Jornais", 3));
        when(categorias.doPainel(StatusDoPonto.APROVADO, PageRequest.of(0, 50))).thenReturn(itens);

        assertThat(painel.listar(1, 50)).isEqualTo(new Pagina<>(itens, 10, 1, 50));
        assertThat(painel.listar(2, 50)).isEqualTo(new Pagina<>(List.of(), 10, 2, 50));
        verify(categorias, never()).doPainel(StatusDoPonto.APROVADO, PageRequest.of(1, 50));
    }

    @Test
    void criarGravaENasceComZeroPontos() {
        when(categorias.saveAndFlush(any())).thenAnswer(pedido -> {
            Categoria gravada = pedido.getArgument(0);
            ReflectionTestUtils.setField(gravada, "idCategoria", 11);
            return gravada;
        });

        CategoriaDoPainel criada = painel.criar(new CategoriaEntrada("  Cápsulas de café ", ""));

        assertThat(criada).isEqualTo(new CategoriaDoPainel(11, "Cápsulas de café", null, 0));
        verify(categorias).existsByNome("Cápsulas de café");
    }

    @Test
    void nomeRepetidoDa409NoCampoSemGravar() {
        when(categorias.existsByNome("plastico")).thenReturn(true);
        when(categorias.findById(8)).thenReturn(Optional.of(categoria(8, "Óleo de cozinha usado")));
        when(categorias.existsByNomeAndIdCategoriaNot("VIDRO", 8)).thenReturn(true);

        for (Runnable pedido : List.<Runnable>of(() -> painel.criar(new CategoriaEntrada("plastico", null)),
                () -> painel.editar(8, new CategoriaEntrada("VIDRO", null)))) {
            assertThatThrownBy(pedido::run).isInstanceOfSatisfying(ErroDaApi.class, erro -> {
                assertThat(erro.codigo()).isEqualTo(CodigoDeErro.NOME_REPETIDO);
                assertThat(erro.corpo().mensagem()).isEqualTo(REPETIDO);
                assertThat(erro.corpo().campos()).isEqualTo(Map.of("nome", REPETIDO));
            });
        }
        verify(categorias, never()).saveAndFlush(any());
        verify(categorias, never()).flush();
    }

    @Test
    void aChaveUnicaDoBancoTambemViraNomeRepetido() {
        when(categorias.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicada",
                new SQLIntegrityConstraintViolationException("Duplicate entry 'x' for key 'uq_categoria_nome'")));

        assertThatThrownBy(() -> painel.criar(new CategoriaEntrada("Cápsulas de café", null)))
                .isInstanceOfSatisfying(ErroDaApi.class,
                        erro -> assertThat(erro.codigo()).isEqualTo(CodigoDeErro.NOME_REPETIDO));
    }

    @Test
    void aChaveUnicaNaEdicaoTambemViraNomeRepetido() {
        when(categorias.findById(8)).thenReturn(Optional.of(categoria(8, "Óleo de cozinha usado")));
        doThrow(new DataIntegrityViolationException("duplicada",
                new SQLIntegrityConstraintViolationException("Duplicate entry 'X' for key 'uq_categoria_nome'")))
                .when(categorias).flush();

        assertThatThrownBy(() -> painel.editar(8, new CategoriaEntrada("X", null)))
                .isInstanceOfSatisfying(ErroDaApi.class, erro -> {
                    assertThat(erro.codigo()).isEqualTo(CodigoDeErro.NOME_REPETIDO);
                    assertThat(erro.corpo().campos()).isEqualTo(Map.of("nome", REPETIDO));
                });
        verify(categorias, never()).umaDoPainel(any(), any());
    }

    @Test
    void outraFalhaDeIntegridadeNaoViraNomeRepetido() {
        DataIntegrityViolationException outra = new DataIntegrityViolationException("outra",
                new SQLIntegrityConstraintViolationException("Cannot add or update a child row"));
        when(categorias.saveAndFlush(any())).thenThrow(outra);

        assertThatThrownBy(() -> painel.criar(new CategoriaEntrada("Cápsulas de café", null))).isSameAs(outra);
    }

    @Test
    void corpoInvalidoDa400SemConsultarONome() {
        List<Map.Entry<CategoriaEntrada, Map<String, String>>> casos = List.of(
                Map.entry(new CategoriaEntrada("", null), Map.of("nome", "Informe o nome da categoria.")),
                Map.entry(new CategoriaEntrada("  \t", null), Map.of("nome", "Informe o nome da categoria.")),
                Map.entry(new CategoriaEntrada(null, null), Map.of("nome", "Informe o nome da categoria.")),
                Map.entry(new CategoriaEntrada("x".repeat(61), null), Map.of("nome", "Use até 60 caracteres.")),
                Map.entry(new CategoriaEntrada("Vidro", "d".repeat(256)),
                        Map.of("descricao", "Use até 255 caracteres.")));
        for (Map.Entry<CategoriaEntrada, Map<String, String>> caso : casos) {
            assertThatThrownBy(() -> painel.criar(caso.getKey())).as("%s", caso.getKey())
                    .isInstanceOfSatisfying(ErroDaApi.class, erro -> {
                        assertThat(erro.corpo().mensagem()).isEqualTo("Confira os campos destacados.");
                        assertThat(erro.corpo().campos()).isEqualTo(caso.getValue());
                    });
        }
        verify(categorias, never()).existsByNome(anyString());
        verify(categorias, never()).saveAndFlush(any());
    }

    @Test
    void nomeEDescricaoNoLimitePassam() {
        when(categorias.saveAndFlush(any())).thenAnswer(pedido -> pedido.getArgument(0));

        CategoriaDoPainel criada = painel.criar(new CategoriaEntrada("ç".repeat(60), "é".repeat(255)));

        assertThat(criada.nome()).hasSize(60);
        assertThat(criada.descricao()).hasSize(255);
    }

    @Test
    void editarTrocaNomeEDescricaoNaMesmaCategoria() {
        Categoria oleo = categoria(8, "Óleo de cozinha usado");
        when(categorias.findById(8)).thenReturn(Optional.of(oleo));
        CategoriaDoPainel resposta = new CategoriaDoPainel(8, "Óleo de cozinha", "Frio", 2);
        when(categorias.umaDoPainel(8, StatusDoPonto.APROVADO)).thenReturn(resposta);

        assertThat(painel.editar(8, new CategoriaEntrada(" Óleo de cozinha ", " Frio "))).isSameAs(resposta);
        assertThat(oleo.getIdCategoria()).isEqualTo(8);
        assertThat(oleo.getNome()).isEqualTo("Óleo de cozinha");
        assertThat(oleo.getDescricao()).isEqualTo("Frio");
        verify(categorias).existsByNomeAndIdCategoriaNot("Óleo de cozinha", 8);
        verify(categorias).flush();
        verify(categorias, never()).saveAndFlush(any());
    }

    @Test
    void editarAQueNaoExisteDa404AntesDeConferirOCorpo() {
        when(categorias.findById(anyInt())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> painel.editar(99, new CategoriaEntrada("", null)))
                .isInstanceOfSatisfying(ErroDaApi.class, erro -> {
                    assertThat(erro.codigo()).isEqualTo(CodigoDeErro.NAO_ENCONTRADO);
                    assertThat(erro.corpo().mensagem()).isEqualTo("Categoria não encontrada.");
                });
        verify(categorias, never()).existsByNomeAndIdCategoriaNot(any(), any());
    }

    @Test
    void aContagemDoPainelFiltraOStatusNaJuncao() throws Exception {
        String pagina = CategoriaRepositorio.class.getMethod("doPainel", StatusDoPonto.class, Pageable.class)
                .getAnnotation(Query.class).value();

        assertThat(pagina).contains("left join c.pontos p on p.status = :status", "count(distinct p.idPonto)")
                .endsWith(" order by c.idCategoria").doesNotContain("where");
    }

    @Test
    void aDescricaoVaziaDoBancoSaiComoNull() {
        assertThat(new CategoriaDoPainel(1, "Vidro", "   ", 0).descricao()).isNull();
        assertThat(new CategoriaDoPainel(1, "Vidro", "", 0).descricao()).isNull();
    }

    private static Categoria categoria(int id, String nome) {
        Categoria categoria = BeanUtils.instantiateClass(Categoria.class);
        ReflectionTestUtils.setField(categoria, "idCategoria", id);
        ReflectionTestUtils.setField(categoria, "nome", nome);
        return categoria;
    }
}
