package br.com.ecoponto.ponto;

import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import br.com.ecoponto.erro.TratamentoDeErros;
import br.com.ecoponto.lista.Pagina;
import br.com.ecoponto.seguranca.UsuarioDaSessao;
import br.com.ecoponto.validacao.Filtros;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SugestaoController {

    private final SugestaoDePontos sugestoes;
    private final BuscaDePontos busca;

    public SugestaoController(SugestaoDePontos sugestoes, BuscaDePontos busca) {
        this.sugestoes = sugestoes;
        this.busca = busca;
    }

    @PostMapping("/api/pontos")
    public ResponseEntity<PontoCriado> sugerir(@RequestBody PontoEntrada entrada,
            @AuthenticationPrincipal UsuarioDaSessao usuario) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sugestoes.sugerir(entrada, logado(usuario).idUsuario()));
    }

    @GetMapping("/api/minhas-sugestoes")
    public Pagina<Sugestao> minhasSugestoes(@RequestParam(required = false) String status,
            @RequestParam(required = false) String pagina, @RequestParam(required = false) String tamanho,
            @AuthenticationPrincipal UsuarioDaSessao usuario) {
        UsuarioDaSessao autor = logado(usuario);
        Filtros filtros = new Filtros();
        String doStatus = filtros.opcao("status", status, Filtros.STATUS_DO_PONTO, null, Filtros.STATUS);
        int numeroDaPagina = filtros.pagina(pagina);
        int tamanhoDaPagina = filtros.tamanho(tamanho);
        filtros.conferir(TratamentoDeErros.CONFIRA_OS_FILTROS);
        return busca.minhasSugestoes(autor.idUsuario(), doStatus == null ? null : StatusDoPonto.valueOf(doStatus),
                numeroDaPagina, tamanhoDaPagina);
    }

    private static UsuarioDaSessao logado(UsuarioDaSessao usuario) {
        if (usuario == null) {
            throw new ErroDaApi(CodigoDeErro.NAO_AUTENTICADO);
        }
        return usuario;
    }
}
