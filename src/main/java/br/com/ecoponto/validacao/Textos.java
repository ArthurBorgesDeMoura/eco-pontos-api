package br.com.ecoponto.validacao;

public final class Textos {

    private Textos() {
    }

    public static String aparar(String texto) {
        if (texto == null) {
            return null;
        }
        int inicio = 0;
        int fim = texto.length();
        while (inicio < fim && espaco(texto.charAt(inicio))) {
            inicio++;
        }
        while (fim > inicio && espaco(texto.charAt(fim - 1))) {
            fim--;
        }
        return texto.substring(inicio, fim);
    }

    public static String aparadoOuNulo(String texto) {
        String aparado = aparar(texto);
        return aparado == null || aparado.isEmpty() ? null : aparado;
    }

    public static boolean temTexto(String texto) {
        if (texto == null) {
            return false;
        }
        for (int i = 0; i < texto.length(); i++) {
            if (!espaco(texto.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean espaco(char c) {
        return c == ' ' || (c >= '\t' && c <= '\r') || Character.isSpaceChar(c) || c == '﻿';
    }
}
