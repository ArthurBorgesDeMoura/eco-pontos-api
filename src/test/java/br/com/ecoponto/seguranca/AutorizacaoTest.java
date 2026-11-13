package br.com.ecoponto.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import br.com.ecoponto.bairro.BairroRepositorio;
import br.com.ecoponto.categoria.CategoriaRepositorio;
import br.com.ecoponto.categoria.PainelDeCategorias;
import br.com.ecoponto.contrato.ClienteDaApi;
import br.com.ecoponto.contrato.ClienteDaApi.Resposta;
import br.com.ecoponto.contrato.Exemplos;
import br.com.ecoponto.contrato.Exemplos.Caso;
import br.com.ecoponto.contrato.Exemplos.Pedido;
import br.com.ecoponto.contrato.RotasDoContrato;
import br.com.ecoponto.contrato.RotasDoContrato.Exige;
import br.com.ecoponto.contrato.RotasDoContrato.Rota;
import br.com.ecoponto.conta.UsuarioRepositorio;
import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import br.com.ecoponto.lista.Pagina;
import br.com.ecoponto.ponto.BuscaDePontos;
import br.com.ecoponto.ponto.ContagemDePontos;
import br.com.ecoponto.ponto.PainelDePontos;
import br.com.ecoponto.ponto.ResumoDoPainel;
import br.com.ecoponto.ponto.SugestaoDePontos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(AutorizacaoTest.RotaNovaDeAdmin.class)
class AutorizacaoTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ROTA_NOVA = "/api/admin/rota-nova-de-teste";
    private static final String RESPOSTA_DA_ROTA_NOVA = "{\"rota\":\"nova\"}";
    private static final Set<String> CODIGOS_DE_ACESSO = Set.of("NAO_AUTENTICADO", "SEM_PERMISSAO");

    enum Quem {
        VISITANTE(null), USUARIO(ContasDoScript.MARIA), ADMIN(ContasDoScript.ADMIN);

        private final String email;

        Quem(String email) {
            this.email = email;
        }
    }

    @LocalServerPort
    private int porta;

    @Autowired
    private PasswordEncoder codificador;

    @MockBean
    private UsuarioRepositorio usuarios;

    @MockBean
    private BairroRepositorio bairros;

    @MockBean
    private CategoriaRepositorio categorias;

    @MockBean
    private BuscaDePontos busca;

    @MockBean
    private SugestaoDePontos sugestoes;

    @MockBean
    private PainelDePontos painel;

    @MockBean
    private PainelDeCategorias painelDeCategorias;

    @BeforeEach
    void contasDoScript() {
        ContasDoScript.simular(usuarios, codificador);
        when(busca.buscar(any(), any(), anyInt(), anyInt())).thenReturn(new Pagina<>(List.of(), 0, 1, 7));
        when(busca.contar(any())).thenReturn(new ContagemDePontos(0, List.of()));
        when(busca.detalhar(anyInt(), anyBoolean())).thenThrow(new ErroDaApi(CodigoDeErro.NAO_ENCONTRADO));
        when(busca.minhasSugestoes(anyInt(), any(), anyInt(), anyInt())).thenReturn(new Pagina<>(List.of(), 0, 1, 7));
        when(busca.pontosDoPainel(any(), anyInt(), anyInt())).thenReturn(new Pagina<>(List.of(), 0, 1, 7));
        when(painel.resumo()).thenReturn(new ResumoDoPainel(0, 0, 0));
        when(painelDeCategorias.listar(anyInt(), anyInt())).thenReturn(new Pagina<>(List.of(), 0, 1, 7));
        when(painel.decidir(anyInt(), any())).thenThrow(new ErroDaApi(CodigoDeErro.NAO_ENCONTRADO));
    }

    @TestFactory
    Stream<DynamicTest> cadaPerfilContraCadaRotaDoContrato() throws IOException {
        List<DynamicTest> testes = new ArrayList<>();
        for (Rota rota : RotasDoContrato.todas("1")) {
            for (Quem quem : Quem.values()) {
                testes.add(DynamicTest.dynamicTest(quem + " " + rota.metodo() + " " + rota.caminho(),
                        () -> conferir(rota, quem)));
            }
        }
        return testes.stream();
    }

    @Test
    void umaRotaNovaEmApiAdminJaExigeAdmin() throws Exception {
        for (String metodo : List.of("GET", "POST")) {
            Rota rota = new Rota(metodo, ROTA_NOVA, Exige.ADMIN);
            conferir(rota, Quem.VISITANTE);
            conferir(rota, Quem.USUARIO);

            Resposta doAdmin = cliente(Quem.ADMIN).enviar(metodo, ROTA_NOVA, metodo.equals("GET") ? null : "{}");
            assertThat(doAdmin.status()).isEqualTo(200);
            JSONAssert.assertEquals(RESPOSTA_DA_ROTA_NOVA, doAdmin.corpo(), JSONCompareMode.STRICT);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sem-login", "sem-permissao"})
    void osCasosDeAcessoSaemComoNoContrato(String nome) throws Exception {
        Caso caso = Exemplos.caso(nome);
        for (Pedido pedido : caso.pedidos()) {
            Quem quem = pedido.sessao() == null ? Quem.VISITANTE : Quem.USUARIO;
            assertThat(pedido.sessao()).isIn(null, ContasDoScript.MARIA);
            String corpo = pedido.corpo() == null ? null : pedido.corpo().toString();

            Resposta resposta = cliente(quem).enviar(pedido.metodo(), pedido.caminho(), corpo);

            assertThat(resposta.status()).as("%s %s", pedido.metodo(), pedido.caminho()).isEqualTo(caso.status());
            JSONAssert.assertEquals(caso.corpo().toString(), resposta.corpo(), JSONCompareMode.STRICT);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/admin", "/api/admin/", ROTA_NOVA + "/", ROTA_NOVA + "?perfil=ADMIN"})
    void oPrefixoDeAdminDa401E403EmQualquerForma(String caminho) throws Exception {
        conferir(new Rota("GET", caminho, Exige.ADMIN), Quem.VISITANTE);
        conferir(new Rota("GET", caminho, Exige.ADMIN), Quem.USUARIO);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/admin//rota-nova-de-teste",
        "/api//admin/rota-nova-de-teste",
        "/api/admin/./rota-nova-de-teste",
        "/api/outra/../admin/rota-nova-de-teste",
        "/api/admin/%2e/rota-nova-de-teste",
        "/api/admin%2Frota-nova-de-teste",
        "/api/admin;perfil=ADMIN/rota-nova-de-teste",
        "/API/admin/rota-nova-de-teste",
        "/api/ADMIN/rota-nova-de-teste"
    })
    void variacaoDoCaminhoNaoChegaARotaDeAdmin(String caminho) {
        Resposta resposta = cliente(Quem.USUARIO).get(caminho);

        assertThat(resposta.status()).as(caminho).isIn(400, 404);
        assertThat(resposta.corpo()).as(caminho).doesNotContain(RESPOSTA_DA_ROTA_NOVA);
    }

    @Test
    void perfilEnviadoPeloClienteNaoVale() {
        Resposta usuarioQueSeDizAdmin = cliente(Quem.USUARIO)
                .comCabecalho("X-Perfil", "ADMIN")
                .enviar("POST", ROTA_NOVA, "{\"perfil\":\"ADMIN\",\"papel\":\"ROLE_ADMIN\"}");
        assertThat(usuarioQueSeDizAdmin.status()).isEqualTo(403);

        String basic = ContasDoScript.ADMIN + ":" + ContasDoScript.senha(ContasDoScript.ADMIN);
        Resposta visitanteComBasic = cliente(Quem.VISITANTE)
                .comCabecalho("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(basic.getBytes(StandardCharsets.UTF_8)))
                .get(ROTA_NOVA);
        assertThat(visitanteComBasic.status()).isEqualTo(401);
    }

    @Test
    void depoisDoLogoutORotaDeAdminVoltaA401() {
        ClienteDaApi admin = cliente(Quem.ADMIN);
        assertThat(admin.get(ROTA_NOVA).status()).isEqualTo(200);

        admin.post("/api/auth/logout", null);

        assertThat(admin.get(ROTA_NOVA).status()).isEqualTo(401);
    }

    private void conferir(Rota rota, Quem quem) throws Exception {
        String corpo = rota.metodo().equals("POST") || rota.metodo().equals("PUT") ? "{}" : null;
        Resposta resposta = cliente(quem).enviar(rota.metodo(), rota.caminho(), corpo);
        String descricao = quem + " " + rota.metodo() + " " + rota.caminho();

        if (quem == Quem.VISITANTE && rota.exige() != Exige.NADA) {
            assertThat(resposta.status()).as(descricao).isEqualTo(401);
            JSONAssert.assertEquals(Exemplos.caso("sem-login").corpo().toString(), resposta.corpo(),
                    JSONCompareMode.STRICT);
        } else if (quem == Quem.USUARIO && rota.exige() == Exige.ADMIN) {
            assertThat(resposta.status()).as(descricao).isEqualTo(403);
            JSONAssert.assertEquals(Exemplos.caso("sem-permissao").corpo().toString(), resposta.corpo(),
                    JSONCompareMode.STRICT);
        } else {
            String explicacao = descricao + ": " + resposta.status() + " " + resposta.corpo();
            assertThat(codigo(resposta)).as(explicacao).isNotIn(CODIGOS_DE_ACESSO);
            if (rota.caminho().equals("/api/auth/login")) {
                assertThat(codigo(resposta)).as(explicacao).isEqualTo("CREDENCIAIS_INVALIDAS");
            } else {
                assertThat(resposta.status()).as(explicacao).isNotIn(401, 403);
            }
        }
    }

    private ClienteDaApi cliente(Quem quem) {
        ClienteDaApi cliente = new ClienteDaApi(porta);
        if (quem.email != null) {
            Resposta login = cliente.post("/api/auth/login", ContasDoScript.corpoDoLogin(quem.email));
            assertThat(login.status()).as("login de %s", quem).isEqualTo(200);
        }
        return cliente;
    }

    private static String codigo(Resposta resposta) {
        if (resposta.corpo() == null || resposta.corpo().isBlank()) {
            return "";
        }
        try {
            JsonNode corpo = JSON.readTree(resposta.corpo());
            return corpo.path("codigo").asText("");
        } catch (IOException e) {
            return "";
        }
    }

    @RestController
    static class RotaNovaDeAdmin {

        @GetMapping(ROTA_NOVA)
        Map<String, String> ler() {
            return Map.of("rota", "nova");
        }

        @PostMapping(ROTA_NOVA)
        Map<String, String> gravar() {
            return Map.of("rota", "nova");
        }
    }
}
