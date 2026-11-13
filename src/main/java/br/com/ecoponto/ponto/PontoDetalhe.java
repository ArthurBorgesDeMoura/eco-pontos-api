package br.com.ecoponto.ponto;

import br.com.ecoponto.bairro.BairroSaida;
import br.com.ecoponto.categoria.CategoriaSaida;
import br.com.ecoponto.validacao.Textos;
import java.util.List;

public record PontoDetalhe(Integer idPonto, String nome, String endereco, String horario, String contato,
        BairroSaida bairro, List<CategoriaSaida> materiais) {

    public static PontoDetalhe de(Ponto ponto, List<CategoriaSaida> materiais) {
        return new PontoDetalhe(ponto.getIdPonto(), ponto.getNome(), ponto.getEndereco(),
                textoOuNulo(ponto.getHorario()), textoOuNulo(ponto.getContato()), BairroSaida.de(ponto.getBairro()),
                materiais);
    }

    static String textoOuNulo(String texto) {
        return Textos.temTexto(texto) ? texto : null;
    }
}
