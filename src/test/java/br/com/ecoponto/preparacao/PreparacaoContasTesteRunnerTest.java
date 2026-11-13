package br.com.ecoponto.preparacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PreparacaoContasTesteRunnerTest {

    @Test
    void achaOScriptDoBancoAPartirDaPastaBackend() {
        assertThat(PreparacaoContasTesteRunner.acharScript()).endsWith(Path.of("db", "ecoponto.sql"));
    }
}
