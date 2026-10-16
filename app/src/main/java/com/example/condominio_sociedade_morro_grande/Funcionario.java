package com.cjstudio.condominio_sociedade_morro_grande.domain.model;

public class Funcionario {
    private String id;
    private String nome;
    private String rua;
    private String numero;
    private String bairro;
    private String cep;
    private String cidade;
    private String estado;
    private String pais;
    private String telefone;
    private String email;
    private String rg;
    private String cpf;
    private String cargaHoraria;
    private String turno;
    private String horaEntrada;
    private String horaSaida;
    private String imagemUri;
    private java.util.List<String> anexos;
    private String cargo;

    public Funcionario() {}

    // Getters
    public String getId() { return id; }
    public String getNome() { return nome; }
    public String getRua() { return rua; }
    public String getNumero() { return numero; }
    public String getBairro() { return bairro; }
    public String getCep() { return cep; }
    public String getCidade() { return cidade; }
    public String getEstado() { return estado; }
    public String getPais() { return pais; }
    public String getTelefone() { return telefone; }
    public String getEmail() { return email; }
    public String getRg() { return rg; }
    public String getCpf() { return cpf; }
    public String getCargaHoraria() { return cargaHoraria; }
    public String getTurno() { return turno; }
    public String getHoraEntrada() { return horaEntrada; }
    public String getHoraSaida() { return horaSaida; }
    public String getImagemUri() { return imagemUri; }
    public java.util.List<String> getAnexos() { return anexos; }
    public void setAnexos(java.util.List<String> anexos) { this.anexos = anexos; }
    public String getCargo() { return cargo; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setNome(String nome) { this.nome = nome; }
    public void setRua(String rua) { this.rua = rua; }
    public void setNumero(String numero) { this.numero = numero; }
    public void setBairro(String bairro) { this.bairro = bairro; }
    public void setCep(String cep) { this.cep = cep; }
    public void setCidade(String cidade) { this.cidade = cidade; }
    public void setEstado(String estado) { this.estado = estado; }
    public void setPais(String pais) { this.pais = pais; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public void setEmail(String email) { this.email = email; }
    public void setRg(String rg) { this.rg = rg; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public void setCargaHoraria(String cargaHoraria) { this.cargaHoraria = cargaHoraria; }
    public void setTurno(String turno) { this.turno = turno; }
    public void setHoraEntrada(String horaEntrada) { this.horaEntrada = horaEntrada; }
    public void setHoraSaida(String horaSaida) { this.horaSaida = horaSaida; }
    public void setImagemUri(String imagemUri) { this.imagemUri = imagemUri; }
    public void setCargo(String cargo) { this.cargo = cargo; }
}