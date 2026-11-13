package br.com.ecoponto.saude;

import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroApi;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SaudeController {

    private static final Logger LOG = LoggerFactory.getLogger(SaudeController.class);
    private static final int ESPERA_VALIDACAO_SEGUNDOS = 2;

    private final DataSource dataSource;

    public SaudeController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/saude")
    public ResponseEntity<Object> verificar() {
        if (bancoResponde()) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(new Saude("ok", "ok"));
        }
        return ResponseEntity.status(CodigoDeErro.BANCO_INDISPONIVEL.status())
                .cacheControl(CacheControl.noStore())
                .body(ErroApi.de(CodigoDeErro.BANCO_INDISPONIVEL));
    }

    private boolean bancoResponde() {
        try (Connection conexao = dataSource.getConnection()) {
            return conexao.isValid(ESPERA_VALIDACAO_SEGUNDOS);
        } catch (SQLException | RuntimeException e) {
            LOG.warn("O banco não respondeu: {}", e.getMessage());
            return false;
        }
    }

    public record Saude(String status, String banco) {
    }
}
