package br.com.ecoponto.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ecoponto.contrato.ClienteDaApi;
import br.com.ecoponto.contrato.ClienteDaApi.Resposta;
import br.com.ecoponto.contrato.Exemplos;
import br.com.ecoponto.contrato.Exemplos.Caso;
import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class CadastroIT {

    private static final String PREFIXO = "it-cadastro-";
    private static final String SENHA = "senha de teste longa";
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder codificador;

    @Autowired
    private CadastroService cadastro;

    @Value("${ecoponto.senha.custo-bcrypt}")
    private int custo;

    @AfterEach
    void apagarAsContasDoTeste() {
        jdbc.update("DELETE FROM usuario WHERE email LIKE ?", PREFIXO + "%");
    }

    @Test
    void cadastroValidoCriaAContaUsuarioComHashBcryptEDataDeCadastro(CapturedOutput saida) throws Exception {
        String email = PREFIXO + System.nanoTime() + "@example.com";
        Resposta resposta = cadastrar(Map.of(
                "nome", "  Teste de Cadastro ", "email", "  " + email.toUpperCase() + " ", "senha", SENHA,
                "perfil", "ADMIN"));

        assertThat(resposta.status()).isEqualTo(201);
        JsonNode corpo = JSON.readTree(resposta.corpo());
        assertThat(corpo.path("idUsuario").asInt()).isGreaterThan(3);
        assertThat(corpo.path("nome").asText()).isEqualTo("Teste de Cadastro");
        assertThat(corpo.path("email").asText()).isEqualTo(email);
        assertThat(corpo.path("perfil").asText()).isEqualTo("USUARIO");
        assertThat(corpo.has("senhaHash") || corpo.has("senha")).isFalse();

        Map<String, Object> linha = jdbc.queryForMap(
                "SELECT perfil, senha_hash, data_cadastro FROM usuario WHERE email = ?", email);
        String hash = String.valueOf(linha.get("senha_hash"));
        assertThat(linha.get("perfil")).isEqualTo("USUARIO");
        assertThat(hash.startsWith(String.format("$2a$%02d$", custo)) && hash.length() == 60)
                .as("hash bcrypt gerado pela aplicação").isTrue();
        assertThat(codificador.matches(SENHA, hash)).isTrue();
        assertThat(linha.get("data_cadastro")).isNotNull();
        assertThat(saida.getAll()).doesNotContain(SENHA).doesNotContain(hash);
    }

    @Test
    void emailRepetidoMesmoComMaiusculasDa409() throws Exception {
        conferirComOExemplo(Exemplos.caso("cadastro-email-repetido"));
    }

    @Test
    void dadosInvalidosDao400ComOErroDeCadaCampo() throws Exception {
        conferirComOExemplo(Exemplos.caso("cadastro-dados-invalidos"));
    }

    @Test
    void aChaveUnicaDoBancoTambemViraEmailRepetido() {
        Usuario repetida = Usuario.novoUsuario("Outra Maria", "maria.souza@example.com", codificador.encode(SENHA));

        assertThatThrownBy(() -> cadastro.gravar(repetida))
                .isInstanceOfSatisfying(ErroDaApi.class,
                        erro -> assertThat(erro.codigo()).isEqualTo(CodigoDeErro.EMAIL_JA_CADASTRADO));
    }

    private void conferirComOExemplo(Caso caso) throws Exception {
        String corpo = caso.pedidos().get(0).corpo().toString();
        Resposta resposta = new ClienteDaApi(porta).post("/api/auth/cadastro", corpo);

        assertThat(resposta.status()).isEqualTo(caso.status());
        JSONAssert.assertEquals(caso.corpo().toString(), resposta.corpo(), JSONCompareMode.STRICT);
    }

    private Resposta cadastrar(Map<String, String> corpo) throws Exception {
        return new ClienteDaApi(porta).post("/api/auth/cadastro", JSON.writeValueAsString(corpo));
    }
}
