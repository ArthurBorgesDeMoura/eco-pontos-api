package br.com.ecoponto.seguranca;

import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

@Component
public class RespostasDeSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String TOKEN_CSRF_INVALIDO = "Atualize a página e tente de novo.";

    private final ObjectMapper json;

    public RespostasDeSeguranca(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest pedido, HttpServletResponse resposta, AuthenticationException erro)
            throws IOException {
        escrever(resposta, CodigoDeErro.NAO_AUTENTICADO, CodigoDeErro.NAO_AUTENTICADO.mensagemPadrao());
    }

    @Override
    public void handle(HttpServletRequest pedido, HttpServletResponse resposta, AccessDeniedException erro)
            throws IOException {
        String mensagem = erro instanceof CsrfException ? TOKEN_CSRF_INVALIDO
                : CodigoDeErro.SEM_PERMISSAO.mensagemPadrao();
        escrever(resposta, CodigoDeErro.SEM_PERMISSAO, mensagem);
    }

    private void escrever(HttpServletResponse resposta, CodigoDeErro codigo, String mensagem) throws IOException {
        resposta.setStatus(codigo.status().value());
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(resposta.getOutputStream(), ErroApi.de(codigo, mensagem));
    }
}
