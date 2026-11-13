package br.com.ecoponto.ponto;

import br.com.ecoponto.bairro.Bairro;
import br.com.ecoponto.bairro.BairroRepositorio;
import br.com.ecoponto.categoria.Categoria;
import br.com.ecoponto.categoria.CategoriaRepositorio;
import br.com.ecoponto.validacao.ValidadorDeEntradas;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConferenciaDoPonto {

    static final String BAIRRO_FORA_DA_LISTA = "Escolha um bairro da lista.";
    static final String MATERIAL_FORA_DA_LISTA = "Escolha só materiais da lista, sem repetir.";

    private final BairroRepositorio bairros;
    private final CategoriaRepositorio categorias;
    private final ValidadorDeEntradas validador;

    public ConferenciaDoPonto(BairroRepositorio bairros, CategoriaRepositorio categorias,
            ValidadorDeEntradas validador) {
        this.bairros = bairros;
        this.categorias = categorias;
        this.validador = validador;
    }

    record Conferida(Bairro bairro, List<Categoria> materiais) {
    }

    Conferida conferir(PontoEntrada entrada) {
        Map<String, String> campos = validador.erros(entrada);
        Bairro bairro = null;
        if (entrada.idBairro() != null) {
            bairro = bairros.findById(entrada.idBairro()).orElse(null);
            if (bairro == null) {
                campos.putIfAbsent("idBairro", BAIRRO_FORA_DA_LISTA);
            }
        }
        List<Categoria> materiais = List.of();
        if (!campos.containsKey("categorias")) {
            materiais = categorias.findAllById(entrada.categorias()).stream()
                    .sorted(Comparator.comparing(Categoria::getIdCategoria))
                    .toList();
            if (materiais.size() != entrada.categorias().size()) {
                campos.put("categorias", MATERIAL_FORA_DA_LISTA);
            }
        }
        ValidadorDeEntradas.exigirSemErros(campos);
        return new Conferida(bairro, materiais);
    }
}
