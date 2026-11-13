package br.com.ecoponto.conta;

import br.com.ecoponto.validacao.Textos;
import java.util.Locale;

public record LoginEntrada(String email, String senha) {

    public LoginEntrada {
        email = email == null ? "" : Textos.aparar(email).toLowerCase(Locale.ROOT);
        senha = senha == null ? "" : senha;
    }

    @Override
    public String toString() {
        return "LoginEntrada[email=" + email + "]";
    }
}
