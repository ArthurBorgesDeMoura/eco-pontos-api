package br.com.ecoponto.contrato;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class Exemplos {

    public static final Path PASTA_DA_API = Path.of("..", "docs", "api");
    public static final Path PASTA = PASTA_DA_API.resolve("exemplos");

    private static final ObjectMapper JSON = new ObjectMapper();

    private Exemplos() {
    }

    public record Pedido(String metodo, String caminho, String sessao, JsonNode corpo) {

        public String caminhoSemConsulta() {
            int consulta = caminho.indexOf('?');
            return consulta < 0 ? caminho : caminho.substring(0, consulta);
        }

        public String consulta() {
            int consulta = caminho.indexOf('?');
            return consulta < 0 ? "" : caminho.substring(consulta + 1);
        }
    }

    public record Caso(String arquivo, String descricao, boolean naSimulacao, List<Pedido> pedidos, int status,
            JsonNode corpo) {

        @Override
        public String toString() {
            return arquivo;
        }
    }

    public static List<Caso> todos() {
        try (Stream<Path> arquivos = Files.list(PASTA)) {
            return arquivos.filter(arquivo -> arquivo.toString().endsWith(".json"))
                    .sorted()
                    .map(Exemplos::ler)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Caso caso(String nome) {
        return ler(PASTA.resolve(nome + ".json"));
    }

    private static Caso ler(Path arquivo) {
        try {
            JsonNode raiz = JSON.readTree(arquivo.toFile());
            List<Pedido> pedidos = new ArrayList<>();
            for (JsonNode pedido : raiz.path("pedidos")) {
                JsonNode sessao = pedido.path("sessao");
                pedidos.add(new Pedido(
                        pedido.path("metodo").asText(),
                        pedido.path("caminho").asText(),
                        sessao.isTextual() ? sessao.asText() : null,
                        pedido.has("corpo") ? pedido.get("corpo") : null));
            }
            JsonNode resposta = raiz.path("resposta");
            JsonNode corpo = resposta.path("corpo");
            return new Caso(
                    arquivo.getFileName().toString(),
                    raiz.path("descricao").asText(),
                    raiz.path("naSimulacao").asBoolean(true),
                    List.copyOf(pedidos),
                    resposta.path("status").asInt(),
                    corpo.isMissingNode() || corpo.isNull() ? null : corpo);
        } catch (IOException e) {
            throw new UncheckedIOException("Não consegui ler " + arquivo, e);
        }
    }
}
