package br.com.ecoponto.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.ecoponto.configuracao.SenhaConfig;
import br.com.ecoponto.contrato.Exemplos;
import br.com.ecoponto.contrato.Exemplos.Caso;
import br.com.ecoponto.seguranca.RespostasDeSeguranca;
import br.com.ecoponto.seguranca.SegurancaConfig;
import br.com.ecoponto.seguranca.Sessoes;
import java.sql.SQLIntegrityConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(ContaController.class)
@Import({CadastroService.class, LoginService.class, TentativasDeLogin.class, Sessoes.class, SenhaConfig.class,
    SegurancaConfig.class, RespostasDeSeguranca.class})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ContaControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder codificador;

    @MockBean
    private UsuarioRepositorio usuarios;

    @Test
    void cadastroValidoCriaUmaContaUsuarioComSenhaEmBcrypt(CapturedOutput saida) throws Exception {
        Caso caso = Exemplos.caso("cadastro-criado");
        when(usuarios.saveAndFlush(any(Usuario.class))).thenAnswer(pedido -> {
            Usuario novo = pedido.getArgument(0);
            ReflectionTestUtils.setField(novo, "idUsuario", 4);
            return novo;
        });

        enviar(caso).andExpect(status().isCreated()).andExpect(content().json(caso.corpo().toString(), true));

        ArgumentCaptor<Usuario> gravado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).existsByEmail("ana.lima@example.com");
        verify(usuarios).saveAndFlush(gravado.capture());
        Usuario conta = gravado.getValue();
        assertThat(conta.getNome()).isEqualTo("Ana Lima");
        assertThat(conta.getEmail()).isEqualTo("ana.lima@example.com");
        assertThat(conta.getPerfil()).as("o perfil ADMIN do corpo não conta").isEqualTo(Perfil.USUARIO);
        assertThat(conta.getSenhaHash().startsWith("$2a$04$")).as("hash bcrypt de custo 4").isTrue();
        assertThat(codificador.matches("uma senha longa", conta.getSenhaHash())).isTrue();
        assertThat(saida.getAll()).doesNotContain("uma senha longa").doesNotContain(conta.getSenhaHash());
    }

    @Test
    void emailRepetidoMesmoComMaiusculasDa409NoCampoEmail() throws Exception {
        Caso caso = Exemplos.caso("cadastro-email-repetido");
        when(usuarios.existsByEmail("maria.souza@example.com")).thenReturn(true);

        enviar(caso).andExpect(status().isConflict()).andExpect(content().json(caso.corpo().toString(), true));
        verify(usuarios, never()).saveAndFlush(any(Usuario.class));
    }

    @Test
    void dadosInvalidosDao400ComOErroDeCadaCampoSemTocarNoBanco() throws Exception {
        Caso caso = Exemplos.caso("cadastro-dados-invalidos");

        enviar(caso).andExpect(status().isBadRequest()).andExpect(content().json(caso.corpo().toString(), true));
        verify(usuarios, never()).existsByEmail(anyString());
    }

    @Test
    void doisCadastrosAoMesmoTempoOSegundoBateNaChaveUnicaEDa409() throws Exception {
        Caso caso = Exemplos.caso("cadastro-email-repetido");
        SQLIntegrityConstraintViolationException doBanco = new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'maria.souza@example.com' for key 'uq_usuario_email'", "23000", 1062);
        when(usuarios.saveAndFlush(any(Usuario.class))).thenThrow(new DataIntegrityViolationException("duplicada",
                new ConstraintViolationException("duplicada", doBanco, "uq_usuario_email")));

        enviar(caso).andExpect(status().isConflict()).andExpect(content().json(caso.corpo().toString(), true));
    }

    @Test
    void outraViolacaoDoBancoNaoViraEmailRepetido() throws Exception {
        when(usuarios.saveAndFlush(any(Usuario.class)))
                .thenThrow(new DataIntegrityViolationException("ck_usuario_perfil"));

        enviar(Exemplos.caso("cadastro-criado"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"));
    }

    private ResultActions enviar(Caso caso) throws Exception {
        return mvc.perform(post(caso.pedidos().get(0).caminho()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(caso.pedidos().get(0).corpo().toString()));
    }
}
