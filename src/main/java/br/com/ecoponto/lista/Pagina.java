package br.com.ecoponto.lista;

import java.util.List;

public record Pagina<T>(List<T> itens, long total, int pagina, int tamanho) {
}
