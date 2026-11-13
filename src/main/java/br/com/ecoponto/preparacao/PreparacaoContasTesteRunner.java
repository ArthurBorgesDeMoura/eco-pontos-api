package br.com.ecoponto.preparacao;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("preparacao")
public class PreparacaoContasTesteRunner implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PreparacaoContasTesteRunner.class);

    private static final List<Path> LOCAIS_DO_SCRIPT =
            List.of(Path.of("..", "db", "ecoponto.sql"), Path.of("db", "ecoponto.sql"));

    private final ContasTesteService contas;

    public PreparacaoContasTesteRunner(ContasTesteService contas) {
        this.contas = contas;
    }

    @Override
    public void run(ApplicationArguments argumentos) throws IOException {
        Path script = acharScript();
        int quantas = contas.refazerHashes(ContasTesteService.lerContas(script));
        LOG.info("Preparação concluída: {} contas de teste com hash bcrypt, lidas de {}.", quantas, script);
    }

    static Path acharScript() {
        return LOCAIS_DO_SCRIPT.stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Não achei o db/ecoponto.sql. Rode a preparação de dentro da pasta backend/."));
    }
}
