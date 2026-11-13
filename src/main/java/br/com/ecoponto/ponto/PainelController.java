package br.com.ecoponto.ponto;

import br.com.ecoponto.erro.TratamentoDeErros;
import br.com.ecoponto.lista.Pagina;
import br.com.ecoponto.seguranca.UsuarioDaSessao;
import br.com.ecoponto.validacao.Filtros;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PainelController {

    private static final Logger LOG = LoggerFactory.getLogger(PainelController.class);

    private final BuscaDePontos busca;
    private final PainelDePontos painel;

    public PainelController(BuscaDePontos busca, PainelDePontos painel) {
        this.busca = busca;
        this.painel = painel;
    }

    @GetMapping("/api/admin/resumo")
    public ResumoDoPainel resumo() {
        return painel.resumo();
    }

    @GetMapping("/api/admin/pontos")
    public Pagina<PontoDoPainel> pontos(@RequestParam(required = false) String status,
            @RequestParam(required = false) String pagina, @RequestParam(required = false) String tamanho) {
        Filtros filtros = new Filtros();
        String doStatus = filtros.opcao("status", status, Filtros.STATUS_DO_PONTO, StatusDoPonto.PENDENTE.name(),
                Filtros.STATUS);
        int numeroDaPagina = filtros.pagina(pagina);
        int tamanhoDaPagina = filtros.tamanho(tamanho);
        filtros.conferir(TratamentoDeErros.CONFIRA_OS_FILTROS);
        return busca.pontosDoPainel(StatusDoPonto.valueOf(doStatus), numeroDaPagina, tamanhoDaPagina);
    }

    @PostMapping("/api/admin/pontos/{idPonto}/aprovar")
    public PontoDoPainel aprovar(@PathVariable String idPonto, @AuthenticationPrincipal UsuarioDaSessao admin) {
        return decidir(idPonto, StatusDoPonto.APROVADO, admin);
    }

    @PostMapping("/api/admin/pontos/{idPonto}/recusar")
    public PontoDoPainel recusar(@PathVariable String idPonto, @AuthenticationPrincipal UsuarioDaSessao admin) {
        return decidir(idPonto, StatusDoPonto.RECUSADO, admin);
    }

    @PutMapping("/api/admin/pontos/{idPonto}")
    public PontoDetalhe editar(@PathVariable String idPonto, @RequestBody PontoEntrada entrada,
            @AuthenticationPrincipal UsuarioDaSessao admin) {
        int id = Filtros.idDoCaminho(idPonto, Filtros.ID_DE_PONTO);
        PontoDetalhe editado = painel.editar(id, entrada);
        registrar(id, "editado", admin);
        return editado;
    }

    @DeleteMapping("/api/admin/pontos/{idPonto}")
    public ResponseEntity<Void> excluir(@PathVariable String idPonto, @AuthenticationPrincipal UsuarioDaSessao admin) {
        int id = Filtros.idDoCaminho(idPonto, Filtros.ID_DE_PONTO);
        painel.excluir(id);
        registrar(id, "excluído", admin);
        return ResponseEntity.noContent().build();
    }

    private PontoDoPainel decidir(String idPonto, StatusDoPonto decisao, UsuarioDaSessao admin) {
        int id = Filtros.idDoCaminho(idPonto, Filtros.ID_DE_PONTO);
        PontoDoPainel decidido = painel.decidir(id, decisao);
        registrar(id, decisao == StatusDoPonto.APROVADO ? "aprovado" : "recusado", admin);
        return decidido;
    }

    private static void registrar(int idPonto, String acao, UsuarioDaSessao admin) {
        LOG.info("Ponto {} {} pela conta {}.", idPonto, acao, admin == null ? "desconhecida" : admin.idUsuario());
    }
}
