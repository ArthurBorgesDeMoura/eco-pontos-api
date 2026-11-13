package br.com.ecoponto.ponto;

import br.com.ecoponto.bairro.BairroSaida;
import br.com.ecoponto.conta.Usuario;
import java.util.List;

public record PontoDoPainel(Integer idPonto, String nome, String endereco, BairroSaida bairro,
        List<MaterialSaida> materiais, String horario, String contato, StatusDoPonto status,
        SugeridoPor sugeridoPor) {

    public record SugeridoPor(Integer idUsuario, String nome) {

        static SugeridoPor de(Usuario autor) {
            return new SugeridoPor(autor.getIdUsuario(), autor.getNome());
        }
    }

    public static PontoDoPainel de(Ponto ponto, List<MaterialSaida> materiais) {
        return new PontoDoPainel(ponto.getIdPonto(), ponto.getNome(), ponto.getEndereco(),
                BairroSaida.de(ponto.getBairro()), materiais, PontoDetalhe.textoOuNulo(ponto.getHorario()),
                PontoDetalhe.textoOuNulo(ponto.getContato()), ponto.getStatus(), SugeridoPor.de(ponto.getAutor()));
    }
}
