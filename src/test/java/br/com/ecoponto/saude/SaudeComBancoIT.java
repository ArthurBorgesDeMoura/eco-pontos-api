package br.com.ecoponto.saude;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SaudeComBancoIT {

    @Autowired
    private TestRestTemplate http;

    @Test
    void respondeOkComOBancoLigado() {
        ResponseEntity<String> resposta = http.getForEntity("/api/saude", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isEqualTo("{\"status\":\"ok\",\"banco\":\"ok\"}");
    }
}
