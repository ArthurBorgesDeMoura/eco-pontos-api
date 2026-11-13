package br.com.ecoponto.categoria;

import br.com.ecoponto.erro.TratamentoDeErros;
import br.com.ecoponto.lista.Pagina;
import br.com.ecoponto.validacao.Filtros;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PainelDeCategoriasController {

    private final PainelDeCategorias painel;

    public PainelDeCategoriasController(PainelDeCategorias painel) {
        this.painel = painel;
    }

    @GetMapping("/api/admin/categorias")
    public Pagina<CategoriaDoPainel> listar(@RequestParam(required = false) String pagina,
            @RequestParam(required = false) String tamanho) {
        Filtros filtros = new Filtros();
        int numeroDaPagina = filtros.pagina(pagina);
        int tamanhoDaPagina = filtros.tamanho(tamanho);
        filtros.conferir(TratamentoDeErros.CONFIRA_OS_FILTROS);
        return painel.listar(numeroDaPagina, tamanhoDaPagina);
    }

    @PostMapping("/api/admin/categorias")
    public ResponseEntity<CategoriaDoPainel> criar(@RequestBody CategoriaEntrada entrada) {
        return ResponseEntity.status(HttpStatus.CREATED).body(painel.criar(entrada));
    }

    @PutMapping("/api/admin/categorias/{idCategoria}")
    public CategoriaDoPainel editar(@PathVariable String idCategoria, @RequestBody CategoriaEntrada entrada) {
        return painel.editar(Filtros.idDoCaminho(idCategoria, Filtros.ID_DE_CATEGORIA), entrada);
    }
}
