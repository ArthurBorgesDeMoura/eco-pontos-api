package br.com.ecoponto.saude;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.SQLTransientConnectionException;
import javax.sql.DataSource;
import br.com.ecoponto.seguranca.RespostasDeSeguranca;
import br.com.ecoponto.seguranca.SegurancaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SaudeController.class)
@Import({SegurancaConfig.class, RespostasDeSeguranca.class})
@ActiveProfiles("test")
class SaudeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private DataSource dataSource;

    @Test
    void respondeOkQuandoOBancoResponde() throws Exception {
        Connection conexao = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(conexao);
        when(conexao.isValid(anyInt())).thenReturn(true);

        mvc.perform(get("/api/saude"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.banco").value("ok"));
        verify(conexao).close();
    }

    @Test
    void respondeIndisponivelQuandoOBancoNaoConecta() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLTransientConnectionException("sem conexão"));

        mvc.perform(get("/api/saude"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.codigo").value("BANCO_INDISPONIVEL"))
                .andExpect(jsonPath("$.mensagem").isNotEmpty());
    }

    @Test
    void respondeIndisponivelQuandoAConexaoNaoEstaValida() throws Exception {
        Connection conexao = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(conexao);
        when(conexao.isValid(anyInt())).thenReturn(false);

        mvc.perform(get("/api/saude"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("BANCO_INDISPONIVEL"));
        verify(conexao).close();
    }
}
