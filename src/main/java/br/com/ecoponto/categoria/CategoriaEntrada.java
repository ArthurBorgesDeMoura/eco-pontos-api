package br.com.ecoponto.categoria;

import br.com.ecoponto.validacao.Textos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaEntrada(
        @NotBlank(message = "Informe o nome da categoria.")
        @Size(max = 60, message = "Use até 60 caracteres.")
        String nome,

        @Size(max = 255, message = "Use até 255 caracteres.")
        String descricao) {

    public CategoriaEntrada {
        nome = Textos.aparar(nome);
        descricao = Textos.aparadoOuNulo(descricao);
    }
}
