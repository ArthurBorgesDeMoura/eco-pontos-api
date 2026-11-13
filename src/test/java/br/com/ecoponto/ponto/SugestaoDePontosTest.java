package br.com.ecoponto.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.ecoponto.bairro.Bairro;
import br.com.ecoponto.bairro.BairroRepositorio;
import br.com.ecoponto.bairro.BairroSaida;
import br.com.ecoponto.categoria.Categoria;
import br.com.ecoponto.categoria.CategoriaRepositorio;
import br.com.ecoponto.categoria.CategoriaSaida;
import br.com.ecoponto.conta.Usuario;
import br.com.ecoponto.conta.UsuarioRepositorio;
import br.com.ecoponto.erro.ErroDaApi;
import br.com.ecoponto.validacao.ValidadorDeEntradas;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

class SugestaoDePontosTest {

    private static final ValidatorFactory FABRICA = Validation.buildDefaultValidatorFactory();

    private final PontoRepositorio pontos = mock(PontoRepositorio.class);
    private final BairroRepositorio bairros = mock(BairroRepositorio.class);
    private final CategoriaRepositorio categorias = mock(CategoriaRepositorio.class);
    private final UsuarioRepositorio usuarios = mock(UsuarioRepositorio.class);
    private final SugestaoDePontos sugestoes = new SugestaoDePontos(pontos, usuarios,
            new ConferenciaDoPonto(bairros, categorias, new ValidadorDeEntradas(FABRICA.getValidator())));

    private final Usuario maria = BeanUtils.instantiateClass(Usuario.class);

    @AfterAll
    static void fecharOValidador() {
        FABRICA.close();
    }

    @BeforeEach
    void dadosDoScript() {
        ReflectionTestUtils.setField(maria, "idUsuario", 2);
        when(usuarios.getReferenceById(2)).thenReturn(maria);
        when(bairros.findById(anyInt())).thenReturn(Optional.empty());
        when(bairros.findById(20)).thenReturn(Optional.of(bairro(20, "Tibery")));
        when(categorias.findAllById(any())).thenAnswer(pedido -> {
            List<Categoria> achadas = new ArrayList<>();
            for (Integer id : pedido.<Iterable<Integer>>getArgument(0)) {
                if (id >= 1 && id <= 10) {
                    achadas.add(categoria(id));
                }
            }
            Collections.reverse(achadas);
            return achadas;
        });
        when(pontos.saveAndFlush(any())).thenAnswer(pedido -> {
            Ponto ponto = pedido.getArgument(0);
            ReflectionTestUtils.setField(ponto, "idPonto", 16);
            return ponto;
        });
    }

    @Test
    void umaSugestaoValidaNascePendenteComOAutorEOsMateriaisEmOrdem() {
        PontoCriado criado = sugestoes.sugerir(entrada("Mercado do Bairro", 20, List.of(6, 2)), 2);

        ArgumentCaptor<Ponto> gravado = ArgumentCaptor.forClass(Ponto.class);
        verify(pontos).saveAndFlush(gravado.capture());
        Ponto ponto = gravado.getValue();
        assertThat(ponto.getStatus()).isEqualTo(StatusDoPonto.PENDENTE);
        assertThat(ponto.getAutor()).isSameAs(maria);
        assertThat(ponto.getBairro().getIdBairro()).isEqualTo(20);
        assertThat(ponto.getMateriais()).extracting(Categoria::getIdCategoria).containsExactly(2, 6);
        assertThat(criado).isEqualTo(new PontoCriado(16, "Mercado do Bairro", "Rua das Flores, 50", "8h às 18h", null,
                new BairroSaida(20, "Tibery"), List.of(new CategoriaSaida(2, "Material 2", "Descrição 2"),
                        new CategoriaSaida(6, "Material 6", "Descrição 6")), StatusDoPonto.PENDENTE));
    }

    @Test
    void textosChegamSemAsPontasEOpcionaisVaziosViramNull() {
        PontoEntrada entrada = new PontoEntrada("  Mercado ", "\tRua das Flores, 50  ", 20, "   ", "", List.of(2));

        PontoCriado criado = sugestoes.sugerir(entrada, 2);

        assertThat(criado.nome()).isEqualTo("Mercado");
        assertThat(criado.endereco()).isEqualTo("Rua das Flores, 50");
        assertThat(criado.horario()).isNull();
        assertThat(criado.contato()).isNull();
    }

    @Test
    void errosDosCamposEDoBancoVemNumaRespostaSo() {
        PontoEntrada entrada = new PontoEntrada("", "Rua das Flores, 50", 99, null, null, List.of(7, 7));

        assertThatThrownBy(() -> sugestoes.sugerir(entrada, 2)).isInstanceOfSatisfying(ErroDaApi.class, erro -> {
            assertThat(erro.corpo().mensagem()).isEqualTo("Confira os campos destacados.");
            assertThat(erro.corpo().campos()).isEqualTo(Map.of(
                    "nome", "Informe o nome do ponto.",
                    "idBairro", "Escolha um bairro da lista.",
                    "categorias", "Escolha só materiais da lista, sem repetir."));
        });
        verify(categorias, never()).findAllById(any());
        verify(pontos, never()).saveAndFlush(any());
    }

    @Test
    void materialQueNaoExisteDa400NasCategorias() {
        for (List<Integer> lista : List.of(List.of(2, 99), List.of(0), List.of(-1, 2), List.of(11))) {
            assertThatThrownBy(() -> sugestoes.sugerir(entrada("Mercado", 20, lista), 2)).as("%s", lista)
                    .isInstanceOfSatisfying(ErroDaApi.class, erro -> assertThat(erro.corpo().campos())
                            .isEqualTo(Map.of("categorias", "Escolha só materiais da lista, sem repetir.")));
        }
        verify(pontos, never()).saveAndFlush(any());
    }

    @Test
    void bairroQueNaoExisteDa400NoBairro() {
        for (int id : new int[] {0, -1, 21, 999_999_999}) {
            assertThatThrownBy(() -> sugestoes.sugerir(entrada("Mercado", id, List.of(2)), 2)).as("bairro %d", id)
                    .isInstanceOfSatisfying(ErroDaApi.class, erro -> assertThat(erro.corpo().campos())
                            .isEqualTo(Map.of("idBairro", "Escolha um bairro da lista.")));
        }
        verify(pontos, never()).saveAndFlush(any());
    }

    @Test
    void listaInvalidaNemChegaAoBanco() {
        List<Integer> comNull = new ArrayList<>();
        comNull.add(null);
        List<Integer> longaDemais = IntStream.rangeClosed(1, 101).boxed().toList();
        for (List<Integer> lista : Arrays.<List<Integer>>asList(null, List.of(), List.of(2, 2), comNull,
                longaDemais)) {
            assertThatThrownBy(() -> sugestoes.sugerir(entrada("Mercado", null, lista), 2)).as("%s", lista)
                    .isInstanceOfSatisfying(ErroDaApi.class, erro -> assertThat(erro.corpo().campos())
                            .containsOnlyKeys("idBairro", "categorias"));
        }
        verifyNoInteractions(categorias);
        verify(bairros, never()).findById(any());
        verify(pontos, never()).saveAndFlush(any());
    }

    @Test
    void aSugestaoRodaNumaTransacaoQueEscreve() throws Exception {
        Method sugerir = SugestaoDePontos.class.getMethod("sugerir", PontoEntrada.class, int.class);
        Transactional transacao = sugerir.getAnnotation(Transactional.class);

        assertThat(transacao).isNotNull();
        assertThat(transacao.readOnly()).isFalse();
    }

    private static PontoEntrada entrada(String nome, Integer idBairro, List<Integer> categorias) {
        return new PontoEntrada(nome, "Rua das Flores, 50", idBairro, "8h às 18h", null, categorias);
    }

    private static Bairro bairro(int id, String nome) {
        Bairro bairro = BeanUtils.instantiateClass(Bairro.class);
        ReflectionTestUtils.setField(bairro, "idBairro", id);
        ReflectionTestUtils.setField(bairro, "nome", nome);
        return bairro;
    }

    private static Categoria categoria(int id) {
        Categoria categoria = BeanUtils.instantiateClass(Categoria.class);
        ReflectionTestUtils.setField(categoria, "idCategoria", id);
        ReflectionTestUtils.setField(categoria, "nome", "Material " + id);
        ReflectionTestUtils.setField(categoria, "descricao", "Descrição " + id);
        return categoria;
    }
}
