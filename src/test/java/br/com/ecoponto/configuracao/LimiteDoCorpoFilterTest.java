package br.com.ecoponto.configuracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LimiteDoCorpoFilterTest {

    private final LimiteDoCorpoFilter filtro = new LimiteDoCorpoFilter(new ObjectMapper());

    @Test
    void corpoDeclaradoAcimaDoLimiteDa400SemChegarARota() throws Exception {
        MockHttpServletRequest pedido = new MockHttpServletRequest("POST", "/api/auth/cadastro");
        pedido.setContent(new byte[LimiteDoCorpoFilter.LIMITE_EM_BYTES + 1]);
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        MockFilterChain cadeia = new MockFilterChain();

        filtro.doFilter(pedido, resposta, cadeia);

        assertThat(resposta.getStatus()).isEqualTo(400);
        assertThat(resposta.getContentType()).startsWith("application/json");
        JSONAssert.assertEquals("{\"codigo\":\"DADOS_INVALIDOS\",\"mensagem\":\"O corpo do pedido passa de 64 KB.\"}",
                resposta.getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);
        assertThat(cadeia.getRequest()).as("a rota não foi chamada").isNull();
    }

    @Test
    void corpoNoLimitePassaInteiro() throws Exception {
        MockHttpServletRequest pedido = new MockHttpServletRequest("POST", "/api/pontos");
        pedido.setContent(new byte[LimiteDoCorpoFilter.LIMITE_EM_BYTES]);
        MockFilterChain cadeia = new MockFilterChain();

        filtro.doFilter(pedido, new MockHttpServletResponse(), cadeia);

        try (InputStream corpo = ((HttpServletRequest) cadeia.getRequest()).getInputStream()) {
            assertThat(corpo.readAllBytes()).hasSize(LimiteDoCorpoFilter.LIMITE_EM_BYTES);
        }
    }

    @Test
    void corpoSemTamanhoDeclaradoFalhaAoPassarDoLimite() throws Exception {
        for (boolean byteAByte : new boolean[] {false, true}) {
            MockFilterChain cadeia = new MockFilterChain();
            filtro.doFilter(semTamanho(LimiteDoCorpoFilter.LIMITE_EM_BYTES + 1), new MockHttpServletResponse(), cadeia);

            InputStream corpo = ((HttpServletRequest) cadeia.getRequest()).getInputStream();
            assertThatThrownBy(() -> {
                if (byteAByte) {
                    while (corpo.read() >= 0) {
                        continue;
                    }
                } else {
                    corpo.readAllBytes();
                }
            }).as("byte a byte: %s", byteAByte).isInstanceOf(LimiteDoCorpoFilter.CorpoGrandeDemais.class)
                    .hasMessage("O corpo do pedido passa de 64 KB.");
        }
    }

    @Test
    void oLeitorDeTextoTambemConta() throws Exception {
        MockFilterChain cadeia = new MockFilterChain();
        filtro.doFilter(semTamanho(LimiteDoCorpoFilter.LIMITE_EM_BYTES + 10), new MockHttpServletResponse(), cadeia);

        BufferedReader leitor = ((HttpServletRequest) cadeia.getRequest()).getReader();
        assertThatThrownBy(() -> {
            char[] bloco = new char[4096];
            while (leitor.read(bloco) >= 0) {
                continue;
            }
        }).isInstanceOf(LimiteDoCorpoFilter.CorpoGrandeDemais.class);
    }

    @Test
    void aLeituraForaDoMvcQuePassaDoLimiteTambemDa400() throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        MockFilterChain quemLeOCorpo = new MockFilterChain(new HttpServlet() {
            private static final long serialVersionUID = 1L;
        }, (pedido, saida, resto) -> pedido.getInputStream().readAllBytes());

        filtro.doFilter(semTamanho(LimiteDoCorpoFilter.LIMITE_EM_BYTES + 1), resposta, quemLeOCorpo);

        assertThat(resposta.getStatus()).isEqualTo(400);
        assertThat(resposta.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        JSONAssert.assertEquals("{\"codigo\":\"DADOS_INVALIDOS\",\"mensagem\":\"O corpo do pedido passa de 64 KB.\"}",
                resposta.getContentAsString(StandardCharsets.UTF_8), JSONCompareMode.STRICT);
    }

    @Test
    void outraFalhaDeLeituraSobeComoVeio() throws Exception {
        IOException outra = new IOException("a conexão caiu");
        MockFilterChain quemFalha = new MockFilterChain(new HttpServlet() {
            private static final long serialVersionUID = 1L;
        }, (pedido, saida, resto) -> {
            throw outra;
        });

        assertThatThrownBy(() -> filtro.doFilter(semTamanho(10), new MockHttpServletResponse(), quemFalha))
                .isSameAs(outra);
    }

    private static MockHttpServletRequest semTamanho(int bytes) {
        MockHttpServletRequest pedido = new MockHttpServletRequest("POST", "/api/pontos") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        byte[] corpo = new byte[bytes];
        Arrays.fill(corpo, (byte) 'x');
        pedido.setContent(corpo);
        pedido.setCharacterEncoding("UTF-8");
        return pedido;
    }
}
