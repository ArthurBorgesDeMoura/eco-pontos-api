package br.com.ecoponto.erro;

import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;

public class ErroDaApi extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final CodigoDeErro codigo;
    private final transient Map<String, String> campos;
    private final HttpHeaders cabecalhos;

    public ErroDaApi(CodigoDeErro codigo) {
        this(codigo, codigo.mensagemPadrao(), Map.of());
    }

    public ErroDaApi(CodigoDeErro codigo, String mensagem) {
        this(codigo, mensagem, Map.of());
    }

    public ErroDaApi(CodigoDeErro codigo, String mensagem, Map<String, String> campos) {
        this(codigo, mensagem, campos, new HttpHeaders());
    }

    private ErroDaApi(CodigoDeErro codigo, String mensagem, Map<String, String> campos, HttpHeaders cabecalhos) {
        super(mensagem);
        this.codigo = codigo;
        this.campos = campos;
        this.cabecalhos = HttpHeaders.readOnlyHttpHeaders(cabecalhos);
    }

    public static ErroDaApi muitasTentativas(Duration espera) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, espera.toSeconds())));
        CodigoDeErro codigo = CodigoDeErro.MUITAS_TENTATIVAS;
        return new ErroDaApi(codigo, codigo.mensagemPadrao(), Map.of(), cabecalhos);
    }

    public CodigoDeErro codigo() {
        return codigo;
    }

    public HttpHeaders cabecalhos() {
        return cabecalhos;
    }

    public ErroApi corpo() {
        return ErroApi.de(codigo, getMessage(), campos);
    }
}
