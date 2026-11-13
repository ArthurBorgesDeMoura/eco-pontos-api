package br.com.ecoponto.preparacao;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class ContasTesteService {

    private static final Pattern CONTA_NO_SCRIPT =
            Pattern.compile("'([^'\\s]+@[^'\\s]+)',\\s*SHA2\\('([^']*)',\\s*256\\)");

    private static final Pattern HOST_DO_BANCO = Pattern.compile("^jdbc:mariadb://(\\[[^\\]]*\\]|[^:/?,]*)");
    private static final Set<String> HOSTS_LOCAIS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private static final String GRAVAR_HASH = "UPDATE usuario SET senha_hash = :hash WHERE email = :email";

    private final NamedParameterJdbcOperations jdbc;
    private final PasswordEncoder codificador;
    private final TransactionOperations transacao;
    private final String urlDoBanco;

    public ContasTesteService(NamedParameterJdbcOperations jdbc, PasswordEncoder codificador,
            TransactionOperations transacao, @Value("${spring.datasource.url}") String urlDoBanco) {
        this.jdbc = jdbc;
        this.codificador = codificador;
        this.transacao = transacao;
        this.urlDoBanco = urlDoBanco;
    }

    public static List<ContaTeste> lerContas(Path script) throws IOException {
        Matcher par = CONTA_NO_SCRIPT.matcher(Files.readString(script, StandardCharsets.UTF_8));
        List<ContaTeste> contas = new ArrayList<>();
        while (par.find()) {
            contas.add(new ContaTeste(par.group(1), par.group(2)));
        }
        if (contas.isEmpty()) {
            throw new IllegalStateException("Nenhuma conta de teste encontrada em " + script + ".");
        }
        return contas;
    }

    public int refazerHashes(List<ContaTeste> contas) {
        exigirBancoLocal(urlDoBanco);
        Integer quantas = transacao.execute(estado -> gravar(contas));
        return quantas == null ? 0 : quantas;
    }

    static void exigirBancoLocal(String url) {
        Matcher host = HOST_DO_BANCO.matcher(url);
        String nome = host.find() ? host.group(1).toLowerCase(Locale.ROOT) : "";
        if (!HOSTS_LOCAIS.contains(nome)) {
            throw new IllegalStateException("A preparação só roda em banco local (localhost, 127.0.0.1 ou ::1),"
                    + " e o banco configurado está em \"" + nome + "\".");
        }
    }

    private int gravar(List<ContaTeste> contas) {
        for (ContaTeste conta : contas) {
            MapSqlParameterSource parametros = new MapSqlParameterSource()
                    .addValue("hash", codificador.encode(conta.senha()))
                    .addValue("email", conta.email());
            if (jdbc.update(GRAVAR_HASH, parametros) != 1) {
                throw new IllegalStateException(
                        "A conta " + conta.email() + " não está no banco. Rode o db/ecoponto.sql antes.");
            }
        }
        return contas.size();
    }

    public record ContaTeste(String email, String senha) {

        @Override
        public String toString() {
            return "ContaTeste[email=" + email + "]";
        }
    }
}
