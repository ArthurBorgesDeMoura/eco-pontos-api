package br.com.ecoponto.ponto;

import br.com.ecoponto.bairro.BairroSaida;
import java.util.List;

public record Sugestao(Integer idPonto, String nome, String endereco, BairroSaida bairro,
        List<MaterialSaida> materiais, StatusDoPonto status) {

    public static Sugestao de(Ponto ponto, List<MaterialSaida> materiais) {
        return new Sugestao(ponto.getIdPonto(), ponto.getNome(), ponto.getEndereco(),
                BairroSaida.de(ponto.getBairro()), materiais, ponto.getStatus());
    }
}
