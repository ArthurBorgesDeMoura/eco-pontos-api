package br.com.ecoponto.ponto;

import br.com.ecoponto.bairro.Bairro;
import br.com.ecoponto.categoria.Categoria;
import br.com.ecoponto.conta.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "ponto_coleta")
public class Ponto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idPonto;

    private String nome;

    private String endereco;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_bairro")
    private Bairro bairro;

    private String horario;

    private String contato;

    @Enumerated(EnumType.STRING)
    private StatusDoPonto status;

    @Column(insertable = false, updatable = false)
    private LocalDateTime dataCadastro;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario")
    private Usuario autor;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "ponto_categoria", joinColumns = @JoinColumn(name = "id_ponto"),
            inverseJoinColumns = @JoinColumn(name = "id_categoria"))
    @OrderBy("idCategoria")
    private Set<Categoria> materiais = new LinkedHashSet<>();

    protected Ponto() {
    }

    static Ponto novaSugestao(String nome, String endereco, Bairro bairro, String horario, String contato,
            Usuario autor, List<Categoria> materiais) {
        Ponto ponto = new Ponto();
        ponto.nome = nome;
        ponto.endereco = endereco;
        ponto.bairro = bairro;
        ponto.horario = horario;
        ponto.contato = contato;
        ponto.status = StatusDoPonto.PENDENTE;
        ponto.autor = autor;
        ponto.materiais.addAll(materiais);
        return ponto;
    }

    void editar(String novoNome, String novoEndereco, Bairro novoBairro, String novoHorario, String novoContato,
            List<Categoria> novosMateriais) {
        nome = novoNome;
        endereco = novoEndereco;
        bairro = novoBairro;
        horario = novoHorario;
        contato = novoContato;
        materiais.clear();
        materiais.addAll(novosMateriais);
    }

    public Integer getIdPonto() {
        return idPonto;
    }

    public String getNome() {
        return nome;
    }

    public String getEndereco() {
        return endereco;
    }

    public Bairro getBairro() {
        return bairro;
    }

    public String getHorario() {
        return horario;
    }

    public String getContato() {
        return contato;
    }

    public StatusDoPonto getStatus() {
        return status;
    }

    public LocalDateTime getDataCadastro() {
        return dataCadastro;
    }

    public Usuario getAutor() {
        return autor;
    }

    public Set<Categoria> getMateriais() {
        return materiais;
    }

    @Override
    public String toString() {
        return "Ponto[idPonto=" + idPonto + ", status=" + status + "]";
    }
}
