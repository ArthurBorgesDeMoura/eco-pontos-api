package br.com.ecoponto.configuracao;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(LogRequisicoesFilter.ORDEM)
public class LogRequisicoesFilter extends OncePerRequestFilter {

    static final int ORDEM = Ordered.HIGHEST_PRECEDENCE + 10;

    private static final Logger LOG = LoggerFactory.getLogger("ecoponto.requisicoes");
    private static final List<String> NOMES_SENSIVEIS =
            List.of("senha", "password", "passwd", "token", "segredo", "secret");

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        long inicio = System.nanoTime();
        int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        try {
            cadeia.doFilter(requisicao, resposta);
            status = resposta.getStatus();
        } finally {
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            LOG.info("{} {} {} {} ms", requisicao.getMethod(), caminhoSeguro(requisicao), status, ms);
        }
    }

    static String caminhoSeguro(HttpServletRequest requisicao) {
        String caminho = requisicao.getRequestURI();
        String consulta = requisicao.getQueryString();
        if (consulta != null && !consulta.isEmpty()) {
            caminho += "?" + mascarar(consulta);
        }
        return caminho.replaceAll("[\\r\\n\\t]", "_");
    }

    static String mascarar(String consulta) {
        return Arrays.stream(consulta.split("&"))
                .map(LogRequisicoesFilter::mascararParametro)
                .collect(Collectors.joining("&"));
    }

    private static String mascararParametro(String parametro) {
        int igual = parametro.indexOf('=');
        String nome = igual < 0 ? parametro : parametro.substring(0, igual);
        String nomeMinusculo = nome.toLowerCase(Locale.ROOT);
        boolean sensivel = NOMES_SENSIVEIS.stream().anyMatch(nomeMinusculo::contains);
        return sensivel ? nome + "=***" : parametro;
    }
}
