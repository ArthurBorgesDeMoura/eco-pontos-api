package br.com.ecoponto.conta;

final class Logs {

    private static final int TAMANHO_MAXIMO = 150;

    private Logs() {
    }

    static String seguro(String texto) {
        if (texto == null) {
            return "";
        }
        String linha = texto.replaceAll("[\\p{Cntrl}]", "_");
        return linha.length() > TAMANHO_MAXIMO ? linha.substring(0, TAMANHO_MAXIMO) + "…" : linha;
    }
}
