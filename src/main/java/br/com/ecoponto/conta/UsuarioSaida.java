package br.com.ecoponto.conta;

public record UsuarioSaida(Integer idUsuario, String nome, String email, Perfil perfil) {

    public static UsuarioSaida de(Usuario usuario) {
        return new UsuarioSaida(usuario.getIdUsuario(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil());
    }
}
