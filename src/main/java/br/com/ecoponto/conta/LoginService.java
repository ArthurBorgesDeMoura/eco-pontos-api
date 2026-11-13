package br.com.ecoponto.conta;

import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {

    private static final Logger LOG = LoggerFactory.getLogger(LoginService.class);

    private static final int MAXIMO_DE_BYTES = 72;
    private static final int MAXIMO_DO_EMAIL = 150;

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder codificador;
    private final TentativasDeLogin tentativas;
    private final String hashDeNinguem;

    public LoginService(UsuarioRepositorio usuarios, PasswordEncoder codificador, TentativasDeLogin tentativas) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.tentativas = tentativas;
        this.hashDeNinguem = codificador.encode(UUID.randomUUID().toString());
    }

    public UsuarioSaida entrar(LoginEntrada entrada) {
        String email = entrada.email();
        if (email.isEmpty() || email.length() > MAXIMO_DO_EMAIL) {
            confere(entrada.senha(), hashDeNinguem);
            LOG.warn("Login recusado: e-mail vazio ou com mais de {} caracteres.", MAXIMO_DO_EMAIL);
            throw new ErroDaApi(CodigoDeErro.CREDENCIAIS_INVALIDAS);
        }

        int falhas = tentativas.reservar(email);
        Optional<Usuario> conta;
        boolean senhaConfere;
        try {
            conta = buscar(email);
            senhaConfere = confere(entrada.senha(), conta.map(Usuario::getSenhaHash).orElse(hashDeNinguem));
        } catch (RuntimeException e) {
            tentativas.devolver(email);
            throw e;
        }

        if (conta.isEmpty() || !senhaConfere) {
            String quem = conta.map(LoginService::descrever).orElse("um e-mail sem conta");
            LOG.warn("Login recusado para {} ({} falha(s) seguida(s)).", quem, falhas);
            if (tentativas.bloqueou(falhas)) {
                LOG.warn("Login de {} bloqueado por {}.", quem, tentativas.bloqueio());
            }
            throw new ErroDaApi(CodigoDeErro.CREDENCIAIS_INVALIDAS);
        }

        tentativas.limpar(email);
        Usuario usuario = conta.get();
        LOG.info("Login da {}.", descrever(usuario));
        return UsuarioSaida.de(usuario);
    }

    private Optional<Usuario> buscar(String email) {
        return usuarios.findByEmail(email)
                .filter(conta -> conta.getEmail().toLowerCase(Locale.ROOT).equals(email));
    }

    private static String descrever(Usuario conta) {
        return "conta " + conta.getIdUsuario() + " (" + Logs.seguro(conta.getEmail()) + ")";
    }

    private boolean confere(String senha, String hash) {
        boolean senhaUsavel = !senha.isEmpty() && senha.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_DE_BYTES;
        boolean confere = codificador.matches(senhaUsavel ? senha : "-", hash);
        return senhaUsavel && confere;
    }
}
