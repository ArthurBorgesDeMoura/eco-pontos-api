package br.com.ecoponto.banco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class UsuarioAplicacaoIT {

    private static final int COMANDO_NEGADO = 1142;
    private static final String BAIRRO_DE_TESTE = "Bairro do teste de permissões";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @Transactional
    void leEGravaNoBancoEcoponto() {
        Integer contas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuario WHERE email LIKE ?", Integer.class, "%@example.com");

        assertThat(contas).isGreaterThanOrEqualTo(3);
        assertThat(jdbc.update("INSERT INTO bairro (nome) VALUES (?)", BAIRRO_DE_TESTE)).isEqualTo(1);
        assertThat(jdbc.update("UPDATE bairro SET nome = ? WHERE nome = ?", BAIRRO_DE_TESTE + " 2", BAIRRO_DE_TESTE))
                .isEqualTo(1);
        assertThat(jdbc.update("DELETE FROM bairro WHERE nome = ?", BAIRRO_DE_TESTE + " 2")).isEqualTo(1);
    }

    @Test
    void naoPodeApagarTabelasNemOBanco() {
        Throwable erro = catchThrowable(() -> jdbc.execute("DROP TABLE ecoponto.tabela_que_nao_existe"));

        assertThat(erro).rootCause().isInstanceOfSatisfying(SQLException.class,
                causa -> assertThat(causa.getErrorCode()).as("código do MariaDB").isEqualTo(COMANDO_NEGADO));

        List<String> permissoes = jdbc.queryForList("SHOW GRANTS", String.class).stream()
                .map(permissao -> permissao.replaceAll("PASSWORD '[^']*'", "PASSWORD '***'"))
                .filter(permissao -> permissao.contains(" ON *.* ") || permissao.contains(" ON `ecoponto`."))
                .toList();
        assertThat(permissoes).anyMatch(p -> p.startsWith("GRANT SELECT, INSERT, UPDATE, DELETE ON `ecoponto`.*"));
        assertThat(permissoes).noneMatch(p -> p.contains("ALL PRIVILEGES") || p.contains("DROP")
                || p.contains("CREATE") || p.contains("ALTER") || p.contains("GRANT OPTION"));
    }
}
