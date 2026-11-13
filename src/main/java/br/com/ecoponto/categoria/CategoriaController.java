package br.com.ecoponto.categoria;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CategoriaController {

    private final CategoriaRepositorio categorias;

    public CategoriaController(CategoriaRepositorio categorias) {
        this.categorias = categorias;
    }

    @GetMapping("/api/categorias")
    public List<CategoriaSaida> listar() {
        return categorias.findAllByOrderByIdCategoriaAsc().stream().map(CategoriaSaida::de).toList();
    }
}
