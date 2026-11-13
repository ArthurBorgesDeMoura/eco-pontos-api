package br.com.ecoponto.conta;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ecoponto.contrato.ClienteDaApi;
import br.com.ecoponto.contrato.ClienteDaApi.Resposta;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CadastroSemBancoTest {

    @LocalServerPort
    private int porta;

    @DynamicPropertySource
    static void bancoNumaPortaFechada(DynamicPropertyRegistry propriedades) {
        int portaFechada = portaLivre();
        propriedades.add("spring.datasource.url", () -> "jdbc:mariadb://127.0.0.1:" + portaFechada + "/ecoponto");
    }

    @Test
    void responde503ComoOContratoEmPoucosSegundos() {
        ClienteDaApi cliente = new ClienteDaApi(porta);
        String corpo = "{\"nome\":\"Ana\",\"email\":\"ana@exemplo.com\",\"senha\":\"uma senha longa\"}";

        long inicio = System.nanoTime();
        Resposta resposta = cliente.post("/api/auth/cadastro", corpo);
        Duration tempo = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(resposta.status()).isEqualTo(503);
        assertThat(resposta.corpo()).contains("\"codigo\":\"BANCO_INDISPONIVEL\"");
        assertThat(tempo).isLessThan(Duration.ofSeconds(10));
    }

    private static int portaLivre() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
