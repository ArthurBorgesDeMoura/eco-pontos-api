package br.com.ecoponto.erro;

import org.springframework.http.HttpStatus;

public enum CodigoDeErro {

    DADOS_INVALIDOS(HttpStatus.BAD_REQUEST, "Confira os dados enviados."),
    NAO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "Entre na sua conta para continuar."),
    CREDENCIAIS_INVALIDAS(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos."),
    SEM_PERMISSAO(HttpStatus.FORBIDDEN, "Sua conta não tem permissão para fazer isso."),
    NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Não encontramos o que você procurou."),
    METODO_NAO_PERMITIDO(HttpStatus.METHOD_NOT_ALLOWED, "Esta rota não aceita este método."),
    EMAIL_JA_CADASTRADO(HttpStatus.CONFLICT, "Este e-mail já tem conta. Entre ou use outro e-mail."),
    JA_ANALISADO(HttpStatus.CONFLICT, "Esta sugestão já foi analisada."),
    NOME_REPETIDO(HttpStatus.CONFLICT, "Já existe uma categoria com este nome."),
    CONFLITO(HttpStatus.CONFLICT, "Os dados mudaram enquanto você usava a página. Atualize e tente de novo."),
    TIPO_NAO_SUPORTADO(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Envie o corpo em JSON (application/json)."),
    MUITAS_TENTATIVAS(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Espere alguns minutos e tente de novo."),
    ERRO_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Algo deu errado. Tente de novo em instantes."),
    BANCO_INDISPONIVEL(HttpStatus.SERVICE_UNAVAILABLE, "O banco de dados não respondeu. Tente de novo em instantes.");

    private final HttpStatus status;
    private final String mensagemPadrao;

    CodigoDeErro(HttpStatus status, String mensagemPadrao) {
        this.status = status;
        this.mensagemPadrao = mensagemPadrao;
    }

    public HttpStatus status() {
        return status;
    }

    public String mensagemPadrao() {
        return mensagemPadrao;
    }

    public static CodigoDeErro doStatus(int status) {
        return switch (status) {
            case 401 -> NAO_AUTENTICADO;
            case 403 -> SEM_PERMISSAO;
            case 404 -> NAO_ENCONTRADO;
            case 405 -> METODO_NAO_PERMITIDO;
            case 406, 415 -> TIPO_NAO_SUPORTADO;
            case 409 -> CONFLITO;
            case 429 -> MUITAS_TENTATIVAS;
            default -> status >= 400 && status < 500 ? DADOS_INVALIDOS : ERRO_INTERNO;
        };
    }
}
