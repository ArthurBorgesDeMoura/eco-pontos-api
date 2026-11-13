package br.com.ecoponto.ponto;

public record MaterialSaida(Integer idCategoria, String nome) {

    public static MaterialSaida de(MaterialDoPonto material) {
        return new MaterialSaida(material.idCategoria(), material.nome());
    }
}
