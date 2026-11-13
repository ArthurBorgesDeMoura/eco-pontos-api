package br.com.ecoponto.conta;

import br.com.ecoponto.erro.ChaveUnica;
import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CadastroService {

    private static final String CHAVE_DO_EMAIL = "uq_usuario_email";

    private final UsuarioRepositorio usuarios;
    private final PasswordEncoder codificador;

    public CadastroService(UsuarioRepositorio usuarios, PasswordEncoder codificador) {
        this.usuarios = usuarios;
        this.codificador = codificador;
    }

    public UsuarioSaida cadastrar(CadastroEntrada entrada) {
        if (usuarios.existsByEmail(entrada.email())) {
            throw emailRepetido();
        }
        Usuario novo = Usuario.novoUsuario(entrada.nome(), entrada.email(), codificador.encode(entrada.senha()));
        return UsuarioSaida.de(gravar(novo));
    }

    Usuario gravar(Usuario novo) {
        try {
            return usuarios.saveAndFlush(novo);
        } catch (DataIntegrityViolationException erro) {
            if (ChaveUnica.violou(erro, CHAVE_DO_EMAIL)) {
                throw emailRepetido();
            }
            throw erro;
        }
    }

    private static ErroDaApi emailRepetido() {
        String mensagem = CodigoDeErro.EMAIL_JA_CADASTRADO.mensagemPadrao();
        return new ErroDaApi(CodigoDeErro.EMAIL_JA_CADASTRADO, mensagem, Map.of("email", mensagem));
    }
}
