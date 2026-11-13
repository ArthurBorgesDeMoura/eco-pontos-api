package br.com.ecoponto.bairro;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BairroController {

    private final BairroRepositorio bairros;

    public BairroController(BairroRepositorio bairros) {
        this.bairros = bairros;
    }

    @GetMapping("/api/bairros")
    public List<BairroSaida> listar() {
        return bairros.findAllByOrderByNomeAscIdBairroAsc().stream().map(BairroSaida::de).toList();
    }
}
