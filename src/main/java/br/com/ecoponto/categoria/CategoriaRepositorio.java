package br.com.ecoponto.categoria;

import br.com.ecoponto.ponto.StatusDoPonto;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoriaRepositorio extends JpaRepository<Categoria, Integer> {

    String COM_OS_PONTOS = "select new br.com.ecoponto.categoria.CategoriaDoPainel(c.idCategoria, c.nome,"
            + " c.descricao, count(distinct p.idPonto)) from Categoria c left join c.pontos p on p.status = :status";

    List<Categoria> findAllByOrderByIdCategoriaAsc();

    boolean existsByNome(String nome);

    boolean existsByNomeAndIdCategoriaNot(String nome, Integer idCategoria);

    @Query(COM_OS_PONTOS + " group by c.idCategoria, c.nome, c.descricao order by c.idCategoria")
    List<CategoriaDoPainel> doPainel(@Param("status") StatusDoPonto status, Pageable pagina);

    @Query(COM_OS_PONTOS + " where c.idCategoria = :idCategoria group by c.idCategoria, c.nome, c.descricao")
    CategoriaDoPainel umaDoPainel(@Param("idCategoria") Integer idCategoria, @Param("status") StatusDoPonto status);
}
