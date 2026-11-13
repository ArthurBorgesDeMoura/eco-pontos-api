package br.com.ecoponto.erro;

import java.sql.SQLException;
import java.util.Locale;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

public final class ChaveUnica {

    private ChaveUnica() {
    }

    public static boolean violou(DataIntegrityViolationException erro, String chave) {
        String procurada = chave.toLowerCase(Locale.ROOT);
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            String detalhe = causa instanceof ConstraintViolationException violacao ? violacao.getConstraintName()
                    : causa instanceof SQLException sql ? sql.getMessage() : null;
            if (detalhe != null && detalhe.toLowerCase(Locale.ROOT).contains(procurada)) {
                return true;
            }
        }
        return false;
    }
}
