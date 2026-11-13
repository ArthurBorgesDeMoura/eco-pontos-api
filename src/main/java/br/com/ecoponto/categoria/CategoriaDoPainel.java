package br.com.ecoponto.categoria;

import br.com.ecoponto.validacao.Textos;

public record CategoriaDoPainel(Integer idCategoria, String nome, String descricao, long pontos) {

    public CategoriaDoPainel {
        descricao = Textos.temTexto(descricao) ? descricao : null;
    }
}
