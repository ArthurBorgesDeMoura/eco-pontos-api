package br.com.ecoponto.categoria;

import br.com.ecoponto.erro.ChaveUnica;
import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import br.com.ecoponto.lista.Pagina;
import br.com.ecoponto.ponto.StatusDoPonto;
import br.com.ecoponto.validacao.ValidadorDeEntradas;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PainelDeCategorias {

    static final String CATEGORIA_NAO_ENCONTRADA = "Categoria não encontrada.";

    private static final String CHAVE_DO_NOME = "uq_categoria_nome";

    private final CategoriaRepositorio categorias;
    private final ValidadorDeEntradas validador;

    public PainelDeCategorias(CategoriaRepositorio categorias, ValidadorDeEntradas validador) {
        this.categorias = categorias;
        this.validador = validador;
    }

    @Transactional(readOnly = true)
    public Pagina<CategoriaDoPainel> listar(int pagina, int tamanho) {
        long total = categorias.count();
        if ((long) (pagina - 1) * tamanho >= total) {
            return new Pagina<>(List.of(), total, pagina, tamanho);
        }
        return new Pagina<>(categorias.doPainel(StatusDoPonto.APROVADO, PageRequest.of(pagina - 1, tamanho)), total,
                pagina, tamanho);
    }

    @Transactional
    public CategoriaDoPainel criar(CategoriaEntrada entrada) {
        ValidadorDeEntradas.exigirSemErros(validador.erros(entrada));
        if (categorias.existsByNome(entrada.nome())) {
            throw nomeRepetido();
        }
        Categoria nova = Categoria.nova(entrada.nome(), entrada.descricao());
        gravar(() -> categorias.saveAndFlush(nova));
        return new CategoriaDoPainel(nova.getIdCategoria(), nova.getNome(), nova.getDescricao(), 0);
    }

    @Transactional
    public CategoriaDoPainel editar(int idCategoria, CategoriaEntrada entrada) {
        Categoria categoria = categorias.findById(idCategoria)
                .orElseThrow(() -> new ErroDaApi(CodigoDeErro.NAO_ENCONTRADO, CATEGORIA_NAO_ENCONTRADA));
        ValidadorDeEntradas.exigirSemErros(validador.erros(entrada));
        if (categorias.existsByNomeAndIdCategoriaNot(entrada.nome(), idCategoria)) {
            throw nomeRepetido();
        }
        categoria.editar(entrada.nome(), entrada.descricao());
        gravar(categorias::flush);
        return categorias.umaDoPainel(idCategoria, StatusDoPonto.APROVADO);
    }

    private static void gravar(Runnable gravacao) {
        try {
            gravacao.run();
        } catch (DataIntegrityViolationException erro) {
            if (ChaveUnica.violou(erro, CHAVE_DO_NOME)) {
                throw nomeRepetido();
            }
            throw erro;
        }
    }

    private static ErroDaApi nomeRepetido() {
        String mensagem = CodigoDeErro.NOME_REPETIDO.mensagemPadrao();
        return new ErroDaApi(CodigoDeErro.NOME_REPETIDO, mensagem, Map.of("nome", mensagem));
    }
}
