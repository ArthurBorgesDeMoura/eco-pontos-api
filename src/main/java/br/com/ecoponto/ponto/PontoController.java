package br.com.ecoponto.ponto;

import br.com.ecoponto.conta.Perfil;
import br.com.ecoponto.lista.Pagina;
import br.com.ecoponto.seguranca.UsuarioDaSessao;
import br.com.ecoponto.validacao.Filtros;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PontoController {

    static final String CONFIRA_OS_FILTROS_DA_BUSCA = "Confira os filtros da busca.";

    private final BuscaDePontos busca;

    public PontoController(BuscaDePontos busca) {
        this.busca = busca;
    }

    @GetMapping("/api/pontos")
    public Pagina<PontoResumo> buscar(@RequestParam(required = false) String bairro,
            @RequestParam(required = false) String categoria, @RequestParam(required = false) String pagina,
            @RequestParam(required = false) String tamanho) {
        Filtros filtros = new Filtros();
        Integer idBairro = filtros.id("bairro", bairro, Filtros.ID_DE_BAIRRO);
        Integer idCategoria = filtros.id("categoria", categoria, Filtros.ID_DE_CATEGORIA);
        int numeroDaPagina = filtros.pagina(pagina);
        int tamanhoDaPagina = filtros.tamanho(tamanho);
        filtros.conferir(CONFIRA_OS_FILTROS_DA_BUSCA);
        return busca.buscar(idBairro, idCategoria, numeroDaPagina, tamanhoDaPagina);
    }

    @GetMapping("/api/pontos/contagem")
    public ContagemDePontos contar(@RequestParam(required = false) String bairro) {
        Filtros filtros = new Filtros();
        Integer idBairro = filtros.id("bairro", bairro, Filtros.ID_DE_BAIRRO);
        filtros.conferir(CONFIRA_OS_FILTROS_DA_BUSCA);
        return busca.contar(idBairro);
    }

    @GetMapping("/api/pontos/{idPonto}")
    public PontoDetalhe detalhar(@PathVariable String idPonto, @AuthenticationPrincipal UsuarioDaSessao usuario) {
        int id = Filtros.idDoCaminho(idPonto, Filtros.ID_DE_PONTO);
        boolean administrador = usuario != null && usuario.perfil() == Perfil.ADMIN;
        return busca.detalhar(id, administrador);
    }
}
