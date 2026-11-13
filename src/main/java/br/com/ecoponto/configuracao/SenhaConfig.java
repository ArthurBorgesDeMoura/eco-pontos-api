package br.com.ecoponto.configuracao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SenhaConfig {

    @Bean
    public PasswordEncoder passwordEncoder(@Value("${ecoponto.senha.custo-bcrypt}") int custo) {
        return new BCryptPasswordEncoder(custo);
    }
}
