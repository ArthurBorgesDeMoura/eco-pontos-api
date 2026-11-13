package br.com.ecoponto.ponto;

import br.com.ecoponto.categoria.CategoriaRepositorio;
import br.com.ecoponto.categoria.CategoriaSaida;
import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import br.com.ecoponto.ponto.ConferenciaDoPonto.Conferida;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PainelDePontos {

    private final PontoRepositorio pontos;
    private final CategoriaRepositorio categorias;
    private final ConferenciaDoPonto conferencia;

    public PainelDePontos(PontoRepositorio pontos, CategoriaRepositorio categorias, ConferenciaDoPonto conferencia) {
        this.pontos = pontos;
        this.categorias = categorias;
        this.conferencia = conferencia;
    }

    @Transactional(readOnly = true)
    public ResumoDoPainel resumo() {
        return new ResumoDoPainel(pontos.contar(StatusDoPonto.PENDENTE, null, null),
                pontos.contar(StatusDoPonto.APROVADO, null, null), categorias.count());
    }

    @Transactional
    public PontoDoPainel decidir(int idPonto, StatusDoPonto decisao) {
        if (decisao == StatusDoPonto.PENDENTE) {
            throw new IllegalArgumentException("A decisão é APROVADO ou RECUSADO.");
        }
        travar(idPonto);
        if (pontos.decidir(idPonto, StatusDoPonto.PENDENTE.name(), decisao.name()) == 0) {
            throw new ErroDaApi(CodigoDeErro.JA_ANALISADO);
        }
        Ponto ponto = pontos.doPainel(idPonto).orElseThrow();
        List<MaterialSaida> materiais = pontos.materiaisDos(List.of(idPonto)).stream().map(MaterialSaida::de).toList();
        return PontoDoPainel.de(ponto, materiais);
    }

    @Transactional
    public PontoDetalhe editar(int idPonto, PontoEntrada entrada) {
        Ponto ponto = travar(idPonto);
        Conferida conferida = conferencia.conferir(entrada);
        ponto.editar(entrada.nome(), entrada.endereco(), conferida.bairro(), entrada.horario(), entrada.contato(),
                conferida.materiais());
        pontos.flush();
        return PontoDetalhe.de(ponto, conferida.materiais().stream().map(CategoriaSaida::de).toList());
    }

    @Transactional
    public void excluir(int idPonto) {
        travar(idPonto);
        pontos.excluir(idPonto);
    }

    private Ponto travar(int idPonto) {
        return pontos.travar(idPonto)
                .orElseThrow(() -> new ErroDaApi(CodigoDeErro.NAO_ENCONTRADO, BuscaDePontos.PONTO_NAO_ENCONTRADO));
    }
}
