package br.com.ecoponto.preparacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ecoponto.preparacao.ContasTesteService.ContaTeste;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class ContasTesteIT {

    @Autowired
    private ContasTesteService servico;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @Value("${ecoponto.senha.custo-bcrypt}")
    private int custo;

    @Test
    void asContasDeTesteFicamComHashBcryptQueConfereComASenhaDoScript() throws IOException {
        List<ContaTeste> contas = ContasTesteService.lerContas(Path.of("..", "db", "ecoponto.sql"));

        servico.refazerHashes(contas);

        String prefixo = String.format("$2a$%02d$", custo);
        for (ContaTeste conta : contas) {
            String hash = jdbc.queryForObject(
                    "SELECT senha_hash FROM usuario WHERE email = ?", String.class, conta.email());
            boolean ehBcrypt = hash != null && hash.startsWith(prefixo) && hash.length() == 60;
            assertThat(ehBcrypt).as("hash bcrypt de %s", conta.email()).isTrue();
            assertThat(codificador.matches(conta.senha(), hash)).as("senha de %s confere", conta.email()).isTrue();
        }
    }
}
