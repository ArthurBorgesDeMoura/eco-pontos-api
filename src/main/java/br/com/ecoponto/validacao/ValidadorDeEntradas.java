package br.com.ecoponto.validacao;

import br.com.ecoponto.erro.CodigoDeErro;
import br.com.ecoponto.erro.ErroDaApi;
import br.com.ecoponto.erro.TratamentoDeErros;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ValidadorDeEntradas {

    private final Validator validador;

    public ValidadorDeEntradas(Validator validador) {
        this.validador = validador;
    }

    public Map<String, String> erros(Object entrada) {
        Map<String, ConstraintViolation<Object>> escolhidas = new LinkedHashMap<>();
        Set<ConstraintViolation<Object>> violacoes = validador.validate(entrada);
        violacoes.stream()
                .sorted(Comparator.comparing(violacao -> violacao.getPropertyPath().toString()))
                .forEach(violacao -> {
                    String campo = violacao.getPropertyPath().iterator().next().getName();
                    ConstraintViolation<Object> atual = escolhidas.get(campo);
                    if (atual == null || prioridade(violacao) < prioridade(atual)) {
                        escolhidas.put(campo, violacao);
                    }
                });
        Map<String, String> campos = new LinkedHashMap<>();
        escolhidas.forEach((campo, violacao) -> campos.put(campo, violacao.getMessage()));
        return campos;
    }

    public static void exigirSemErros(Map<String, String> campos) {
        if (!campos.isEmpty()) {
            throw new ErroDaApi(CodigoDeErro.DADOS_INVALIDOS, TratamentoDeErros.CONFIRA_OS_CAMPOS, campos);
        }
    }

    private static int prioridade(ConstraintViolation<Object> violacao) {
        return TratamentoDeErros.prioridadeDaRestricao(
                violacao.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
    }
}
