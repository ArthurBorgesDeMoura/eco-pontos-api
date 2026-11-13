package br.com.ecoponto.validacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ecoponto.erro.ErroDaApi;
import org.junit.jupiter.api.Test;

class FiltrosTest {

    @Test
    void semParametrosValemOsPadroes() {
        Filtros filtros = new Filtros();

        assertThat(filtros.id("bairro", null, Filtros.ID_DE_BAIRRO)).isNull();
        assertThat(filtros.pagina(null)).isEqualTo(1);
        assertThat(filtros.tamanho(null)).isEqualTo(7);
        assertThat(filtros.opcao("status", null, Filtros.STATUS_DO_PONTO, "PENDENTE", Filtros.STATUS))
                .isEqualTo("PENDENTE");
        assertThatCode(() -> filtros.conferir("Confira os filtros.")).doesNotThrowAnyException();
    }

    @Test
    void valoresValidosPassam() {
        Filtros filtros = new Filtros();

        assertThat(filtros.id("bairro", "004", Filtros.ID_DE_BAIRRO)).isEqualTo(4);
        assertThat(filtros.pagina("2")).isEqualTo(2);
        assertThat(filtros.tamanho("50")).isEqualTo(50);
        assertThat(filtros.opcao("status", "RECUSADO", Filtros.STATUS_DO_PONTO, null, Filtros.STATUS))
                .isEqualTo("RECUSADO");
        assertThatCode(() -> filtros.conferir("Confira os filtros.")).doesNotThrowAnyException();
    }

    @Test
    void juntaOErroDeTodosOsParametrosInvalidos() {
        Filtros filtros = new Filtros();
        filtros.id("bairro", "abc", Filtros.ID_DE_BAIRRO);
        filtros.id("categoria", "-1", Filtros.ID_DE_CATEGORIA);
        filtros.pagina("0");
        filtros.tamanho("51");
        filtros.opcao("status", "TODOS", Filtros.STATUS_DO_PONTO, null, Filtros.STATUS);

        assertThatThrownBy(() -> filtros.conferir("Confira os filtros da busca."))
                .isInstanceOfSatisfying(ErroDaApi.class, erro -> {
                    assertThat(erro.corpo().mensagem()).isEqualTo("Confira os filtros da busca.");
                    assertThat(erro.corpo().campos()).containsOnlyKeys("bairro", "categoria", "pagina", "tamanho",
                            "status");
                });
    }

    @Test
    void idDoCaminhoSoAceitaDigitosDe1EmDiante() {
        assertThat(Filtros.idDoCaminho("13", Filtros.ID_DE_PONTO)).isEqualTo(13);
        assertThat(Filtros.idDoCaminho("013", Filtros.ID_DE_PONTO)).isEqualTo(13);
        assertThat(Filtros.idDoCaminho("999999999", Filtros.ID_DE_PONTO)).isEqualTo(999_999_999);
        for (String invalido : new String[] {null, "", "0", "-1", "+13", " 13", "13 ", "0x0D", "#0D", "1e1", "1.0",
            "abc", "9999999999", "١٣"}) {
            assertThatThrownBy(() -> Filtros.idDoCaminho(invalido, Filtros.ID_DE_PONTO)).as(invalido)
                    .isInstanceOfSatisfying(ErroDaApi.class, erro -> {
                        assertThat(erro.corpo().mensagem()).isEqualTo("Use o id de um ponto.");
                        assertThat(erro.corpo().campos()).isEmpty();
                    });
        }
    }

    @Test
    void numeroGrandeDemaisNaoEUmId() {
        Filtros filtros = new Filtros();

        assertThat(filtros.id("bairro", "9999999999", Filtros.ID_DE_BAIRRO)).isNull();
        assertThatThrownBy(() -> filtros.conferir("Confira os filtros.")).isInstanceOf(ErroDaApi.class);
    }
}
