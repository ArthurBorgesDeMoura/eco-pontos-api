package br.com.ecoponto.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class TentativasDeLoginTest {

    private static final String MARIA = "maria.souza@example.com";

    private final RelogioDeTeste relogio = new RelogioDeTeste();
    private final TentativasDeLogin tentativas = new TentativasDeLogin(relogio, 5, Duration.ofMinutes(5));

    @Test
    void cincoTentativasPassamEAQuintaBloqueia() {
        for (int i = 1; i <= 5; i++) {
            assertThat(tentativas.reservar(MARIA)).isEqualTo(i);
        }

        assertThat(tentativas.bloqueou(5)).isTrue();
        assertThat(tentativas.bloqueou(4)).isFalse();
    }

    @Test
    void depoisDe5FalhasATentativaSeguinteLeva429ComRetryAfter() {
        falhar(MARIA, 5);

        assertThatThrownBy(() -> tentativas.reservar(MARIA))
                .isInstanceOfSatisfying(ErroDaApi.class, erro -> {
                    assertThat(erro.codigo()).isEqualTo(CodigoDeErro.MUITAS_TENTATIVAS);
                    assertThat(erro.cabecalhos().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("300");
                });
    }

    @Test
    void oAcessoVoltaDepoisDoPrazo() {
        falhar(MARIA, 5);

        relogio.avancar(Duration.ofMinutes(4).plusSeconds(59));
        assertThatThrownBy(() -> tentativas.reservar(MARIA))
                .isInstanceOfSatisfying(ErroDaApi.class,
                        erro -> assertThat(erro.cabecalhos().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1"));

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(tentativas.reservar(MARIA)).as("as falhas recomeçam do zero").isEqualTo(1);
    }

    @Test
    void oLoginCertoZeraAsFalhas() {
        falhar(MARIA, 4);
        tentativas.limpar(MARIA);
        falhar(MARIA, 4);

        assertThatCode(() -> tentativas.reservar(MARIA)).doesNotThrowAnyException();
    }

    @Test
    void falhaMaisAntigaQueOPrazoNaoContaComoSeguida() {
        falhar(MARIA, 4);
        relogio.avancar(Duration.ofMinutes(6));

        assertThat(tentativas.reservar(MARIA)).isEqualTo(1);
    }

    @Test
    void cadaEmailTemAsSuasFalhas() {
        falhar(MARIA, 5);

        assertThat(tentativas.reservar("joao.pereira@example.com")).isEqualTo(1);
    }

    @Test
    void aReservaDevolvidaNaoContaNemBloqueia() {
        falhar(MARIA, 5);
        tentativas.devolver(MARIA);

        assertThat(tentativas.reservar(MARIA)).as("a 5ª volta a valer").isEqualTo(5);
    }

    @Test
    void pedidosSimultaneosNoMesmoEmailNaoPassamDe5() throws Exception {
        int pedidos = 64;
        ExecutorService threads = Executors.newFixedThreadPool(pedidos);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        try {
            for (int i = 0; i < pedidos; i++) {
                resultados.add(threads.submit(() -> {
                    largada.await();
                    try {
                        tentativas.reservar(MARIA);
                        return true;
                    } catch (ErroDaApi bloqueado) {
                        return false;
                    }
                }));
            }
            largada.countDown();
            int passaram = 0;
            for (Future<Boolean> resultado : resultados) {
                passaram += resultado.get(10, TimeUnit.SECONDS) ? 1 : 0;
            }

            assertThat(passaram).isEqualTo(5);
        } finally {
            threads.shutdownNow();
        }
    }

    private void falhar(String email, int vezes) {
        for (int i = 0; i < vezes; i++) {
            tentativas.reservar(email);
        }
    }
}
