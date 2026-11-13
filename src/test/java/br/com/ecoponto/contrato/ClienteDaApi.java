package br.com.ecoponto.contrato;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ClienteDaApi {

    private static final Set<String> MUDAM_DADOS = Set.of("POST", "PUT", "DELETE");

    private final String base;
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final HttpClient http;
    private boolean mandarToken = true;
    private final Map<String, String> cabecalhos = new LinkedHashMap<>();

    public record Resposta(int status, String corpo, List<String> setCookies, Optional<String> retryAfter,
            HttpHeaders cabecalhos) {

        public Optional<String> setCookie(String nome) {
            return setCookies.stream().filter(cookie -> cookie.startsWith(nome + "=")).findFirst();
        }

        public Optional<String> cabecalho(String nome) {
            return cabecalhos.firstValue(nome);
        }
    }

    public ClienteDaApi(int porta) {
        this.base = "http://localhost:" + porta;
        this.http = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    public ClienteDaApi semTokenCsrf() {
        this.mandarToken = false;
        return this;
    }

    public ClienteDaApi comCabecalho(String nome, String valor) {
        cabecalhos.put(nome, valor);
        return this;
    }

    public Resposta get(String caminho) {
        return enviar("GET", caminho, null);
    }

    public Resposta post(String caminho, String json) {
        return enviar("POST", caminho, json);
    }

    public Resposta enviar(String metodo, String caminho, String json) {
        return enviar(metodo, caminho, json == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json), json == null ? null : "application/json");
    }

    public Resposta enviarEmPartes(String metodo, String caminho, byte[] corpo) {
        return enviarEmPartes(metodo, caminho, corpo, "application/json");
    }

    public Resposta enviarEmPartes(String metodo, String caminho, byte[] corpo, String tipo) {
        return enviar(metodo, caminho, HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(corpo)),
                tipo);
    }

    public Resposta enviarComTipo(String metodo, String caminho, String corpo, String tipo) {
        return enviar(metodo, caminho, HttpRequest.BodyPublishers.ofString(corpo), tipo);
    }

    private Resposta enviar(String metodo, String caminho, HttpRequest.BodyPublisher corpo, String tipo) {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(base + caminho)).method(metodo, corpo);
        if (tipo != null) {
            pedido.header("Content-Type", tipo);
        }
        cabecalhos.forEach(pedido::header);
        if (mandarToken && MUDAM_DADOS.contains(metodo)) {
            if (cookie("XSRF-TOKEN").isEmpty()) {
                get("/api/auth/eu");
            }
            cookie("XSRF-TOKEN").ifPresent(token -> pedido.header("X-XSRF-TOKEN", token));
        }
        try {
            HttpResponse<String> resposta = http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
            return new Resposta(resposta.statusCode(), resposta.body(),
                    resposta.headers().allValues("set-cookie"), resposta.headers().firstValue("retry-after"),
                    resposta.headers());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public Optional<String> cookie(String nome) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals(nome))
                .map(HttpCookie::getValue)
                .findFirst();
    }
}
