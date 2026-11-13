package br.com.ecoponto.configuracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class LogRequisicoesFilterTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void registraMetodoCaminhoEStatusSemNenhumaSenha(CapturedOutput saida) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/auth/login?senha=ValorSecretoNaConsulta&pagina=2")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"maria.souza@example.com\",\"senha\":\"ValorSecretoNoCorpo\"}"))
                .andReturn();
        int status = resultado.getResponse().getStatus();

        assertThat(saida.getOut()).contains("POST /api/auth/login?senha=***&pagina=2 " + status + " ");
        assertThat(saida.getAll())
                .doesNotContain("ValorSecretoNaConsulta")
                .doesNotContain("ValorSecretoNoCorpo");
    }

    @Test
    void mascaraSoOsParametrosComNomeDeSenhaOuDeSegredo() {
        assertThat(LogRequisicoesFilter.mascarar("senha=a&bairro=4&TOKEN=b&nova_senha=c&categoria&password"))
                .isEqualTo("senha=***&bairro=4&TOKEN=***&nova_senha=***&categoria&password=***");
    }

    @Test
    void registra500QuandoAlgoFalhaDepoisDoFiltro(CapturedOutput saida) {
        MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/api/qualquer");
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        FilterChain cadeiaQueFalha = (req, res) -> {
            throw new ServletException("falha");
        };

        assertThatThrownBy(() -> new LogRequisicoesFilter().doFilter(requisicao, resposta, cadeiaQueFalha))
                .isInstanceOf(ServletException.class);
        assertThat(saida.getOut()).contains("GET /api/qualquer 500 ");
    }
}
