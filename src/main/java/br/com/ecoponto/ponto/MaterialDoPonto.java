package br.com.ecoponto.ponto;

import br.com.ecoponto.categoria.CategoriaSaida;

public record MaterialDoPonto(Integer idPonto, Integer idCategoria, String nome, String descricao) {

    public CategoriaSaida comoCategoria() {
        return CategoriaSaida.de(idCategoria, nome, descricao);
    }
}
