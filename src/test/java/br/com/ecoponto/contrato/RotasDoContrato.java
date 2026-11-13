package br.com.ecoponto.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RotasDoContrato {

    public enum Exige {
        NADA, LOGIN, ADMIN
    }

    public record Rota(String metodo, String caminho, Exige exige) {
    }

    private RotasDoContrato() {
    }

    public static List<Rota> todas(String idDoCaminho) throws IOException {
        String especificacao = Files.readString(Exemplos.PASTA_DA_API.resolve("openapi.yaml"));
        ParseOptions opcoes = new ParseOptions();
        opcoes.setResolve(true);
        Map<String, PathItem> caminhos =
                new OpenAPIV3Parser().readContents(especificacao, null, opcoes).getOpenAPI().getPaths();
        List<Rota> rotas = new ArrayList<>();
        caminhos.forEach((caminho, item) -> item.readOperationsMap().forEach((metodo, operacao) -> {
            if (!caminho.equals("/api/saude")) {
                rotas.add(new Rota(metodo.name(), caminho.replaceAll("\\{[^}]+}", idDoCaminho),
                        exige(caminho, operacao)));
            }
        }));
        assertThat(rotas).as("rotas do contrato").hasSizeGreaterThanOrEqualTo(20);
        return rotas;
    }

    private static Exige exige(String caminho, Operation operacao) {
        boolean comSessao = operacao.getSecurity() != null && !operacao.getSecurity().isEmpty();
        if (caminho.startsWith("/api/admin")) {
            assertThat(comSessao).as("%s marca a sessão no contrato", caminho).isTrue();
            return Exige.ADMIN;
        }
        return comSessao ? Exige.LOGIN : Exige.NADA;
    }
}
