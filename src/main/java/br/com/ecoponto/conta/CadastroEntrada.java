package br.com.ecoponto.conta;

import br.com.ecoponto.validacao.MaximoDeBytes;
import br.com.ecoponto.validacao.TemTexto;
import br.com.ecoponto.validacao.Textos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record CadastroEntrada(
        @NotBlank(message = "Informe seu nome.")
        @Size(max = 100, message = "Use até 100 caracteres.")
        String nome,

        @NotBlank(message = "Informe seu e-mail.")
        @Size(max = 150, message = "Use até 150 caracteres.")
        @Pattern(regexp = "(?U)[^\\s@]+@[^\\s@]+\\.[^\\s@]+",
                message = "Digite um e-mail válido, como nome@exemplo.com.")
        String email,

        @TemTexto(message = "Informe sua senha.")
        @Size(min = 8, message = "A senha precisa ter pelo menos 8 caracteres.")
        @MaximoDeBytes(value = 72, message = "Use uma senha mais curta: até 72 caracteres sem acento.")
        String senha) {

    public CadastroEntrada {
        nome = Textos.aparar(nome);
        email = email == null ? null : Textos.aparar(email).toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "CadastroEntrada[nome=" + nome + ", email=" + email + "]";
    }
}
