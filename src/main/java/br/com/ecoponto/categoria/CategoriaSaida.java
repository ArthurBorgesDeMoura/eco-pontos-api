package br.com.ecoponto.categoria;

import br.com.ecoponto.validacao.Textos;

public record CategoriaSaida(Integer idCategoria, String nome, String descricao) {

    public static CategoriaSaida de(Categoria categoria) {
        return de(categoria.getIdCategoria(), categoria.getNome(), categoria.getDescricao());
    }

    public static CategoriaSaida de(Integer idCategoria, String nome, String descricao) {
        return new CategoriaSaida(idCategoria, nome, Textos.temTexto(descricao) ? descricao : null);
    }
}
