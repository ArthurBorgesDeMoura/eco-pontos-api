package br.com.ecoponto.seguranca;

import br.com.ecoponto.conta.Perfil;
import br.com.ecoponto.conta.UsuarioSaida;
import java.io.Serializable;

public record UsuarioDaSessao(Integer idUsuario, String nome, String email, Perfil perfil) implements Serializable {

    public static UsuarioDaSessao de(UsuarioSaida usuario) {
        return new UsuarioDaSessao(usuario.idUsuario(), usuario.nome(), usuario.email(), usuario.perfil());
    }

    public UsuarioSaida comoSaida() {
        return new UsuarioSaida(idUsuario, nome, email, perfil);
    }

    public String papel() {
        return "ROLE_" + perfil.name();
    }
}
