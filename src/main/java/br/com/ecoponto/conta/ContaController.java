package br.com.ecoponto.conta;

import br.com.ecoponto.seguranca.Sessoes;
import br.com.ecoponto.seguranca.UsuarioDaSessao;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ContaController {

    private final CadastroService cadastro;
    private final LoginService login;
    private final Sessoes sessoes;

    public ContaController(CadastroService cadastro, LoginService login, Sessoes sessoes) {
        this.cadastro = cadastro;
        this.login = login;
        this.sessoes = sessoes;
    }

    @PostMapping("/api/auth/cadastro")
    public ResponseEntity<UsuarioSaida> cadastrar(@Valid @RequestBody CadastroEntrada entrada) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cadastro.cadastrar(entrada));
    }

    @PostMapping("/api/auth/login")
    public UsuarioSaida entrar(@RequestBody LoginEntrada entrada, HttpServletRequest pedido,
            HttpServletResponse resposta) {
        UsuarioSaida usuario = login.entrar(entrada);
        sessoes.abrir(usuario, pedido, resposta);
        return usuario;
    }

    @GetMapping("/api/auth/eu")
    public UsuarioSaida quemSouEu(@AuthenticationPrincipal UsuarioDaSessao usuario) {
        return usuario.comoSaida();
    }
}
