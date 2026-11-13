package br.com.ecoponto.ponto;

import br.com.ecoponto.bairro.BairroSaida;
import br.com.ecoponto.categoria.CategoriaSaida;
import java.util.List;

public record PontoCriado(Integer idPonto, String nome, String endereco, String horario, String contato,
        BairroSaida bairro, List<CategoriaSaida> materiais, StatusDoPonto status) {

    static PontoCriado de(Ponto ponto) {
        List<CategoriaSaida> materiais = ponto.getMateriais().stream().map(CategoriaSaida::de).toList();
        PontoDetalhe detalhe = PontoDetalhe.de(ponto, materiais);
        return new PontoCriado(detalhe.idPonto(), detalhe.nome(), detalhe.endereco(), detalhe.horario(),
                detalhe.contato(), detalhe.bairro(), detalhe.materiais(), ponto.getStatus());
    }
}
