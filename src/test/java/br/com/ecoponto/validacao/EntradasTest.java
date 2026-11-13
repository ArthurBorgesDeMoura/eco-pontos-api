package br.com.ecoponto.validacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ecoponto.categoria.CategoriaEntrada;
import br.com.ecoponto.conta.CadastroEntrada;
import br.com.ecoponto.conta.LoginEntrada;
import br.com.ecoponto.ponto.PontoEntrada;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntradasTest {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void criarValidador() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void fecharValidador() {
        fabrica.close();
    }

    @Test
    void oPontoAceitaAteOTamanhoDasColunasERecusaUmAMais() {
        assertThat(erros(ponto(x(120), x(200), x(100), x(100)))).isEmpty();
        assertThat(erros(ponto(x(121), x(201), x(101), x(101)))).containsOnlyKeys("nome", "endereco", "horario",
                "contato");
    }

    @Test
    void oPontoContaOsTextosSemAsPontasEOpcionalVazioViraNull() {
        PontoEntrada entrada = new PontoEntrada("  " + x(120) + "  ", " Rua A, 1 ", 4, "   ", "", List.of(7));

        assertThat(erros(entrada)).isEmpty();
        assertThat(entrada.nome()).hasSize(120);
        assertThat(entrada.endereco()).isEqualTo("Rua A, 1");
        assertThat(entrada.horario()).isNull();
        assertThat(entrada.contato()).isNull();
    }

    @Test
    void oPontoExigeNomeEnderecoBairroEMateriaisSemRepetir() {
        assertThat(erros(new PontoEntrada("  ", null, null, null, null, null))).containsOnly(
                Map.entry("nome", "Informe o nome do ponto."),
                Map.entry("endereco", "Informe o endereço."),
                Map.entry("idBairro", "Escolha o bairro."),
                Map.entry("categorias", "Escolha pelo menos um material."));
        assertThat(erros(new PontoEntrada("Ponto", "Rua A, 1", 4, null, null, List.of(7, 7))))
                .containsOnly(Map.entry("categorias", "Escolha só materiais da lista, sem repetir."));
        assertThat(erros(new PontoEntrada("Ponto", "Rua A, 1", 4, null, null, Arrays.asList(7, null))))
                .containsOnly(Map.entry("categorias", "Escolha só materiais da lista, sem repetir."));
    }

    @Test
    void oCadastroAceitaEmailDe150ERecusaDe151() {
        String email150 = x(138) + "@exemplo.com";
        assertThat(email150).hasSize(150);

        assertThat(erros(new CadastroEntrada("Ana", email150, "12345678"))).isEmpty();
        assertThat(erros(new CadastroEntrada("Ana", "a" + email150, "12345678")))
                .containsOnly(Map.entry("email", "Use até 150 caracteres."));
    }

    @Test
    void oCadastroDaUmaMensagemPorCampoNaOrdemFaltaTamanhoFormato() {
        assertThat(erros(new CadastroEntrada(x(101), "", " "))).containsOnly(
                Map.entry("nome", "Use até 100 caracteres."),
                Map.entry("email", "Informe seu e-mail."),
                Map.entry("senha", "Informe sua senha."));
        assertThat(erros(new CadastroEntrada("Ana", "ana@exemplo", "1234567"))).containsOnly(
                Map.entry("email", "Digite um e-mail válido, como nome@exemplo.com."),
                Map.entry("senha", "A senha precisa ter pelo menos 8 caracteres."));
    }

    @Test
    void aSenhaVaiAte72BytesComoOBcrypt() {
        assertThat(erros(new CadastroEntrada("Ana", "ana@exemplo.com", x(72)))).isEmpty();
        assertThat(erros(new CadastroEntrada("Ana", "ana@exemplo.com", "ç".repeat(36)))).isEmpty();
        assertThat(erros(new CadastroEntrada("Ana", "ana@exemplo.com", x(73)))).containsKey("senha");
        assertThat(erros(new CadastroEntrada("Ana", "ana@exemplo.com", "ç".repeat(37)))).containsKey("senha");
    }

    @Test
    void oEmailVaiSemAsPontasEmMinusculasEASenhaNaoMuda() {
        CadastroEntrada cadastro = new CadastroEntrada(" Ana Lima ", " Ana.Lima@Example.COM ", " senha com espaço ");
        LoginEntrada login = new LoginEntrada(" Maria.Souza@Example.com", null);

        assertThat(cadastro.nome()).isEqualTo("Ana Lima");
        assertThat(cadastro.email()).isEqualTo("ana.lima@example.com");
        assertThat(cadastro.senha()).isEqualTo(" senha com espaço ");
        assertThat(login.email()).isEqualTo("maria.souza@example.com");
        assertThat(login.senha()).isEmpty();
    }

    @Test
    void oToStringNuncaMostraASenha() {
        assertThat(new CadastroEntrada("Ana", "ana@exemplo.com", "SenhaSecreta1")).asString()
                .doesNotContain("SenhaSecreta1");
        assertThat(new LoginEntrada("ana@exemplo.com", "SenhaSecreta1")).asString().doesNotContain("SenhaSecreta1");
    }

    @Test
    void aCategoriaAceitaAte60E255() {
        assertThat(erros(new CategoriaEntrada(x(60), x(255)))).isEmpty();
        assertThat(erros(new CategoriaEntrada(x(61), x(256)))).containsOnly(
                Map.entry("nome", "Use até 60 caracteres."),
                Map.entry("descricao", "Use até 255 caracteres."));
        assertThat(new CategoriaEntrada(" Vidro ", "  ").descricao()).isNull();
    }

    private static PontoEntrada ponto(String nome, String endereco, String horario, String contato) {
        return new PontoEntrada(nome, endereco, 4, horario, contato, List.of(7));
    }

    @Test
    void espacoNaoSeparavelContaComoEspaco() {
        String soEspacos = "   ";

        assertThat(erros(new PontoEntrada(soEspacos, "Rua A, 1", 4, null, null, List.of(7))))
                .containsOnly(Map.entry("nome", "Informe o nome do ponto."));
        assertThat(erros(new CadastroEntrada("Ana", "ana@exemplo.com", soEspacos.repeat(4))))
                .containsOnly(Map.entry("senha", "Informe sua senha."));
        assertThat(new CategoriaEntrada(" Vidro ", null).nome()).isEqualTo("Vidro");
        assertThat(new CadastroEntrada("Ana", "Maria.Souza@Example.com ", "12345678").email())
                .isEqualTo("maria.souza@example.com");
        assertThat(new LoginEntrada(" maria.souza@example.com", "12345678").email())
                .isEqualTo("maria.souza@example.com");
        assertThat(erros(new CadastroEntrada("Ana", "maria souza@example.com", "12345678")))
                .containsOnly(Map.entry("email", "Digite um e-mail válido, como nome@exemplo.com."));
    }

    @Test
    void oPontoAceitaAte100Materiais() {
        List<Integer> cem = IntStream.rangeClosed(1, 100).boxed().toList();
        List<Integer> centoEUm = IntStream.rangeClosed(1, 101).boxed().toList();

        assertThat(erros(new PontoEntrada("Ponto", "Rua A, 1", 4, null, null, cem))).isEmpty();
        assertThat(erros(new PontoEntrada("Ponto", "Rua A, 1", 4, null, null, centoEUm)))
                .containsOnly(Map.entry("categorias", "Escolha só materiais da lista, sem repetir."));
    }

    private static Map<String, String> erros(Object entrada) {
        return new ValidadorDeEntradas(validador).erros(entrada);
    }

    private static String x(int tamanho) {
        return "x".repeat(tamanho);
    }
}
