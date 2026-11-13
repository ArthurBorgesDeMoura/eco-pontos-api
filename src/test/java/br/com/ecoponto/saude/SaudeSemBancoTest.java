package br.com.ecoponto.saude;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SaudeSemBancoTest {

    @Autowired
    private TestRestTemplate http;

    @DynamicPropertySource
    static void bancoNumaPortaFechada(DynamicPropertyRegistry propriedades) {
        int porta = portaLivre();
        propriedades.add("spring.datasource.url", () -> "jdbc:mariadb://127.0.0.1:" + porta + "/ecoponto");
    }

    @Test
    void respondeIndisponivelEmPoucosSegundosComOBancoDesligado() {
        long inicio = System.nanoTime();
        ResponseEntity<String> resposta = http.getForEntity("/api/saude", String.class);
        Duration tempo = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resposta.getBody()).contains("\"codigo\":\"BANCO_INDISPONIVEL\"");
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
