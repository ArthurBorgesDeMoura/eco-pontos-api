package br.com.ecoponto.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

final class TokenCsrfDeSpa implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler mascarado = new XorCsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler cru = new CsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest pedido, HttpServletResponse resposta, Supplier<CsrfToken> token) {
        mascarado.handle(pedido, resposta, token);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest pedido, CsrfToken token) {
        if (StringUtils.hasText(pedido.getHeader(token.getHeaderName()))) {
            return cru.resolveCsrfTokenValue(pedido, token);
        }
        return mascarado.resolveCsrfTokenValue(pedido, token);
    }
}
