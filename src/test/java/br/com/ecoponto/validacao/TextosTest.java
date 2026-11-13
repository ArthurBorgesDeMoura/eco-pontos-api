package br.com.ecoponto.validacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TextosTest {

    private static final Pattern ESPACO = Pattern.compile("[\\s\\p{Z}\\uFEFF]");

    @Test
    void apara() {
        assertThat(Textos.aparar(null)).isNull();
        assertThat(Textos.aparar("")).isEmpty();
        assertThat(Textos.aparar(" \t\n\u000B\f\r   ﻿")).isEmpty();
        assertThat(Textos.aparar("  Ponto  Central  \t")).isEqualTo("Ponto  Central");
        assertThat(Textos.aparadoOuNulo("   ")).isNull();
        assertThat(Textos.aparadoOuNulo(" a ")).isEqualTo("a");
        assertThat(Textos.temTexto("  ﻿")).isFalse();
        assertThat(Textos.temTexto("  x ")).isTrue();
        assertThat(Textos.temTexto(null)).isFalse();
    }

    @Test
    void trataComoEspacoExatamenteOQueAExpressaoDeAntesTratava() {
        for (char c = 0; c < Character.MAX_VALUE; c++) {
            String texto = "x" + c + "x";
            boolean eraEspaco = ESPACO.matcher(String.valueOf(c)).matches();
            assertThat(Textos.aparar(c + "x" + c)).as("U+%04X", (int) c).isEqualTo(eraEspaco ? "x" : c + "x" + c);
            assertThat(Textos.temTexto(String.valueOf(c))).as("U+%04X", (int) c).isEqualTo(!eraEspaco);
            assertThat(Textos.aparar(texto)).isEqualTo(texto);
        }
    }

    @Test
    void textoEnormeDeEspacosLevaTempoLinear() {
        String ataque = "a" + " ".repeat(2_000_000) + "x";

        String aparado = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> Textos.aparar(ataque));

        assertThat(aparado).isEqualTo(ataque);
        assertThat(Textos.aparar(" ".repeat(2_000_000) + "x")).isEqualTo("x");
    }
}
