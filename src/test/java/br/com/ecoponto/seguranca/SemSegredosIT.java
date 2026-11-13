package br.com.ecoponto.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ecoponto.contrato.ClienteDaApi;
import br.com.ecoponto.contrato.ClienteDaApi.Resposta;
import br.com.ecoponto.contrato.RotasDoContrato;
import br.com.ecoponto.contrato.RotasDoContrato.Exige;
import br.com.ecoponto.contrato.RotasDoContrato.Rota;
import br.com.ecoponto.preparacao.ContasTesteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class SemSegredosIT {

    private static final String ID_QUE_NAO_EXISTE = "999999999";
    private static final Pattern HASH_BCRYPT = Pattern.compile("\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{53}");
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int porta;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ContasTesteService contasTeste;

    @Autowired
    private Environment ambiente;

    private final List<String> emailsCriados = new ArrayList<>();

    @AfterEach
    void apagarAsContasCriadas() {
        emailsCriados.forEach(email -> jdbc.update("DELETE FROM usuario WHERE email = ?", email));
    }

    @Test
    void nenhumaRespostaNemLogMostraSenhaHashOuSegredo(CapturedOutput saida) throws Exception {
        contasTeste.refazerHashes(ContasDoScript.todas());
        Map<String, String> respostas = new LinkedHashMap<>();

        for (Rota rota : RotasDoContrato.todas(ID_QUE_NAO_EXISTE)) {
            for (String quem : new String[] {null, ContasDoScript.MARIA, ContasDoScript.ADMIN}) {
                String corpo = rota.metodo().equals("POST") || rota.metodo().equals("PUT") ? "{}" : null;
                Resposta resposta = cliente(quem).enviar(rota.metodo(), rota.caminho(), corpo);
                conferirAcesso(rota, quem, resposta);
                guardar(respostas, (quem == null ? "visitante" : quem) + " " + rota.metodo() + " " + rota.caminho(),
                        resposta);
            }
        }

        ClienteDaApi visitante = new ClienteDaApi(porta);
        guardar(respostas, "senha errada", visitante.post("/api/auth/login",
                ContasDoScript.corpo(ContasDoScript.JOAO, "senha-errada-do-teste")));
        String semConta = "bloqueio-" + System.nanoTime() + "@example.com";
        for (int tentativa = 1; tentativa <= 6; tentativa++) {
            guardar(respostas, "sem conta " + tentativa, visitante.post("/api/auth/login",
                    ContasDoScript.corpo(semConta, "qualquer-senha-1")));
        }
        assertThat(visitante.post("/api/auth/login", ContasDoScript.corpo(semConta, "qualquer-senha-1")).status())
                .as("o sexto erro seguido bloqueia").isEqualTo(429);
        guardar(respostas, "login do João", cliente(ContasDoScript.JOAO).get("/api/auth/eu"));

        String senhaNova = "Nova-" + HexFormat.of().formatHex(new SecureRandom().generateSeed(8));
        String emailNovo = "segredos-" + System.nanoTime() + "@example.com";
        emailsCriados.add(emailNovo);
        String cadastro = JSON.createObjectNode().put("nome", "Conta do teste de segredos").put("email", emailNovo)
                .put("senha", senhaNova).toString();
        Resposta criada = visitante.post("/api/auth/cadastro", cadastro);
        assertThat(criada.status()).isEqualTo(201);
        guardar(respostas, "cadastro", criada);
        ClienteDaApi nova = new ClienteDaApi(porta);
        Resposta login = nova.post("/api/auth/login", ContasDoScript.corpo(emailNovo, senhaNova));
        assertThat(login.status()).isEqualTo(200);
        guardar(respostas, "login da conta nova", login);
        guardar(respostas, "eu da conta nova", nova.get("/api/auth/eu"));

        Map<String, String> segredos = new LinkedHashMap<>();
        for (String email : List.of(ContasDoScript.ADMIN, ContasDoScript.MARIA, ContasDoScript.JOAO)) {
            segredos.put("a senha de " + email, ContasDoScript.senha(email));
        }
        segredos.put("a senha da conta nova", senhaNova);
        List<String> hashes = jdbc.queryForList("SELECT senha_hash FROM usuario", String.class);
        for (int i = 0; i < hashes.size(); i++) {
            segredos.put("o hash gravado nº " + (i + 1), hashes.get(i));
        }
        String senhaDoBanco = ambiente.getProperty("spring.datasource.password");
        assertThat(senhaDoBanco != null && !senhaDoBanco.isBlank()).as("a senha do banco está no ambiente").isTrue();
        segredos.put("a senha do banco", senhaDoBanco);

        String log = saida.getAll();
        segredos.forEach((qual, valor) -> {
            respostas.forEach((pedido, texto) -> assertThat(texto.contains(valor))
                    .as("%s apareceu na resposta de %s", qual, pedido).isFalse());
            assertThat(log.contains(valor)).as("%s apareceu no log", qual).isFalse();
        });
        respostas.forEach((pedido, texto) -> {
            assertThat(HASH_BCRYPT.matcher(texto).find()).as("um hash bcrypt na resposta de %s", pedido).isFalse();
            assertThat(texto.toLowerCase()).as("a resposta de %s", pedido).doesNotContain("senhahash", "senha_hash");
        });
        assertThat(HASH_BCRYPT.matcher(log).find()).as("um hash bcrypt no log").isFalse();
        assertThat(log.toLowerCase()).as("o log").doesNotContain("senha_hash", "senhahash");
    }

    @Test
    void oCookieDaSessaoEHttpOnly() {
        Resposta login = new ClienteDaApi(porta).post("/api/auth/login",
                ContasDoScript.corpoDoLogin(ContasDoScript.MARIA));

        assertThat(login.setCookie("JSESSIONID")).hasValueSatisfying(cookie -> assertThat(cookie)
                .containsIgnoringCase("HttpOnly").containsIgnoringCase("SameSite=Lax"));
        assertThat(login.setCookie("XSRF-TOKEN")).hasValueSatisfying(cookie -> assertThat(cookie)
                .doesNotContainIgnoringCase("HttpOnly"));
    }

    private static void conferirAcesso(Rota rota, String quem, Resposta resposta) {
        String descricao = (quem == null ? "visitante" : quem) + " " + rota.metodo() + " " + rota.caminho() + ": "
                + resposta.status();
        if (quem == null && rota.exige() != Exige.NADA) {
            assertThat(resposta.status()).as(descricao).isEqualTo(401);
        } else if (ContasDoScript.MARIA.equals(quem) && rota.exige() == Exige.ADMIN) {
            assertThat(resposta.status()).as(descricao).isEqualTo(403);
        } else if (rota.caminho().equals("/api/auth/login")) {
            assertThat(resposta.status()).as(descricao).isEqualTo(401);
        } else {
            assertThat(resposta.status()).as(descricao).isNotIn(401, 403, 500);
        }
    }

    private static void guardar(Map<String, String> respostas, String pedido, Resposta resposta) {
        respostas.put(pedido, resposta.corpo() + "\n" + String.join("\n", resposta.setCookies()));
    }

    private ClienteDaApi cliente(String email) {
        ClienteDaApi cliente = new ClienteDaApi(porta);
        if (email != null) {
            Resposta login = cliente.post("/api/auth/login", ContasDoScript.corpoDoLogin(email));
            assertThat(login.status()).as("login de %s", email).isEqualTo(200);
        }
        return cliente;
    }
}
