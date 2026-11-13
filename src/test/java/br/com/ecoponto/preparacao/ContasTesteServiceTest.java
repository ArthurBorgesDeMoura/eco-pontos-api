package br.com.ecoponto.preparacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.ecoponto.preparacao.ContasTesteService.ContaTeste;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionOperations;

class ContasTesteServiceTest {

    private static final Path SCRIPT = Path.of("..", "db", "ecoponto.sql");
    private static final String BANCO_LOCAL = "jdbc:mariadb://localhost:3306/ecoponto";

    private final NamedParameterJdbcOperations jdbc = mock(NamedParameterJdbcOperations.class);
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(4);
    private final ContasTesteService servico = servicoNoBanco(BANCO_LOCAL);

    @Test
    void leAsTresContasDeTesteDoScriptDoBanco() throws IOException {
        List<ContaTeste> contas = ContasTesteService.lerContas(SCRIPT);

        assertThat(contas).extracting(ContaTeste::email)
                .containsExactly("admin@example.com", "maria.souza@example.com", "joao.pereira@example.com");
        assertThat(contas).allSatisfy(conta -> assertThat(conta.senha()).isNotBlank());
    }

    @Test
    void gravaUmHashBcryptQueConfereComASenhaDeCadaConta() throws IOException {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
        List<ContaTeste> contas = ContasTesteService.lerContas(SCRIPT);

        int quantas = servico.refazerHashes(contas);

        ArgumentCaptor<SqlParameterSource> parametros = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc, times(3)).update(
                eq("UPDATE usuario SET senha_hash = :hash WHERE email = :email"), parametros.capture());
        assertThat(quantas).isEqualTo(3);
        for (int i = 0; i < contas.size(); i++) {
            SqlParameterSource gravado = parametros.getAllValues().get(i);
            String hash = String.valueOf(gravado.getValue("hash"));
            assertThat(gravado.getValue("email")).isEqualTo(contas.get(i).email());
            assertThat(hash.startsWith("$2a$04$")).as("hash bcrypt de custo 4").isTrue();
            assertThat(bcrypt.matches(contas.get(i).senha(), hash)).as("o hash confere com a senha").isTrue();
        }
    }

    @Test
    void falhaQuandoAContaNaoEstaNoBanco() {
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);

        assertThatThrownBy(() -> servico.refazerHashes(List.of(new ContaTeste("ninguem@example.com", "qualquer"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db/ecoponto.sql");
    }

    @Test
    void recusaBancoQueNaoELocalSemTocarNoBanco() throws IOException {
        ContasTesteService noBancoDeOutraMaquina = servicoNoBanco("jdbc:mariadb://192.168.0.10:3306/ecoponto");

        assertThatThrownBy(() -> noBancoDeOutraMaquina.refazerHashes(ContasTesteService.lerContas(SCRIPT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("só roda em banco local");
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "jdbc:mariadb://localhost:3306/ecoponto",
        "jdbc:mariadb://LOCALHOST/ecoponto",
        "jdbc:mariadb://127.0.0.1:3306/ecoponto",
        "jdbc:mariadb://[::1]:3306/ecoponto"
    })
    void aceitaBancoLocal(String url) {
        assertThatCode(() -> ContasTesteService.exigirBancoLocal(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "jdbc:mariadb://banco.exemplo.com:3306/ecoponto",
        "jdbc:mariadb://192.168.0.10/ecoponto",
        "jdbc:mariadb://localhost.exemplo.com/ecoponto",
        "jdbc:mysql://localhost/ecoponto"
    })
    void recusaBancoQueNaoELocal(String url) {
        assertThatThrownBy(() -> ContasTesteService.exigirBancoLocal(url))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("só roda em banco local");
    }

    @Test
    void oToStringDaContaNaoMostraASenha() {
        assertThat(new ContaTeste("maria.souza@example.com", "SenhaQueNaoAparece"))
                .hasToString("ContaTeste[email=maria.souza@example.com]");
    }

    private ContasTesteService servicoNoBanco(String url) {
        return new ContasTesteService(jdbc, bcrypt, TransactionOperations.withoutTransaction(), url);
    }
}
