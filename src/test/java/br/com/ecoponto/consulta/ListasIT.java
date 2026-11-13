package br.com.ecoponto.consulta;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ecoponto.contrato.ClienteDaApi;
import br.com.ecoponto.contrato.ClienteDaApi.Resposta;
import br.com.ecoponto.contrato.Exemplos;
import br.com.ecoponto.contrato.Exemplos.Caso;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ListasIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    private final Map<Integer, String> nomesOriginais = new LinkedHashMap<>();

    @Test
    void osVinteBairrosDoScriptEmOrdemAlfabetica() throws Exception {
        Caso caso = Exemplos.caso("bairros");

        Resposta resposta = new ClienteDaApi(porta).get("/api/bairros");

        assertThat(resposta.status()).isEqualTo(200);
        JSONAssert.assertEquals(caso.corpo().toString(), resposta.corpo(), JSONCompareMode.STRICT);
        List<String> nomes = new ArrayList<>();
        JSON.readTree(resposta.corpo()).forEach(bairro -> nomes.add(bairro.path("nome").asText()));
        Collator portugues = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
        assertThat(nomes).hasSize(20).isSortedAccordingTo(portugues);
    }

    @Test
    void aOrdemEPeloNomeIgnorandoAcentosEMaiusculas() throws Exception {
        renomear(20, "Água Limpa");
        renomear(19, "brasil novo");

        Resposta resposta = new ClienteDaApi(porta).get("/api/bairros");

        assertThat(resposta.status()).isEqualTo(200);
        List<Integer> ids = new ArrayList<>();
        JSON.readTree(resposta.corpo()).forEach(bairro -> ids.add(bairro.path("idBairro").asInt()));
        assertThat(ids).startsWith(20, 1, 2, 3, 19, 4).hasSize(20);
    }

    @AfterEach
    void devolverOsNomesDoScript() {
        nomesOriginais.forEach((id, nome) -> jdbc.update("UPDATE bairro SET nome = ? WHERE id_bairro = ?", nome, id));
        nomesOriginais.clear();
    }

    private void renomear(int idBairro, String nome) {
        nomesOriginais.putIfAbsent(idBairro,
                jdbc.queryForObject("SELECT nome FROM bairro WHERE id_bairro = ?", String.class, idBairro));
        jdbc.update("UPDATE bairro SET nome = ? WHERE id_bairro = ?", nome, idBairro);
    }

    @Test
    void asDezCategoriasDoScriptComDescricaoNaOrdemDeCadastro() throws Exception {
        Caso caso = Exemplos.caso("categorias");

        Resposta resposta = new ClienteDaApi(porta).get("/api/categorias");

        assertThat(resposta.status()).isEqualTo(200);
        JSONAssert.assertEquals(caso.corpo().toString(), resposta.corpo(), JSONCompareMode.STRICT);
        JsonNode corpo = JSON.readTree(resposta.corpo());
        assertThat(corpo).hasSize(10);
        corpo.forEach(categoria -> assertThat(categoria.path("descricao").asText()).isNotBlank());
    }
}
