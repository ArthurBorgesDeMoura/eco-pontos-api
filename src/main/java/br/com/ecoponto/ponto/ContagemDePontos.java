package br.com.ecoponto.ponto;

import java.util.List;

public record ContagemDePontos(long total, List<ContagemDoMaterial> categorias) {
}
