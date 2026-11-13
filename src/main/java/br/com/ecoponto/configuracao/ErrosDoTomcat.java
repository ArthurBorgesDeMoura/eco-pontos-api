package br.com.ecoponto.configuracao;

import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

@Component
public class ErrosDoTomcat implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory fabrica) {
        fabrica.addContextCustomizers(contexto -> {
            if (contexto.getParent() instanceof StandardHost host) {
                host.setErrorReportValveClass(RelatorioEmJson.class.getName());
            }
        });
    }

    public static class RelatorioEmJson extends ErrorReportValve {

        private static final ObjectMapper JSON = new ObjectMapper();

        @Override
        protected void report(Request pedido, Response resposta, Throwable erro) {
            int status = resposta.getStatus();
            if (status < 400 || resposta.getContentWritten() > 0 || !resposta.setErrorReported()) {
                return;
            }
            try {
                resposta.setContentType("application/json");
                resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
                resposta.setHeader("X-Content-Type-Options", "nosniff");
                byte[] corpo = JSON.writeValueAsBytes(ErroApi.de(CodigoDeErro.doStatus(status)));
                resposta.getOutputStream().write(corpo);
                resposta.finishResponse();
            } catch (IOException | IllegalStateException ignorada) {
            }
        }
    }
}
