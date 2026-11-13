package br.com.ecoponto.seguranca;

import static org.mockito.Mockito.when;

import br.com.ecoponto.conta.Perfil;
import br.com.ecoponto.conta.Usuario;
import br.com.ecoponto.conta.UsuarioRepositorio;
import br.com.ecoponto.conta.UsuarioSaida;
import br.com.ecoponto.preparacao.ContasTesteService;
import br.com.ecoponto.preparacao.ContasTesteService.ContaTeste;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class ContasDoScript {

    public static final String ADMIN = "admin@example.com";
    public static final String MARIA = "maria.souza@example.com";
    public static final String JOAO = "joao.pereira@example.com";

    private static final List<ContaTeste> CONTAS = lerDoScript();
    private static final List<UsuarioSaida> USUARIOS = List.of(
            new UsuarioSaida(1, "Administrador EcoPonto", ADMIN, Perfil.ADMIN),
            new UsuarioSaida(2, "Maria Souza", MARIA, Perfil.USUARIO),
            new UsuarioSaida(3, "João Pereira", JOAO, Perfil.USUARIO));

    private ContasDoScript() {
    }

    public static void simular(UsuarioRepositorio usuarios, PasswordEncoder codificador) {
        for (UsuarioSaida dados : USUARIOS) {
            String hash = codificador.encode(senha(dados.email()));
            Usuario usuario = Usuario.novoUsuario(dados.nome(), dados.email(), hash);
            ReflectionTestUtils.setField(usuario, "idUsuario", dados.idUsuario());
            ReflectionTestUtils.setField(usuario, "perfil", dados.perfil());
            when(usuarios.findByEmail(dados.email())).thenReturn(Optional.of(usuario));
        }
    }

    public static List<ContaTeste> todas() {
        return CONTAS;
    }

    public static String senha(String email) {
        return CONTAS.stream().filter(conta -> conta.email().equals(email)).findFirst().orElseThrow().senha();
    }

    public static String corpoDoLogin(String email) {
        return corpo(email, senha(email));
    }

    public static String corpo(String email, String senha) {
        return "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}";
    }

    public static RequestPostProcessor sessaoDe(String email) {
        if (email == null) {
            return SecurityMockMvcRequestPostProcessors.anonymous();
        }
        UsuarioDaSessao usuario = UsuarioDaSessao.de(
                USUARIOS.stream().filter(conta -> conta.email().equals(email)).findFirst().orElseThrow());
        return SecurityMockMvcRequestPostProcessors.authentication(UsernamePasswordAuthenticationToken.authenticated(
                usuario, null, List.of(new SimpleGrantedAuthority(usuario.papel()))));
    }

    private static List<ContaTeste> lerDoScript() {
        try {
            return ContasTesteService.lerContas(Path.of("..", "db", "ecoponto.sql"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
