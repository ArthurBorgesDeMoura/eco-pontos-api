package br.com.ecoponto.ponto;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PontoRepositorio extends JpaRepository<Ponto, Integer> {

    String FILTROS_DA_BUSCA = " where p.status = :status"
            + " and (:bairro is null or p.bairro.idBairro = :bairro)"
            + " and (:categoria is null or exists ("
            + "select 1 from p.materiais m where m.idCategoria = :categoria))";

    @Query("select count(p) from Ponto p" + FILTROS_DA_BUSCA)
    long contar(@Param("status") StatusDoPonto status, @Param("bairro") Integer bairro,
            @Param("categoria") Integer categoria);

    @Query("select p from Ponto p join fetch p.bairro b" + FILTROS_DA_BUSCA
            + " order by b.nome, p.nome, p.idPonto")
    List<Ponto> buscar(@Param("status") StatusDoPonto status, @Param("bairro") Integer bairro,
            @Param("categoria") Integer categoria, Pageable pagina);

    String FILTROS_DAS_SUGESTOES = " where p.autor.idUsuario = :autor and (:status is null or p.status = :status)";

    @Query("select count(p) from Ponto p" + FILTROS_DAS_SUGESTOES)
    long contarSugestoes(@Param("autor") Integer autor, @Param("status") StatusDoPonto status);

    @Query("select p from Ponto p join fetch p.bairro" + FILTROS_DAS_SUGESTOES + " order by p.idPonto desc")
    List<Ponto> buscarSugestoes(@Param("autor") Integer autor, @Param("status") StatusDoPonto status,
            Pageable pagina);

    @Query("select p from Ponto p join fetch p.bairro join fetch p.autor where p.status = :status"
            + " order by p.idPonto")
    List<Ponto> filaDoPainel(@Param("status") StatusDoPonto status, Pageable pagina);

    @Query("select p from Ponto p join fetch p.bairro b join fetch p.autor where p.status = :status"
            + " order by b.nome, p.nome, p.idPonto")
    List<Ponto> pontosDoPainel(@Param("status") StatusDoPonto status, Pageable pagina);

    @Query("select p from Ponto p join fetch p.bairro join fetch p.autor where p.idPonto = :idPonto")
    Optional<Ponto> doPainel(@Param("idPonto") Integer idPonto);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE ponto_coleta FORCE INDEX (PRIMARY) SET status = :decisao"
            + " WHERE id_ponto = :idPonto AND status = :origem", nativeQuery = true)
    int decidir(@Param("idPonto") Integer idPonto, @Param("origem") String origem, @Param("decisao") String decisao);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Ponto p where p.idPonto = :idPonto")
    Optional<Ponto> travar(@Param("idPonto") Integer idPonto);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Ponto p where p.idPonto = :idPonto")
    int excluir(@Param("idPonto") Integer idPonto);

    @Query("select new br.com.ecoponto.ponto.ContagemDoMaterial(c.idCategoria, count(distinct p.idPonto))"
            + " from Categoria c left join c.pontos p on p.status = :status"
            + " and (:bairro is null or p.bairro.idBairro = :bairro)"
            + " group by c.idCategoria order by c.idCategoria")
    List<ContagemDoMaterial> contarPorMaterial(@Param("status") StatusDoPonto status,
            @Param("bairro") Integer bairro);

    @Query("select p from Ponto p join fetch p.bairro"
            + " where p.idPonto = :idPonto and (:status is null or p.status = :status)")
    Optional<Ponto> detalhe(@Param("idPonto") Integer idPonto, @Param("status") StatusDoPonto status);

    @Query("select new br.com.ecoponto.ponto.MaterialDoPonto(p.idPonto, m.idCategoria, m.nome, m.descricao)"
            + " from Ponto p join p.materiais m where p.idPonto in :pontos order by p.idPonto, m.idCategoria")
    List<MaterialDoPonto> materiaisDos(@Param("pontos") Collection<Integer> pontos);
}
