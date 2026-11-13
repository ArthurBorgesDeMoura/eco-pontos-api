package br.com.ecoponto.seguranca;

import br.com.ecoponto.conta.UsuarioSaida;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

@Component
public class Sessoes {

    private final SecurityContextRepository contextos;
    private final SessionAuthenticationStrategy aoEntrar;
    private final SecurityContextHolderStrategy guardaDoContexto = SecurityContextHolder.getContextHolderStrategy();

    public Sessoes(SecurityContextRepository contextos, SessionAuthenticationStrategy aoEntrar) {
        this.contextos = contextos;
        this.aoEntrar = aoEntrar;
    }

    public void abrir(UsuarioSaida usuario, HttpServletRequest pedido, HttpServletResponse resposta) {
        UsuarioDaSessao daSessao = UsuarioDaSessao.de(usuario);
        Authentication autenticacao = UsernamePasswordAuthenticationToken.authenticated(
                daSessao, null, List.of(new SimpleGrantedAuthority(daSessao.papel())));
        aoEntrar.onAuthentication(autenticacao, pedido, resposta);

        SecurityContext contexto = guardaDoContexto.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        guardaDoContexto.setContext(contexto);
        contextos.saveContext(contexto, pedido, resposta);

        if (pedido.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            token.getToken();
        }
    }
}
