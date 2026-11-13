package br.com.ecoponto.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ecoponto.bairro.Bairro;
import br.com.ecoponto.bairro.BairroRepositorio;
import br.com.ecoponto.bairro.BairroSaida;
import br.com.ecoponto.categoria.Categoria;
import br.com.ecoponto.categoria.CategoriaRepositorio;
import br.com.ecoponto.categoria.CategoriaSaida;
import br.com.ecoponto.conta.Usuario;
import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import br.com.ecoponto.validacao.ValidadorDeEntradas;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

class PainelDePontosTest {

    private static final ValidatorFactory FABRICA = Validation.buildDefaultValidatorFactory();

    private final PontoRepositorio pontos = mock(PontoRepositorio.class);
    private final BairroRepositorio bairros = mock(BairroRepositorio.class);
    private final CategoriaRepositorio categorias = mock(CategoriaRepositorio.class);
    private final PainelDePontos painel = new PainelDePontos(pontos, categorias,
            new ConferenciaDoPonto(bairros, categorias, new ValidadorDeEntradas(FABRICA.getValidator())));

    @AfterAll
    static void fecharOValidador() {
        FABRICA.close();
    }

    @Test
    void oResumoContaPendentesAprovadosECategorias() {
        when(pontos.contar(StatusDoPonto.PENDENTE, null, null)).thenReturn(2L);
        when(pontos.contar(StatusDoPonto.APROVADO, null, null)).thenReturn(13L);
        when(categorias.count()).thenReturn(10L);

        assertThat(painel.resumo()).isEqualTo(new ResumoDoPainel(2, 13, 10));
    }

    @Test
    void aprovarMudaOStatusEDevolveOPontoComQuemSugeriu() {
        when(pontos.travar(14)).thenReturn(Optional.of(danielFonseca(StatusDoPonto.PENDENTE)));
        when(pontos.decidir(14, "PENDENTE", "APROVADO")).thenReturn(1);
        when(pontos.doPainel(14)).thenReturn(Optional.of(danielFonseca(StatusDoPonto.APROVADO)));
        when(pontos.materiaisDos(List.of(14))).thenReturn(List.of(
                new MaterialDoPonto(14, 1, "Papel e papelão", "Descrição"),
                new MaterialDoPonto(14, 10, "Entulho e volumosos", null)));

        PontoDoPainel decidido = painel.decidir(14, StatusDoPonto.APROVADO);

        assertThat(decidido).isEqualTo(new PontoDoPainel(14, "Ecoponto Daniel Fonseca", "Rua Itabira, 1720",
                new BairroSaida(7, "Daniel Fonseca"), List.of(new MaterialSaida(1, "Papel e papelão"),
                        new MaterialSaida(10, "Entulho e volumosos")), "7h às 19h, todos os dias", null,
                StatusDoPonto.APROVADO, new PontoDoPainel.SugeridoPor(3, "João Pereira")));
        InOrder ordem = inOrder(pontos);
        ordem.verify(pontos).travar(14);
        ordem.verify(pontos).decidir(14, "PENDENTE", "APROVADO");
        ordem.verify(pontos).doPainel(14);
    }

    @Test
    void recusarTambemSoSaiDePendente() {
        when(pontos.travar(15)).thenReturn(Optional.of(danielFonseca(StatusDoPonto.PENDENTE)));
        when(pontos.decidir(15, "PENDENTE", "RECUSADO")).thenReturn(1);
        when(pontos.doPainel(15)).thenReturn(Optional.of(danielFonseca(StatusDoPonto.RECUSADO)));

        assertThat(painel.decidir(15, StatusDoPonto.RECUSADO).status()).isEqualTo(StatusDoPonto.RECUSADO);
        verify(pontos).decidir(15, "PENDENTE", "RECUSADO");
    }

    @Test
    void pontoJaDecididoDa409SemLerOPonto() {
        when(pontos.travar(13)).thenReturn(Optional.of(danielFonseca(StatusDoPonto.APROVADO)));
        when(pontos.decidir(13, "PENDENTE", "APROVADO")).thenReturn(0);

        assertThatThrownBy(() -> painel.decidir(13, StatusDoPonto.APROVADO))
                .isInstanceOfSatisfying(ErroDaApi.class, erro -> {
                    assertThat(erro.codigo()).isEqualTo(CodigoDeErro.JA_ANALISADO);
                    assertThat(erro.corpo().mensagem()).isEqualTo("Esta sugestão já foi analisada.");
                });
        verify(pontos, never()).doPainel(any());
        verify(pontos, never()).materiaisDos(anyCollection());
    }

    @Test
    void pontoQueNaoExisteDa404SemTentarOUpdate() {
        when(pontos.travar(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> painel.decidir(999, StatusDoPonto.RECUSADO))
                .isInstanceOfSatisfying(ErroDaApi.class, erro -> {
                    assertThat(erro.codigo()).isEqualTo(CodigoDeErro.NAO_ENCONTRADO);
                    assertThat(erro.corpo().mensagem()).isEqualTo("Ponto não encontrado.");
                });
        verify(pontos, never()).decidir(any(), any(), any());
        verify(pontos, never()).doPainel(any());
    }

    @Test
    void pendenteNaoEUmaDecisao() {
        assertThatThrownBy(() -> painel.decidir(14, StatusDoPonto.PENDENTE))
                .isInstanceOf(IllegalArgumentException.class);
        verify(pontos, never()).decidir(any(), any(), any());
    }

    @Test
    void aDecisaoEUmUpdateCondicionalPelaChaveNumaTransacaoQueEscreve() throws Exception {
        Method decidir = PontoRepositorio.class.getMethod("decidir", Integer.class, String.class, String.class);
        Query consulta = decidir.getAnnotation(Query.class);
        assertThat(consulta.nativeQuery()).isTrue();
        assertThat(consulta.value()).isEqualTo("UPDATE ponto_coleta FORCE INDEX (PRIMARY) SET status = :decisao"
                + " WHERE id_ponto = :idPonto AND status = :origem");
        assertThat(decidir.getAnnotation(Modifying.class).clearAutomatically()).isTrue();

        Transactional transacao = PainelDePontos.class.getMethod("decidir", int.class, StatusDoPonto.class)
                .getAnnotation(Transactional.class);
        assertThat(transacao).isNotNull();
        assertThat(transacao.readOnly()).isFalse();
    }

    @Test
    void editarTrocaOsDadosEOsMateriaisENaoMexeNoStatusNemNoAutor() {
        Ponto ponto = danielFonseca(StatusDoPonto.APROVADO);
        Usuario autor = ponto.getAutor();
        when(pontos.travar(14)).thenReturn(Optional.of(ponto));
        when(bairros.findById(4)).thenReturn(Optional.of(bairro(4, "Centro")));
        when(categorias.findAllById(List.of(7, 6)))
                .thenReturn(List.of(categoria(7, "Lâmpadas"), categoria(6, "Pilhas")));

        PontoDetalhe editado = painel.editar(14, new PontoEntrada("  Bretas ", "Av. João Pinheiro, 77", 4,
                "8h às 22h", "", List.of(7, 6)));

        assertThat(editado).isEqualTo(new PontoDetalhe(14, "Bretas", "Av. João Pinheiro, 77", "8h às 22h", null,
                new BairroSaida(4, "Centro"), List.of(new CategoriaSaida(6, "Pilhas", "Descrição 6"),
                        new CategoriaSaida(7, "Lâmpadas", "Descrição 7"))));
        assertThat(ponto.getStatus()).isEqualTo(StatusDoPonto.APROVADO);
        assertThat(ponto.getAutor()).isSameAs(autor);
        assertThat(ponto.getMateriais()).extracting(Categoria::getIdCategoria).containsExactly(6, 7);
        verify(pontos).flush();
        verify(pontos, never()).saveAndFlush(any());
    }

    @Test
    void editarOQueNaoExisteDa404SemConferirOCorpo() {
        when(pontos.travar(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> painel.editar(999, new PontoEntrada("", "", 99, null, null, List.of())))
                .isInstanceOfSatisfying(ErroDaApi.class, erro -> {
                    assertThat(erro.codigo()).isEqualTo(CodigoDeErro.NAO_ENCONTRADO);
                    assertThat(erro.corpo().mensagem()).isEqualTo("Ponto não encontrado.");
                });
        verify(bairros, never()).findById(any());
        verify(pontos, never()).flush();
    }

    @Test
    void editarComCorpoInvalidoDa400ENaoMudaOPonto() {
        Ponto ponto = danielFonseca(StatusDoPonto.APROVADO);
        when(pontos.travar(14)).thenReturn(Optional.of(ponto));
        PontoEntrada entrada = new PontoEntrada("Bretas", "Av. João Pinheiro, 77", 99, null, null, List.of(7, 7));

        assertThatThrownBy(() -> painel.editar(14, entrada)).isInstanceOfSatisfying(ErroDaApi.class,
                erro -> assertThat(erro.corpo().campos()).isEqualTo(Map.of("idBairro", "Escolha um bairro da lista.",
                        "categorias", "Escolha só materiais da lista, sem repetir.")));
        assertThat(ponto.getNome()).isEqualTo("Ecoponto Daniel Fonseca");
        verify(pontos, never()).flush();
    }

    @Test
    void excluirTravaAntesEOQueNaoExisteDa404() {
        when(pontos.travar(4)).thenReturn(Optional.of(danielFonseca(StatusDoPonto.APROVADO)));
        when(pontos.excluir(4)).thenReturn(1);

        painel.excluir(4);
        assertThatThrownBy(() -> painel.excluir(999)).isInstanceOfSatisfying(ErroDaApi.class,
                erro -> assertThat(erro.codigo()).isEqualTo(CodigoDeErro.NAO_ENCONTRADO));
        InOrder ordem = inOrder(pontos);
        ordem.verify(pontos).travar(4);
        ordem.verify(pontos).excluir(4);
        verify(pontos, never()).excluir(999);
    }

    @Test
    void aEdicaoTravaALinhaEAsMudancasEscrevem() throws Exception {
        assertThat(PontoRepositorio.class.getMethod("travar", Integer.class).getAnnotation(Lock.class).value())
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(PontoRepositorio.class.getMethod("excluir", Integer.class).getAnnotation(Query.class).value())
                .isEqualTo("delete from Ponto p where p.idPonto = :idPonto");
        for (Method metodo : List.of(PainelDePontos.class.getMethod("editar", int.class, PontoEntrada.class),
                PainelDePontos.class.getMethod("excluir", int.class))) {
            Transactional transacao = metodo.getAnnotation(Transactional.class);
            assertThat(transacao).as(metodo.getName()).isNotNull();
            assertThat(transacao.readOnly()).as(metodo.getName()).isFalse();
        }
    }

    private static Bairro bairro(int id, String nome) {
        Bairro bairro = BeanUtils.instantiateClass(Bairro.class);
        ReflectionTestUtils.setField(bairro, "idBairro", id);
        ReflectionTestUtils.setField(bairro, "nome", nome);
        return bairro;
    }

    private static Categoria categoria(int id, String nome) {
        Categoria categoria = BeanUtils.instantiateClass(Categoria.class);
        ReflectionTestUtils.setField(categoria, "idCategoria", id);
        ReflectionTestUtils.setField(categoria, "nome", nome);
        ReflectionTestUtils.setField(categoria, "descricao", "Descrição " + id);
        return categoria;
    }

    private static Ponto danielFonseca(StatusDoPonto status) {
        Bairro bairro = BeanUtils.instantiateClass(Bairro.class);
        ReflectionTestUtils.setField(bairro, "idBairro", 7);
        ReflectionTestUtils.setField(bairro, "nome", "Daniel Fonseca");
        Usuario joao = BeanUtils.instantiateClass(Usuario.class);
        ReflectionTestUtils.setField(joao, "idUsuario", 3);
        ReflectionTestUtils.setField(joao, "nome", "João Pereira");
        Ponto ponto = BeanUtils.instantiateClass(Ponto.class);
        ReflectionTestUtils.setField(ponto, "idPonto", 14);
        ReflectionTestUtils.setField(ponto, "nome", "Ecoponto Daniel Fonseca");
        ReflectionTestUtils.setField(ponto, "endereco", "Rua Itabira, 1720");
        ReflectionTestUtils.setField(ponto, "bairro", bairro);
        ReflectionTestUtils.setField(ponto, "horario", "7h às 19h, todos os dias");
        ReflectionTestUtils.setField(ponto, "contato", "  ");
        ReflectionTestUtils.setField(ponto, "status", status);
        ReflectionTestUtils.setField(ponto, "autor", joao);
        return ponto;
    }
}
