package br.com.ecoponto.ponto;

import br.com.ecoponto.bairro.BairroSaida;
import java.util.List;

public record PontoResumo(Integer idPonto, String nome, String endereco, BairroSaida bairro,
        List<MaterialSaida> materiais) {

    public static PontoResumo de(Ponto ponto, List<MaterialSaida> materiais) {
        return new PontoResumo(ponto.getIdPonto(), ponto.getNome(), ponto.getEndereco(),
                BairroSaida.de(ponto.getBairro()), materiais);
    }
}
