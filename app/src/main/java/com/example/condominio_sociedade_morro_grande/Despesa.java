package com.cjstudio.condominio_sociedade_morro_grande.domain.model;

import java.util.List;

public class Despesa {
    private String id;
    private String dataHora;
    private String nome;
    private String descricao;
    private double valor;
    private List<String> anexos;

    public Despesa() {}

    public Despesa(String dataHora, String nome, String descricao, double valor, List<String> anexos) {
        this.dataHora = dataHora;
        this.nome = nome;
        this.descricao = descricao;
        this.valor = valor;
        this.anexos = anexos;
    }

    // Getters e Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDataHora() { return dataHora; }
    public void setDataHora(String dataHora) { this.dataHora = dataHora; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public double getValor() { return valor; }
    public void setValor(double valor) { this.valor = valor; }

    public List<String> getAnexos() { return anexos; }
    public void setAnexos(List<String> anexos) { this.anexos = anexos; }
}