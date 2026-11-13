package br.com.ecoponto.configuracao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BancoConfig {

    private static final Logger LOG = LoggerFactory.getLogger(BancoConfig.class);

    public BancoConfig(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String usuario,
            @Value("${spring.datasource.password:}") String senha) {
        if (senha.isBlank()) {
            throw new IllegalStateException(
                    "A senha do banco está vazia. Preencha ECOPONTO_DB_SENHA no .env da raiz (veja o .env.example).");
        }
        LOG.info("Banco: {}, com o usuário {}.", url.replaceFirst("\\?.*$", ""), usuario);
    }
}
