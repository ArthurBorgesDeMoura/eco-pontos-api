package br.com.ecoponto.categoria;

import br.com.ecoponto.ponto.Ponto;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "categoria_residuo")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idCategoria;

    private String nome;

    private String descricao;

    @ManyToMany(mappedBy = "materiais", fetch = FetchType.LAZY)
    private Set<Ponto> pontos = new HashSet<>();

    protected Categoria() {
    }

    static Categoria nova(String nome, String descricao) {
        Categoria categoria = new Categoria();
        categoria.editar(nome, descricao);
        return categoria;
    }

    void editar(String novoNome, String novaDescricao) {
        nome = novoNome;
        descricao = novaDescricao;
    }

    public Integer getIdCategoria() {
        return idCategoria;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }
}
