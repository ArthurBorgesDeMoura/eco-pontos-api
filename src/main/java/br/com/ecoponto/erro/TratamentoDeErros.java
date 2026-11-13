package br.com.ecoponto.erro;

import br.com.ecoponto.configuracao.LimiteDoCorpoFilter;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.hibernate.exception.JDBCConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class TratamentoDeErros extends ResponseEntityExceptionHandler {

    public static final String CONFIRA_OS_CAMPOS = "Confira os campos destacados.";
    public static final String CONFIRA_OS_FILTROS = "Confira os filtros.";
    static final String JSON_INVALIDO = "Envie um JSON válido no corpo da requisição.";
    static final String VALOR_INVALIDO = "Valor inválido.";
    static final String INFORME_O_PARAMETRO = "Informe este parâmetro.";

    private static final Logger LOG = LoggerFactory.getLogger(TratamentoDeErros.class);

    private static final List<Set<String>> PRIORIDADE = List.of(
            Set.of("NotNull", "NotBlank", "NotEmpty", "TemTexto"),
            Set.of("Size", "MaximoDeBytes", "Min", "Max", "Positive"));

    private final MessageSource mensagens;

    public TratamentoDeErros(MessageSource mensagens) {
        this.mensagens = mensagens;
    }

    @ExceptionHandler(ErroDaApi.class)
    public ResponseEntity<Object> erroDaApi(ErroDaApi erro) {
        return ResponseEntity.status(erro.codigo().status()).headers(erro.cabecalhos()).body(erro.corpo());
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<Object> bancoIndisponivel(DataAccessResourceFailureException erro) {
        LOG.warn("O banco não respondeu: {}", erro.getMessage());
        return responder(CodigoDeErro.BANCO_INDISPONIVEL, CodigoDeErro.BANCO_INDISPONIVEL.mensagemPadrao(),
                Map.of(), null);
    }

    @ExceptionHandler(CannotCreateTransactionException.class)
    public ResponseEntity<Object> transacaoSemBanco(CannotCreateTransactionException erro) {
        if (faltouConexao(erro)) {
            LOG.warn("O banco não respondeu ao abrir a transação: {}", erro.getMessage());
            return responder(CodigoDeErro.BANCO_INDISPONIVEL, CodigoDeErro.BANCO_INDISPONIVEL.mensagemPadrao(),
                    Map.of(), null);
        }
        return erroInesperado(erro);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> erroInesperado(Exception erro) {
        LOG.error("Erro inesperado ao atender a requisição.", erro);
        return responder(CodigoDeErro.ERRO_INTERNO, CodigoDeErro.ERRO_INTERNO.mensagemPadrao(), Map.of(), null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException erro, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest pedido) {
        return responder(CodigoDeErro.DADOS_INVALIDOS, CONFIRA_OS_CAMPOS,
                camposDe(erro.getBindingResult().getFieldErrors()), cabecalhos);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException erro, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest pedido) {
        String mensagemDoCaminho = null;
        Map<String, String> doCorpo = new LinkedHashMap<>();
        Map<String, String> daConsulta = new LinkedHashMap<>();
        for (ParameterValidationResult resultado : erro.getAllValidationResults()) {
            MethodParameter parametro = resultado.getMethodParameter();
            if (resultado instanceof ParameterErrors errosDoObjeto) {
                camposDe(errosDoObjeto.getFieldErrors()).forEach(doCorpo::putIfAbsent);
                continue;
            }
            String mensagem = resultado.getResolvableErrors().stream()
                    .map(e -> Objects.requireNonNullElse(e.getDefaultMessage(), VALOR_INVALIDO))
                    .findFirst()
                    .orElse(VALOR_INVALIDO);
            if (parametro.hasParameterAnnotation(PathVariable.class)) {
                mensagemDoCaminho = mensagemDoCaminho == null ? mensagem : mensagemDoCaminho;
            } else {
                daConsulta.putIfAbsent(nomeDoParametro(parametro), mensagem);
            }
        }
        if (mensagemDoCaminho != null) {
            return responder(CodigoDeErro.DADOS_INVALIDOS, mensagemDoCaminho, Map.of(), cabecalhos);
        }
        if (!doCorpo.isEmpty()) {
            return responder(CodigoDeErro.DADOS_INVALIDOS, CONFIRA_OS_CAMPOS, doCorpo, cabecalhos);
        }
        return responder(CodigoDeErro.DADOS_INVALIDOS, CONFIRA_OS_FILTROS, daConsulta, cabecalhos);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException erro, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest pedido) {
        if (erro instanceof MethodArgumentTypeMismatchException doParametro) {
            String mensagem = mensagemDoParametro(doParametro.getName());
            if (doParametro.getParameter().hasParameterAnnotation(PathVariable.class)) {
                return responder(CodigoDeErro.DADOS_INVALIDOS, mensagem, Map.of(), cabecalhos);
            }
            return responder(CodigoDeErro.DADOS_INVALIDOS, CONFIRA_OS_FILTROS,
                    Map.of(doParametro.getName(), mensagem), cabecalhos);
        }
        return responder(CodigoDeErro.DADOS_INVALIDOS, CodigoDeErro.DADOS_INVALIDOS.mensagemPadrao(), Map.of(),
                cabecalhos);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException erro, HttpHeaders cabecalhos, HttpStatusCode status,
            WebRequest pedido) {
        return responder(CodigoDeErro.DADOS_INVALIDOS, CONFIRA_OS_FILTROS,
                Map.of(erro.getParameterName(), INFORME_O_PARAMETRO), cabecalhos);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException erro, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest pedido) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof LimiteDoCorpoFilter.CorpoGrandeDemais) {
                return responder(CodigoDeErro.DADOS_INVALIDOS, LimiteDoCorpoFilter.CORPO_GRANDE_DEMAIS, Map.of(),
                        cabecalhos);
            }
        }
        if (erro.getCause() instanceof MismatchedInputException tipoErrado) {
            String campo = nomeDoCampo(tipoErrado);
            if (campo != null) {
                return responder(CodigoDeErro.DADOS_INVALIDOS, CONFIRA_OS_CAMPOS, Map.of(campo, VALOR_INVALIDO),
                        cabecalhos);
            }
        }
        return responder(CodigoDeErro.DADOS_INVALIDOS, JSON_INVALIDO, Map.of(), cabecalhos);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception erro, Object corpo, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest pedido) {
        if (pedido instanceof ServletWebRequest web && web.getResponse() != null && web.getResponse().isCommitted()) {
            return null;
        }
        CodigoDeErro codigo = CodigoDeErro.doStatus(status.value());
        if (status.is5xxServerError()) {
            LOG.error("Erro inesperado ao atender a requisição.", erro);
        }
        return ResponseEntity.status(status)
                .headers(cabecalhos)
                .body(ErroApi.de(codigo, codigo.mensagemPadrao(), Map.of()));
    }

    static Map<String, String> camposDe(List<FieldError> erros) {
        Map<String, FieldError> escolhidos = new LinkedHashMap<>();
        for (FieldError erro : erros) {
            String campo = semIndice(erro.getField());
            FieldError atual = escolhidos.get(campo);
            if (atual == null || prioridade(erro) < prioridade(atual)) {
                escolhidos.put(campo, erro);
            }
        }
        Map<String, String> campos = new LinkedHashMap<>();
        escolhidos.forEach((campo, erro) -> campos.put(campo, mensagemDe(erro)));
        return campos;
    }

    public static int prioridadeDaRestricao(String restricao) {
        for (int i = 0; i < PRIORIDADE.size(); i++) {
            if (PRIORIDADE.get(i).contains(restricao)) {
                return i;
            }
        }
        return PRIORIDADE.size();
    }

    private static int prioridade(FieldError erro) {
        return prioridadeDaRestricao(erro.getCode());
    }

    private static String mensagemDe(FieldError erro) {
        if (erro.isBindingFailure() || erro.getDefaultMessage() == null) {
            return VALOR_INVALIDO;
        }
        return erro.getDefaultMessage();
    }

    private static String semIndice(String campo) {
        int fim = campo.length();
        for (char separador : new char[] {'[', '.'}) {
            int posicao = campo.indexOf(separador);
            if (posicao >= 0 && posicao < fim) {
                fim = posicao;
            }
        }
        return campo.substring(0, fim);
    }

    private static String nomeDoParametro(MethodParameter parametro) {
        RequestParam anotacao = parametro.getParameterAnnotation(RequestParam.class);
        if (anotacao != null) {
            String nome = anotacao.name().isEmpty() ? anotacao.value() : anotacao.name();
            if (!nome.isEmpty()) {
                return nome;
            }
        }
        return Objects.requireNonNullElse(parametro.getParameterName(), "valor");
    }

    static boolean faltouConexao(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof DataAccessResourceFailureException || causa instanceof JDBCConnectionException
                    || causa instanceof SQLTransientConnectionException
                    || causa instanceof SQLNonTransientConnectionException
                    || (causa instanceof SQLException sql && sql.getSQLState() != null
                            && sql.getSQLState().startsWith("08"))) {
                return true;
            }
        }
        return false;
    }

    private static String nomeDoCampo(JsonMappingException erro) {
        return erro.getPath().stream()
                .map(JsonMappingException.Reference::getFieldName)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String mensagemDoParametro(String nome) {
        return mensagens.getMessage("parametro." + nome, null, VALOR_INVALIDO, Locale.ROOT);
    }

    private static ResponseEntity<Object> responder(
            CodigoDeErro codigo, String mensagem, Map<String, String> campos, HttpHeaders cabecalhos) {
        return ResponseEntity.status(codigo.status())
                .headers(cabecalhos)
                .body(ErroApi.de(codigo, mensagem, campos));
    }
}
