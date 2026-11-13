package br.com.ecoponto.conta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idUsuario;

    private String nome;

    private String email;

    private String senhaHash;

    @Enumerated(EnumType.STRING)
    private Perfil perfil;

    @Column(insertable = false, updatable = false)
    private LocalDateTime dataCadastro;

    protected Usuario() {
    }

    private Usuario(String nome, String email, String senhaHash, Perfil perfil) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.perfil = perfil;
    }

    public static Usuario novoUsuario(String nome, String email, String senhaHash) {
        return new Usuario(nome, email, senhaHash, Perfil.USUARIO);
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public LocalDateTime getDataCadastro() {
        return dataCadastro;
    }

    @Override
    public String toString() {
        return "Usuario[idUsuario=" + idUsuario + ", email=" + email + ", perfil=" + perfil + "]";
    }
}
