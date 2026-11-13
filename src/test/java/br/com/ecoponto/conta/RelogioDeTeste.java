package br.com.ecoponto.conta;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class RelogioDeTeste extends Clock {

    private Instant agora = Instant.parse("2026-10-30T12:00:00Z");

    public void avancar(Duration tempo) {
        agora = agora.plus(tempo);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }

    @Override
    public Instant instant() {
        return agora;
    }
}
