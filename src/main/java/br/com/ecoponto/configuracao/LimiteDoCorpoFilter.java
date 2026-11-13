package br.com.ecoponto.configuracao;

import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(LogRequisicoesFilter.ORDEM + 1)
public class LimiteDoCorpoFilter extends OncePerRequestFilter {

    public static final int LIMITE_EM_BYTES = 64 * 1024;
    public static final String CORPO_GRANDE_DEMAIS = "O corpo do pedido passa de 64 KB.";

    private final ObjectMapper json;

    public LimiteDoCorpoFilter(ObjectMapper json) {
        this.json = json;
    }

    public static class CorpoGrandeDemais extends IOException {

        private static final long serialVersionUID = 1L;

        CorpoGrandeDemais() {
            super(CORPO_GRANDE_DEMAIS);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        if (pedido.getContentLengthLong() > LIMITE_EM_BYTES) {
            recusar(resposta);
            return;
        }
        try {
            cadeia.doFilter(new PedidoComLimite(pedido), resposta);
        } catch (IOException | ServletException erro) {
            if (!passouDoLimite(erro) || resposta.isCommitted()) {
                throw erro;
            }
            resposta.reset();
            recusar(resposta);
        }
    }

    private void recusar(HttpServletResponse resposta) throws IOException {
        resposta.setStatus(CodigoDeErro.DADOS_INVALIDOS.status().value());
        resposta.setHeader("X-Content-Type-Options", "nosniff");
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(resposta.getOutputStream(), ErroApi.de(CodigoDeErro.DADOS_INVALIDOS, CORPO_GRANDE_DEMAIS));
    }

    private static boolean passouDoLimite(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof CorpoGrandeDemais) {
                return true;
            }
        }
        return false;
    }

    private static final class PedidoComLimite extends HttpServletRequestWrapper {

        private ServletInputStream corpo;

        PedidoComLimite(HttpServletRequest pedido) {
            super(pedido);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (corpo == null) {
                corpo = new CorpoContado(super.getInputStream());
            }
            return corpo;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String codificacao = getCharacterEncoding();
            Charset charset = codificacao == null ? StandardCharsets.UTF_8 : Charset.forName(codificacao);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class CorpoContado extends ServletInputStream {

        private final ServletInputStream original;
        private long lidos;

        CorpoContado(ServletInputStream original) {
            this.original = original;
        }

        @Override
        public int read() throws IOException {
            int lido = original.read();
            if (lido >= 0) {
                contar(1);
            }
            return lido;
        }

        @Override
        public int read(byte[] destino, int inicio, int tamanho) throws IOException {
            int lidosAgora = original.read(destino, inicio, tamanho);
            if (lidosAgora > 0) {
                contar(lidosAgora);
            }
            return lidosAgora;
        }

        private void contar(int bytes) throws CorpoGrandeDemais {
            lidos += bytes;
            if (lidos > LIMITE_EM_BYTES) {
                throw new CorpoGrandeDemais();
            }
        }

        @Override
        public boolean isFinished() {
            return original.isFinished();
        }

        @Override
        public boolean isReady() {
            return original.isReady();
        }

        @Override
        public void setReadListener(ReadListener ouvinte) {
            original.setReadListener(ouvinte);
        }
    }
}
