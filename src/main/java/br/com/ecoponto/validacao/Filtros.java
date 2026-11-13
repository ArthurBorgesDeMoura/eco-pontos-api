package br.com.ecoponto.validacao;

import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class Filtros {

    public static final String ID_DE_BAIRRO = "Use o id de um bairro.";
    public static final String ID_DE_CATEGORIA = "Use o id de uma categoria.";
    public static final String ID_DE_PONTO = "Use o id de um ponto.";
    public static final String PAGINA = "A página começa em 1.";
    public static final String TAMANHO = "O tamanho vai de 1 a 50.";
    public static final String STATUS = "Use PENDENTE, APROVADO ou RECUSADO.";
    public static final Set<String> STATUS_DO_PONTO = Set.of("PENDENTE", "APROVADO", "RECUSADO");

    public static final int TAMANHO_PADRAO = 7;
    public static final int TAMANHO_MAXIMO = 50;

    private static final Pattern SO_DIGITOS = Pattern.compile("\\d{1,9}");

    private final Map<String, String> campos = new LinkedHashMap<>();

    public Integer id(String nome, String valor, String mensagem) {
        if (valor == null) {
            return null;
        }
        Integer numero = inteiroPositivo(valor);
        if (numero == null) {
            campos.put(nome, mensagem);
        }
        return numero;
    }

    public int pagina(String valor) {
        if (valor == null) {
            return 1;
        }
        Integer numero = inteiroPositivo(valor);
        if (numero == null) {
            campos.put("pagina", PAGINA);
            return 1;
        }
        return numero;
    }

    public int tamanho(String valor) {
        if (valor == null) {
            return TAMANHO_PADRAO;
        }
        Integer numero = inteiroPositivo(valor);
        if (numero == null || numero > TAMANHO_MAXIMO) {
            campos.put("tamanho", TAMANHO);
            return TAMANHO_PADRAO;
        }
        return numero;
    }

    public String opcao(String nome, String valor, Set<String> opcoes, String padrao, String mensagem) {
        if (valor == null) {
            return padrao;
        }
        if (!opcoes.contains(valor)) {
            campos.put(nome, mensagem);
            return padrao;
        }
        return valor;
    }

    public static int idDoCaminho(String valor, String mensagem) {
        Integer numero = inteiroPositivo(valor);
        if (numero == null) {
            throw new ErroDaApi(CodigoDeErro.DADOS_INVALIDOS, mensagem);
        }
        return numero;
    }

    public void conferir(String mensagem) {
        if (!campos.isEmpty()) {
            throw new ErroDaApi(CodigoDeErro.DADOS_INVALIDOS, mensagem, campos);
        }
    }

    static Integer inteiroPositivo(String texto) {
        if (texto == null || !SO_DIGITOS.matcher(texto).matches()) {
            return null;
        }
        int numero = Integer.parseInt(texto);
        return numero >= 1 ? numero : null;
    }
}
