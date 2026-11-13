package br.com.ecoponto.configuracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SemSegredosNoRepositorioTest {

    private static final Path RAIZ = Path.of("..");
    private static final Pattern NOME_DE_SEGREDO =
            Pattern.compile("(senha|segredo|secret|token|password)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SO_VARIAVEL = Pattern.compile("^\\$\\{[A-Z0-9_]+\\}$");

    @Test
    void oEnvExampleTrazOsSegredosVazios() throws IOException {
        Map<String, String> segredos = segredos(lerChaveValor(RAIZ.resolve(".env.example")));

        assertThat(segredos).containsKeys("ECOPONTO_DB_SENHA", "ECOPONTO_DB_ROOT_SENHA");
        assertThat(segredos.values()).allMatch(String::isEmpty);
    }

    @Test
    void oGitignoreDeixaOEnvForaDoRepositorio() throws IOException {
        assertThat(Files.readAllLines(RAIZ.resolve(".gitignore"))).contains(".env", ".env.*", "!.env.example");
    }

    @Test
    void asPropriedadesSoTiramSegredosDasVariaveisDeAmbiente() throws IOException {
        List<Path> arquivos;
        try (Stream<Path> recursos = Files.list(Path.of("src", "main", "resources"))) {
            arquivos = recursos.filter(arquivo -> arquivo.toString().endsWith(".properties")).toList();
        }
        assertThat(arquivos).hasSizeGreaterThanOrEqualTo(3);

        for (Path arquivo : arquivos) {
            segredos(lerChaveValor(arquivo)).forEach((chave, valor) ->
                    assertThat(valor).as("%s em %s", chave, arquivo.getFileName()).matches(SO_VARIAVEL));
        }
    }

    @Test
    void oComposeSoTiraAsSenhasDoEnv() throws IOException {
        List<String> senhas = Files.readAllLines(RAIZ.resolve("compose.yaml")).stream()
                .map(String::trim)
                .filter(linha -> linha.matches("^[A-Z_]*PASSWORD:.*"))
                .toList();

        assertThat(senhas).hasSize(2)
                .allMatch(linha -> linha.matches("^[A-Z_]+: \\$\\{ECOPONTO_[A-Z_]+:\\?[^}]*\\}$"));
    }

    private static Map<String, String> lerChaveValor(Path arquivo) throws IOException {
        Map<String, String> pares = new LinkedHashMap<>();
        for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
            String limpa = linha.trim();
            int igual = limpa.indexOf('=');
            if (!limpa.startsWith("#") && igual > 0) {
                pares.put(limpa.substring(0, igual).trim(), limpa.substring(igual + 1).trim());
            }
        }
        return pares;
    }

    private static Map<String, String> segredos(Map<String, String> pares) {
        Map<String, String> segredos = new LinkedHashMap<>();
        pares.forEach((chave, valor) -> {
            if (NOME_DE_SEGREDO.matcher(chave).find()) {
                segredos.put(chave, valor);
            }
        });
        return segredos;
    }
}
