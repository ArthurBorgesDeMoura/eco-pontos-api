package br.com.ecoponto.bairro;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "bairro")
public class Bairro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idBairro;

    private String nome;

    protected Bairro() {
    }

    public Integer getIdBairro() {
        return idBairro;
    }

    public String getNome() {
        return nome;
    }
}
