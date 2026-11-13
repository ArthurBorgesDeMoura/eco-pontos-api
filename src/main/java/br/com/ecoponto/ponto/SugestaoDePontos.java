package br.com.ecoponto.ponto;

import br.com.ecoponto.conta.UsuarioRepositorio;
import br.com.ecoponto.ponto.ConferenciaDoPonto.Conferida;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SugestaoDePontos {

    private final PontoRepositorio pontos;
    private final UsuarioRepositorio usuarios;
    private final ConferenciaDoPonto conferencia;

    public SugestaoDePontos(PontoRepositorio pontos, UsuarioRepositorio usuarios, ConferenciaDoPonto conferencia) {
        this.pontos = pontos;
        this.usuarios = usuarios;
        this.conferencia = conferencia;
    }

    @Transactional
    public PontoCriado sugerir(PontoEntrada entrada, int idAutor) {
        Conferida conferida = conferencia.conferir(entrada);
        Ponto ponto = Ponto.novaSugestao(entrada.nome(), entrada.endereco(), conferida.bairro(), entrada.horario(),
                entrada.contato(), usuarios.getReferenceById(idAutor), conferida.materiais());
        pontos.saveAndFlush(ponto);
        return PontoCriado.de(ponto);
    }
}
