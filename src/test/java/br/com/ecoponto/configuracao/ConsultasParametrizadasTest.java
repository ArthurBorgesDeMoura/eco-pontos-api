package br.com.ecoponto.configuracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

class ConsultasParametrizadasTest {

    private static final Path CODIGO_PRINCIPAL = Path.of("src", "main", "java");

    private static final List<String> PROIBIDOS = List.of("createQuery(", "createNativeQuery(", "JdbcTemplate",
            "java.sql.Statement", "createStatement(", "prepareStatement(", "executeQuery(", "executeUpdate(",
            "addBatch(", "EntityManager", "JdbcClient", "createSelectionQuery(", "createMutationQuery(",
            "prepareCall(");

    @Test
    void oCodigoPrincipalNaoMontaSqlAMao() throws IOException {
        List<String> achados = new ArrayList<>();
        try (Stream<Path> arquivos = Files.walk(CODIGO_PRINCIPAL)) {
            for (Path arquivo : arquivos.filter(a -> a.toString().endsWith(".java")).toList()) {
                List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
                for (int i = 0; i < linhas.size(); i++) {
                    for (String proibido : PROIBIDOS) {
                        if (linhas.get(i).contains(proibido)) {
                            achados.add(arquivo + ":" + (i + 1) + " usa " + proibido);
                        }
                    }
                }
            }
        }
        assertThat(achados).as("SQL montado à mão no código principal").isEmpty();
    }

    @Test
    void asConsultasDosRepositoriosSoUsamParametrosNomeados() throws Exception {
        List<String> consultas = new ArrayList<>();
        List<String> nativas = new ArrayList<>();
        for (Class<?> repositorio : repositorios()) {
            for (Method metodo : repositorio.getDeclaredMethods()) {
                Query consulta = metodo.getAnnotation(Query.class);
                if (consulta == null) {
                    continue;
                }
                String nome = repositorio.getSimpleName() + "." + metodo.getName();
                consultas.add(nome);
                if (consulta.nativeQuery()) {
                    nativas.add(nome);
                }
                assertThat(consulta.value()).as(nome).doesNotContain("'", "\"", "?")
                        .containsPattern(":[a-zA-Z]+");
                assertThat(metodo.getParameterCount()).as("%s recebe os valores por parâmetro", nome).isPositive();
            }
        }
        assertThat(consultas).as("as consultas escritas à mão").hasSizeGreaterThanOrEqualTo(15);
        assertThat(nativas).containsExactly("PontoRepositorio.decidir");
    }

    private static List<Class<?>> repositorios() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider busca = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definicao) {
                return definicao.getMetadata().isInterface();
            }
        };
        busca.addIncludeFilter(new AssignableTypeFilter(Repository.class));
        List<Class<?>> repositorios = new ArrayList<>();
        for (BeanDefinition definicao : busca.findCandidateComponents("br.com.ecoponto")) {
            repositorios.add(Class.forName(definicao.getBeanClassName()));
        }
        assertThat(repositorios).as("repositórios achados").hasSizeGreaterThanOrEqualTo(4);
        return repositorios;
    }
}
