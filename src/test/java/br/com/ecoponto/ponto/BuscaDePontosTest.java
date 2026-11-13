package br.com.ecoponto.ponto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ecoponto.bairro.Bairro;
import br.com.ecoponto.bairro.BairroSaida;
import br.com.ecoponto.categoria.CategoriaSaida;
import br.com.ecoponto.conta.Usuario;
import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import br.com.ecoponto.lista.Pagina;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.util.ReflectionTestUtils;

class BuscaDePontosTest {

    private final PontoRepositorio pontos = mock(PontoRepositorio.class);
    private final BuscaDePontos busca = new BuscaDePontos(pontos);

    @Test
    void osMateriaisVemNumaConsultaSoEAgrupadosPorPonto() {
        Ponto drogaria = ponto(2, "Drogaria São Paulo - DSP Uberlândia III", 1, "Altamira");
        Ponto padaria = ponto(10, "Pão de Açúcar", 1, "Altamira");
        Ponto semMaterial = ponto(30, "Ponto sem material", 3, "Brasil");
        when(pontos.contar(StatusDoPonto.APROVADO, null, null)).thenReturn(13L);
        when(pontos.buscar(StatusDoPonto.APROVADO, null, null, PageRequest.of(0, 7)))
                .thenReturn(List.of(drogaria, padaria, semMaterial));
        when(pontos.materiaisDos(List.of(2, 10, 30))).thenReturn(List.of(
                new MaterialDoPonto(2, 6, "Pilhas e baterias", "Pilhas usadas."),
                new MaterialDoPonto(2, 9, "Medicamentos vencidos", "Medicamentos vencidos ou em desuso."),
                new MaterialDoPonto(10, 8, "Óleo de cozinha usado", null)));

        Pagina<PontoResumo> pagina = busca.buscar(null, null, 1, 7);

        assertThat(pagina.total()).isEqualTo(13);
        assertThat(pagina.itens()).containsExactly(
                new PontoResumo(2, drogaria.getNome(), "Endereço 2", new BairroSaida(1, "Altamira"), List.of(
                        new MaterialSaida(6, "Pilhas e baterias"), new MaterialSaida(9, "Medicamentos vencidos"))),
                new PontoResumo(10, padaria.getNome(), "Endereço 10", new BairroSaida(1, "Altamira"),
                        List.of(new MaterialSaida(8, "Óleo de cozinha usado"))),
                new PontoResumo(30, semMaterial.getNome(), "Endereço 30", new BairroSaida(3, "Brasil"), List.of()));
        verify(pontos).materiaisDos(List.of(2, 10, 30));
    }

    @Test
    void osFiltrosEAPaginaChegamAsConsultas() {
        when(pontos.contar(StatusDoPonto.APROVADO, 4, 7)).thenReturn(20L);

        busca.buscar(4, 7, 3, 5);

        verify(pontos).buscar(StatusDoPonto.APROVADO, 4, 7, PageRequest.of(2, 5));
    }

    @Test
    void semNadaQueCombineSoContaENaoBuscaAPagina() {
        Pagina<PontoResumo> pagina = busca.buscar(2, null, 1, 7);

        assertThat(pagina).isEqualTo(new Pagina<>(List.of(), 0, 1, 7));
        verify(pontos, never()).buscar(any(), any(), any(), any());
        verify(pontos, never()).materiaisDos(anyCollection());
    }

    @Test
    void depoisDaUltimaPaginaDevolveItensVaziosSemEstourarOOffset() {
        when(pontos.contar(StatusDoPonto.APROVADO, null, null)).thenReturn(13L);

        assertThat(busca.buscar(null, null, 3, 7)).isEqualTo(new Pagina<>(List.of(), 13, 3, 7));
        assertThat(busca.buscar(null, null, 999_999_999, 50)).isEqualTo(new Pagina<>(List.of(), 13, 999_999_999, 50));
        verify(pontos, never()).buscar(any(), any(), any(), any());
    }

    @Test
    void aConsultaDaPaginaOrdenaPorBairroNomeEId() throws Exception {
        Query consulta = PontoRepositorio.class.getMethod("buscar", StatusDoPonto.class, Integer.class,
                Integer.class, Pageable.class).getAnnotation(Query.class);

        assertThat(consulta.value()).endsWith(" order by b.nome, p.nome, p.idPonto")
                .contains("join fetch p.bairro b").contains(PontoRepositorio.FILTROS_DA_BUSCA);
        assertThat(PontoRepositorio.class.getMethod("contar", StatusDoPonto.class, Integer.class, Integer.class)
                .getAnnotation(Query.class).value()).contains(PontoRepositorio.FILTROS_DA_BUSCA);
    }

    @Test
    void aContagemJuntaOTotalDaBuscaEAsCategoriasDoMesmoBairro() {
        List<ContagemDoMaterial> categorias = List.of(new ContagemDoMaterial(1, 0), new ContagemDoMaterial(5, 1));
        when(pontos.contar(StatusDoPonto.APROVADO, 4, null)).thenReturn(2L);
        when(pontos.contarPorMaterial(StatusDoPonto.APROVADO, 4)).thenReturn(categorias);

        assertThat(busca.contar(4)).isEqualTo(new ContagemDePontos(2, categorias));
    }

    @Test
    void aContagemPorMaterialUsaJuncaoExternaComOFiltroNoOn() throws Exception {
        String consulta = PontoRepositorio.class.getMethod("contarPorMaterial", StatusDoPonto.class, Integer.class)
                .getAnnotation(Query.class).value();

        assertThat(consulta).contains("count(distinct p.idPonto)")
                .contains(" from Categoria c left join c.pontos p on p.status = :status"
                        + " and (:bairro is null or p.bairro.idBairro = :bairro)")
                .doesNotContain(" where ")
                .endsWith(" group by c.idCategoria order by c.idCategoria");
    }

    @Test
    void oDetalheTrazOsMateriaisComADescricaoEOsVaziosComoNull() {
        Ponto luizote = ponto(13, "Ecoponto Luizote de Freitas", 11, "Luizote de Freitas");
        ReflectionTestUtils.setField(luizote, "horario", "7h às 19h, todos os dias");
        ReflectionTestUtils.setField(luizote, "contato", "   ");
        when(pontos.detalhe(13, StatusDoPonto.APROVADO)).thenReturn(Optional.of(luizote));
        when(pontos.materiaisDos(List.of(13))).thenReturn(List.of(
                new MaterialDoPonto(13, 1, "Papel e papelão", "Jornais e revistas."),
                new MaterialDoPonto(13, 10, "Entulho e volumosos", "")));

        PontoDetalhe detalhe = busca.detalhar(13, false);

        assertThat(detalhe).isEqualTo(new PontoDetalhe(13, "Ecoponto Luizote de Freitas", "Endereço 13",
                "7h às 19h, todos os dias", null, new BairroSaida(11, "Luizote de Freitas"), List.of(
                        new CategoriaSaida(1, "Papel e papelão", "Jornais e revistas."),
                        new CategoriaSaida(10, "Entulho e volumosos", null))));
    }

    @Test
    void horarioEContatoVaziosOuSoDeEspacosVoltamComoNull() {
        for (String vazio : new String[] {null, "", "   ", "\t\n", "\u00a0"}) {
            Ponto ponto = ponto(4, "Bretas - Loja 518", 4, "Centro");
            ReflectionTestUtils.setField(ponto, "horario", vazio);
            ReflectionTestUtils.setField(ponto, "contato", vazio);

            PontoDetalhe detalhe = PontoDetalhe.de(ponto, List.of());

            assertThat(detalhe.horario()).as("horário %s", String.valueOf(vazio)).isNull();
            assertThat(detalhe.contato()).as("contato %s", String.valueOf(vazio)).isNull();
        }
    }

    @Test
    void quemNaoEAdminSoPedeOAprovadoEOAdminPedeQualquerStatus() {
        Ponto pendente = ponto(14, "Ecoponto Daniel Fonseca", 7, "Daniel Fonseca");
        when(pontos.detalhe(14, null)).thenReturn(Optional.of(pendente));

        assertThat(busca.detalhar(14, true).idPonto()).isEqualTo(14);
        assertThatThrownBy(() -> busca.detalhar(14, false)).isInstanceOf(ErroDaApi.class);
        verify(pontos).detalhe(14, StatusDoPonto.APROVADO);
    }

    @Test
    void pontoQueNaoAchaDa404SemBuscarOsMateriais() {
        assertThatThrownBy(() -> busca.detalhar(999, false))
                .isInstanceOfSatisfying(ErroDaApi.class, erro -> {
                    assertThat(erro.codigo()).isEqualTo(CodigoDeErro.NAO_ENCONTRADO);
                    assertThat(erro.getMessage()).isEqualTo("Ponto não encontrado.");
                });
        verify(pontos, never()).materiaisDos(anyCollection());
    }

    @Test
    void aConsultaDoDetalheFiltraOStatusETrazOBairro() throws Exception {
        String consulta = PontoRepositorio.class.getMethod("detalhe", Integer.class, StatusDoPonto.class)
                .getAnnotation(Query.class).value();

        assertThat(consulta).contains("join fetch p.bairro")
                .endsWith(" where p.idPonto = :idPonto and (:status is null or p.status = :status)");
    }

    @Test
    void minhasSugestoesTrazemOStatusEOsMateriaisDoAutor() {
        Ponto coru = ponto(15, "Cooperativa dos Recicladores de Uberlândia (CORU)", 9, "Jardim Brasília");
        ReflectionTestUtils.setField(coru, "status", StatusDoPonto.PENDENTE);
        Ponto recusado = ponto(20, "Ponto recusado", 9, "Jardim Brasília");
        ReflectionTestUtils.setField(recusado, "status", StatusDoPonto.RECUSADO);
        when(pontos.contarSugestoes(2, null)).thenReturn(2L);
        when(pontos.buscarSugestoes(2, null, PageRequest.of(0, 7))).thenReturn(List.of(recusado, coru));
        when(pontos.materiaisDos(List.of(20, 15))).thenReturn(List.of(
                new MaterialDoPonto(15, 8, "Óleo de cozinha usado", "Descrição"),
                new MaterialDoPonto(20, 1, "Papel e papelão", null)));

        Pagina<Sugestao> pagina = busca.minhasSugestoes(2, null, 1, 7);

        assertThat(pagina).isEqualTo(new Pagina<>(List.of(
                new Sugestao(20, "Ponto recusado", "Endereço 20", new BairroSaida(9, "Jardim Brasília"),
                        List.of(new MaterialSaida(1, "Papel e papelão")), StatusDoPonto.RECUSADO),
                new Sugestao(15, "Cooperativa dos Recicladores de Uberlândia (CORU)", "Endereço 15",
                        new BairroSaida(9, "Jardim Brasília"), List.of(new MaterialSaida(8, "Óleo de cozinha usado")),
                        StatusDoPonto.PENDENTE)), 2, 1, 7));
    }

    @Test
    void minhasSugestoesVaziasOuDepoisDoFimSoContam() {
        when(pontos.contarSugestoes(2, StatusDoPonto.RECUSADO)).thenReturn(0L);
        when(pontos.contarSugestoes(1, StatusDoPonto.APROVADO)).thenReturn(13L);

        assertThat(busca.minhasSugestoes(2, StatusDoPonto.RECUSADO, 1, 7)).isEqualTo(new Pagina<>(List.of(), 0, 1, 7));
        assertThat(busca.minhasSugestoes(1, StatusDoPonto.APROVADO, 3, 7))
                .isEqualTo(new Pagina<>(List.of(), 13, 3, 7));
        verify(pontos, never()).buscarSugestoes(any(), any(), any());
        verify(pontos, never()).materiaisDos(anyCollection());
    }

    @Test
    void aPaginaDasSugestoesUsaOAutorOStatusEOOffset() {
        when(pontos.contarSugestoes(1, StatusDoPonto.APROVADO)).thenReturn(13L);

        busca.minhasSugestoes(1, StatusDoPonto.APROVADO, 2, 5);

        verify(pontos).buscarSugestoes(1, StatusDoPonto.APROVADO, PageRequest.of(1, 5));
    }

    @Test
    void asConsultasDasSugestoesFiltramOAutorEOrdenamPeloIdDecrescente() throws Exception {
        String contagem = PontoRepositorio.class.getMethod("contarSugestoes", Integer.class, StatusDoPonto.class)
                .getAnnotation(Query.class).value();
        String pagina = PontoRepositorio.class
                .getMethod("buscarSugestoes", Integer.class, StatusDoPonto.class, Pageable.class)
                .getAnnotation(Query.class).value();
        String filtro = " where p.autor.idUsuario = :autor and (:status is null or p.status = :status)";

        assertThat(contagem).isEqualTo("select count(p) from Ponto p" + filtro);
        assertThat(pagina).isEqualTo("select p from Ponto p join fetch p.bairro" + filtro + " order by p.idPonto desc");
    }

    @Test
    void oPainelEscolheAOrdemPeloStatus() {
        for (StatusDoPonto status : StatusDoPonto.values()) {
            when(pontos.contar(status, null, null)).thenReturn(13L);
        }

        busca.pontosDoPainel(StatusDoPonto.PENDENTE, 1, 7);
        busca.pontosDoPainel(StatusDoPonto.APROVADO, 2, 5);
        busca.pontosDoPainel(StatusDoPonto.RECUSADO, 1, 7);

        verify(pontos, times(1)).filaDoPainel(any(), any());
        verify(pontos).filaDoPainel(StatusDoPonto.PENDENTE, PageRequest.of(0, 7));
        verify(pontos, times(2)).pontosDoPainel(any(), any());
        verify(pontos).pontosDoPainel(StatusDoPonto.APROVADO, PageRequest.of(1, 5));
        verify(pontos).pontosDoPainel(StatusDoPonto.RECUSADO, PageRequest.of(0, 7));
    }

    @Test
    void cadaPontoDoPainelTrazQuemSugeriuEOsVaziosComoNull() {
        Ponto coru = ponto(15, "Cooperativa dos Recicladores de Uberlândia (CORU)", 9, "Jardim Brasília");
        ReflectionTestUtils.setField(coru, "status", StatusDoPonto.PENDENTE);
        ReflectionTestUtils.setField(coru, "horario", "");
        ReflectionTestUtils.setField(coru, "contato", "(34) 99798-1494");
        Usuario maria = BeanUtils.instantiateClass(Usuario.class);
        ReflectionTestUtils.setField(maria, "idUsuario", 2);
        ReflectionTestUtils.setField(maria, "nome", "Maria Souza");
        ReflectionTestUtils.setField(coru, "autor", maria);
        when(pontos.contar(StatusDoPonto.PENDENTE, null, null)).thenReturn(1L);
        when(pontos.filaDoPainel(StatusDoPonto.PENDENTE, PageRequest.of(0, 7))).thenReturn(List.of(coru));
        when(pontos.materiaisDos(List.of(15))).thenReturn(List.of(new MaterialDoPonto(15, 8, "Óleo de cozinha usado",
                "Descrição")));

        assertThat(busca.pontosDoPainel(StatusDoPonto.PENDENTE, 1, 7)).isEqualTo(new Pagina<>(List.of(
                new PontoDoPainel(15, "Cooperativa dos Recicladores de Uberlândia (CORU)", "Endereço 15",
                        new BairroSaida(9, "Jardim Brasília"), List.of(new MaterialSaida(8, "Óleo de cozinha usado")),
                        null, "(34) 99798-1494", StatusDoPonto.PENDENTE,
                        new PontoDoPainel.SugeridoPor(2, "Maria Souza"))),
                1, 1, 7));
    }

    @Test
    void asConsultasDoPainelTrazemOAutorEOrdenamComoOContrato() throws Exception {
        String fila = PontoRepositorio.class.getMethod("filaDoPainel", StatusDoPonto.class, Pageable.class)
                .getAnnotation(Query.class).value();
        String outros = PontoRepositorio.class.getMethod("pontosDoPainel", StatusDoPonto.class, Pageable.class)
                .getAnnotation(Query.class).value();

        assertThat(fila).contains("join fetch p.bairro", "join fetch p.autor", "where p.status = :status")
                .endsWith(" order by p.idPonto");
        assertThat(outros).contains("join fetch p.bairro b", "join fetch p.autor", "where p.status = :status")
                .endsWith(" order by b.nome, p.nome, p.idPonto");
    }

    private static Ponto ponto(int id, String nome, int idBairro, String nomeDoBairro) {
        Bairro bairro = BeanUtils.instantiateClass(Bairro.class);
        ReflectionTestUtils.setField(bairro, "idBairro", idBairro);
        ReflectionTestUtils.setField(bairro, "nome", nomeDoBairro);
        Ponto ponto = BeanUtils.instantiateClass(Ponto.class);
        ReflectionTestUtils.setField(ponto, "idPonto", id);
        ReflectionTestUtils.setField(ponto, "nome", nome);
        ReflectionTestUtils.setField(ponto, "endereco", "Endereço " + id);
        ReflectionTestUtils.setField(ponto, "bairro", bairro);
        ReflectionTestUtils.setField(ponto, "status", StatusDoPonto.APROVADO);
        return ponto;
    }
}
