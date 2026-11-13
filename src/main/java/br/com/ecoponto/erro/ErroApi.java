package br.com.ecoponto.erro;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ErroApi(
        String codigo,
        String mensagem,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> campos) {

    public ErroApi {
        campos = campos == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(campos));
    }

    public static ErroApi de(CodigoDeErro codigo) {
        return new ErroApi(codigo.name(), codigo.mensagemPadrao(), Map.of());
    }

    public static ErroApi de(CodigoDeErro codigo, String mensagem) {
        return new ErroApi(codigo.name(), mensagem, Map.of());
    }

    public static ErroApi de(CodigoDeErro codigo, String mensagem, Map<String, String> campos) {
        return new ErroApi(codigo.name(), mensagem, campos);
    }
}
