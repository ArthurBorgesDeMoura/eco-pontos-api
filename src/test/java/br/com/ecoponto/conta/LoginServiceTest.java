package br.com.ecoponto.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(OutputCaptureExtension.class)
class LoginServiceTest {

    private static final String EMAIL = "maria.souza@example.com";
    private static final String SENHA = "uma senha certa";

    private final BCryptPasswordEncoder bcrypt = spy(new BCryptPasswordEncoder(4));
    private final UsuarioRepositorio usuarios = mock(UsuarioRepositorio.class);
    private final RelogioDeTeste relogio = new RelogioDeTeste();
    private final LoginService login =
            new LoginService(usuarios, bcrypt, new TentativasDeLogin(relogio, 5, Duration.ofMinutes(5)));
    private Usuario maria;

    @BeforeEach
    void contaDaMaria() {
        maria = Usuario.novoUsuario("Maria Souza", EMAIL, bcrypt.encode(SENHA));
        ReflectionTestUtils.setField(maria, "idUsuario", 2);
        when(usuarios.findByEmail(EMAIL)).thenReturn(Optional.of(maria));
        clearInvocations(bcrypt);
    }

    @Test
    void senhaCertaDevolveQuemEntrouERegistraSemASenha(CapturedOutput saida) {
        UsuarioSaida usuario = login.entrar(new LoginEntrada(" Maria.Souza@Example.com ", SENHA));

        assertThat(usuario).isEqualTo(new UsuarioSaida(2, "Maria Souza", EMAIL, Perfil.USUARIO));
        assertThat(saida.getOut()).contains("Login da conta 2 (maria.souza@example.com)").doesNotContain(SENHA);
    }

    @Test
    void senhaErradaEEmailQueNaoExisteDaoOMesmo401(CapturedOutput saida) {
        ErroDaApi senhaErrada = falha(new LoginEntrada(EMAIL, "senha errada"));
        ErroDaApi semConta = falha(new LoginEntrada("ninguem@example.com", SENHA));

        assertThat(senhaErrada.codigo()).isEqualTo(CodigoDeErro.CREDENCIAIS_INVALIDAS);
        assertThat(semConta.corpo()).isEqualTo(senhaErrada.corpo());
        assertThat(saida.getOut())
                .contains("Login recusado para conta 2 (maria.souza@example.com) (1 falha(s) seguida(s))")
                .contains("Login recusado para um e-mail sem conta (1 falha(s) seguida(s))")
                .doesNotContain("senha errada").doesNotContain(SENHA);
    }

    @Test
    void oQueFoiDigitadoNoEmailSemContaNaoVaiParaOLog(CapturedOutput saida) {
        falha(new LoginEntrada("MinhaSenhaSecreta!", "x"));

        assertThat(saida.getOut()).doesNotContainIgnoringCase("MinhaSenhaSecreta!");
    }

    @Test
    void oBcryptRodaMesmoQuandoAContaNaoExiste() {
        falha(new LoginEntrada("ninguem@example.com", SENHA));

        verify(bcrypt).matches(eq(SENHA), anyString());
    }

    @Test
    void emailVazioOuLongoDemaisDa401SemIrAoBancoENuncaBloqueia() {
        String longo = "a".repeat(150) + "@example.com";
        for (int i = 0; i < 10; i++) {
            assertThat(falha(new LoginEntrada(null, null)).codigo()).isEqualTo(CodigoDeErro.CREDENCIAIS_INVALIDAS);
            assertThat(falha(new LoginEntrada(longo, SENHA)).codigo()).isEqualTo(CodigoDeErro.CREDENCIAIS_INVALIDAS);
        }

        verify(usuarios, never()).findByEmail(anyString());
        verify(bcrypt, times(20)).matches(anyString(), anyString());
    }

    @Test
    void emailComAcentoNaoEntraNaContaDeOutroMesmoComASenhaCerta() {
        String variacao = "mária.souza@example.com";
        when(usuarios.findByEmail(variacao)).thenReturn(Optional.of(maria));
        for (int i = 0; i < 5; i++) {
            falha(new LoginEntrada(EMAIL, "senha errada"));
        }

        assertThat(falha(new LoginEntrada(variacao, SENHA)).codigo()).isEqualTo(CodigoDeErro.CREDENCIAIS_INVALIDAS);
        assertThat(falha(new LoginEntrada(EMAIL, SENHA)).codigo()).isEqualTo(CodigoDeErro.MUITAS_TENTATIVAS);
    }

    @Test
    void senhaAcimaDe72BytesNaoConfere() {
        String longa = SENHA + "x".repeat(72);

        assertThat(falha(new LoginEntrada(EMAIL, longa)).codigo()).isEqualTo(CodigoDeErro.CREDENCIAIS_INVALIDAS);
    }

    @Test
    void depoisDe5FalhasNemASenhaCertaEntraAteOPrazoAcabar() {
        for (int i = 0; i < 5; i++) {
            falha(new LoginEntrada(EMAIL, "senha errada " + i));
        }

        assertThat(falha(new LoginEntrada(EMAIL, SENHA)).codigo()).isEqualTo(CodigoDeErro.MUITAS_TENTATIVAS);

        relogio.avancar(Duration.ofMinutes(5));
        assertThat(login.entrar(new LoginEntrada(EMAIL, SENHA)).idUsuario()).isEqualTo(2);
    }

    @Test
    void oLoginCertoZeraAsFalhasSeguidas() {
        for (int i = 0; i < 4; i++) {
            falha(new LoginEntrada(EMAIL, "senha errada"));
        }
        login.entrar(new LoginEntrada(EMAIL, SENHA));
        for (int i = 0; i < 4; i++) {
            falha(new LoginEntrada(EMAIL, "senha errada"));
        }

        assertThat(login.entrar(new LoginEntrada(EMAIL, SENHA)).idUsuario()).isEqualTo(2);
    }

    @Test
    void oBancoQueFalhaNaoContaComoTentativa() {
        doThrow(new DataAccessResourceFailureException("banco fora do ar")).when(usuarios).findByEmail(EMAIL);
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> login.entrar(new LoginEntrada(EMAIL, SENHA)))
                    .isInstanceOf(DataAccessResourceFailureException.class);
        }

        doReturn(Optional.of(maria)).when(usuarios).findByEmail(EMAIL);
        assertThat(login.entrar(new LoginEntrada(EMAIL, SENHA)).idUsuario()).isEqualTo(2);
    }

    private ErroDaApi falha(LoginEntrada entrada) {
        Throwable erro = catchThrowable(() -> login.entrar(entrada));
        assertThat(erro).isInstanceOf(ErroDaApi.class);
        return (ErroDaApi) erro;
    }
}
