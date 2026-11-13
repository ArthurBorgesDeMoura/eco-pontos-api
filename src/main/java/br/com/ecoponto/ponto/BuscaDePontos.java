package br.com.ecoponto.ponto;

import br.com.ecoponto.categoria.CategoriaSaida;
import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import br.com.ecoponto.lista.Pagina;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuscaDePontos {

    static final String PONTO_NAO_ENCONTRADO = "Ponto não encontrado.";

    private final PontoRepositorio pontos;

    public BuscaDePontos(PontoRepositorio pontos) {
        this.pontos = pontos;
    }

    @Transactional(readOnly = true)
    public Pagina<PontoResumo> buscar(Integer bairro, Integer categoria, int pagina, int tamanho) {
        long total = pontos.contar(StatusDoPonto.APROVADO, bairro, categoria);
        return umaPagina(total, pagina, tamanho,
                daPagina -> pontos.buscar(StatusDoPonto.APROVADO, bairro, categoria, daPagina), PontoResumo::de);
    }

    @Transactional(readOnly = true)
    public Pagina<Sugestao> minhasSugestoes(int idAutor, StatusDoPonto status, int pagina, int tamanho) {
        long total = pontos.contarSugestoes(idAutor, status);
        return umaPagina(total, pagina, tamanho,
                daPagina -> pontos.buscarSugestoes(idAutor, status, daPagina), Sugestao::de);
    }

    @Transactional(readOnly = true)
    public Pagina<PontoDoPainel> pontosDoPainel(StatusDoPonto status, int pagina, int tamanho) {
        long total = pontos.contar(status, null, null);
        return umaPagina(total, pagina, tamanho, daPagina -> status == StatusDoPonto.PENDENTE
                ? pontos.filaDoPainel(status, daPagina)
                : pontos.pontosDoPainel(status, daPagina), PontoDoPainel::de);
    }

    private <T> Pagina<T> umaPagina(long total, int pagina, int tamanho, Function<Pageable, List<Ponto>> ler,
            BiFunction<Ponto, List<MaterialSaida>, T> item) {
        long inicio = (long) (pagina - 1) * tamanho;
        if (inicio >= total) {
            return new Pagina<>(List.of(), total, pagina, tamanho);
        }

        List<Ponto> daPagina = ler.apply(PageRequest.of(pagina - 1, tamanho));
        List<Integer> ids = daPagina.stream().map(Ponto::getIdPonto).toList();
        Map<Integer, List<MaterialSaida>> materiais = new LinkedHashMap<>();
        ids.forEach(id -> materiais.put(id, new ArrayList<>()));
        if (!ids.isEmpty()) {
            pontos.materiaisDos(ids)
                    .forEach(material -> materiais.get(material.idPonto()).add(MaterialSaida.de(material)));
        }

        List<T> itens = daPagina.stream()
                .map(ponto -> item.apply(ponto, List.copyOf(materiais.get(ponto.getIdPonto()))))
                .toList();
        return new Pagina<>(itens, total, pagina, tamanho);
    }

    @Transactional(readOnly = true)
    public ContagemDePontos contar(Integer bairro) {
        long total = pontos.contar(StatusDoPonto.APROVADO, bairro, null);
        return new ContagemDePontos(total, pontos.contarPorMaterial(StatusDoPonto.APROVADO, bairro));
    }

    @Transactional(readOnly = true)
    public PontoDetalhe detalhar(int idPonto, boolean veQualquerStatus) {
        StatusDoPonto status = veQualquerStatus ? null : StatusDoPonto.APROVADO;
        Ponto ponto = pontos.detalhe(idPonto, status)
                .orElseThrow(() -> new ErroDaApi(CodigoDeErro.NAO_ENCONTRADO, PONTO_NAO_ENCONTRADO));
        List<CategoriaSaida> materiais = pontos.materiaisDos(List.of(idPonto)).stream()
                .map(MaterialDoPonto::comoCategoria)
                .toList();
        return PontoDetalhe.de(ponto, materiais);
    }
}
