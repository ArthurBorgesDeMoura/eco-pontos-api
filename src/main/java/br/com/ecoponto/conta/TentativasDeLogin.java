package br.com.ecoponto.conta;

import br.com.ecoponto.erro.ErroDaApi;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TentativasDeLogin {

    private static final int LIMITE_DE_EMAILS = 10_000;

    private final Clock relogio;
    private final int falhasParaBloquear;
    private final Duration bloqueio;
    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();

    private record Estado(int falhas, Instant ultimaFalha, Instant bloqueadoAte) {
    }

    public TentativasDeLogin(Clock relogio, @Value("${ecoponto.login.falhas-para-bloquear}") int falhasParaBloquear,
            @Value("${ecoponto.login.bloqueio}") Duration bloqueio) {
        this.relogio = relogio;
        this.falhasParaBloquear = falhasParaBloquear;
        this.bloqueio = bloqueio;
    }

    public int reservar(String email) {
        Instant agora = relogio.instant();
        if (estados.size() >= LIMITE_DE_EMAILS) {
            estados.values().removeIf(estado -> velha(estado, agora));
        }
        return estados.compute(email, (chave, atual) -> {
            if (atual != null && atual.bloqueadoAte() != null && agora.isBefore(atual.bloqueadoAte())) {
                long segundos = (Duration.between(agora, atual.bloqueadoAte()).toMillis() + 999) / 1000;
                throw ErroDaApi.muitasTentativas(Duration.ofSeconds(segundos));
            }
            boolean recomeca = atual == null || atual.bloqueadoAte() != null || velha(atual, agora);
            int falhas = recomeca ? 1 : atual.falhas() + 1;
            return new Estado(falhas, agora, falhas >= falhasParaBloquear ? agora.plus(bloqueio) : null);
        }).falhas();
    }

    public void devolver(String email) {
        estados.computeIfPresent(email, (chave, atual) -> {
            int falhas = atual.falhas() - 1;
            if (falhas <= 0) {
                return null;
            }
            return new Estado(falhas, atual.ultimaFalha(), falhas >= falhasParaBloquear ? atual.bloqueadoAte() : null);
        });
    }

    public void limpar(String email) {
        estados.remove(email);
    }

    public boolean bloqueou(int falhas) {
        return falhas == falhasParaBloquear;
    }

    public Duration bloqueio() {
        return bloqueio;
    }

    private boolean velha(Estado estado, Instant agora) {
        boolean bloqueioAcabou = estado.bloqueadoAte() == null || !agora.isBefore(estado.bloqueadoAte());
        return bloqueioAcabou && estado.ultimaFalha().plus(bloqueio).isBefore(agora);
    }
}
