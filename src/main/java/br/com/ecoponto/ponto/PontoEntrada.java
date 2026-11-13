package br.com.ecoponto.ponto;

import br.com.ecoponto.validacao.SemRepeticao;
import br.com.ecoponto.validacao.Textos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record PontoEntrada(
        @NotBlank(message = "Informe o nome do ponto.")
        @Size(max = 120, message = "Use até 120 caracteres.")
        String nome,

        @NotBlank(message = "Informe o endereço.")
        @Size(max = 200, message = "Use até 200 caracteres.")
        String endereco,

        @NotNull(message = "Escolha o bairro.")
        Integer idBairro,

        @Size(max = 100, message = "Use até 100 caracteres.")
        String horario,

        @Size(max = 100, message = "Use até 100 caracteres.")
        String contato,

        @NotEmpty(message = "Escolha pelo menos um material.")
        @Size(max = 100, message = "Escolha só materiais da lista, sem repetir.")
        @SemRepeticao(message = "Escolha só materiais da lista, sem repetir.")
        List<Integer> categorias) {

    public PontoEntrada {
        nome = Textos.aparar(nome);
        endereco = Textos.aparar(endereco);
        horario = Textos.aparadoOuNulo(horario);
        contato = Textos.aparadoOuNulo(contato);
        categorias = categorias == null ? null : Collections.unmodifiableList(new ArrayList<>(categorias));
    }
}
