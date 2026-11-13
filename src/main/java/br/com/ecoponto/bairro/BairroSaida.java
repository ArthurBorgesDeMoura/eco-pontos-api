package br.com.ecoponto.bairro;

public record BairroSaida(Integer idBairro, String nome) {

    public static BairroSaida de(Bairro bairro) {
        return new BairroSaida(bairro.getIdBairro(), bairro.getNome());
    }
}
