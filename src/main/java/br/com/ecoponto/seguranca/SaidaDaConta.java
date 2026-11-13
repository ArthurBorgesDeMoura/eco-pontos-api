package br.com.ecoponto.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;

final class SaidaDaConta implements LogoutSuccessHandler {

    private final CsrfTokenRepository tokensCsrf;

    SaidaDaConta(CsrfTokenRepository tokensCsrf) {
        this.tokensCsrf = tokensCsrf;
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest pedido, HttpServletResponse resposta, Authentication quemSaiu) {
        tokensCsrf.saveToken(tokensCsrf.generateToken(pedido), pedido, resposta);
        resposta.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
