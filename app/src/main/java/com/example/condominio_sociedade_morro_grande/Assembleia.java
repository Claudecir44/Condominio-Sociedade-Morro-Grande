package com.cjstudio.condominio_sociedade_morro_grande;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Assembleia {

    private long id;
    private String dataHora;
    private String local;
    private String descricao;
    private String assunto;
    private List<Uri> anexos;

    public Assembleia() {
        this.id = -1;
        this.anexos = new ArrayList<>();
        this.assunto = "";
    }

    public Assembleia(long id, String dataHora, String local, String descricao, String assunto, List<Uri> anexos) {
        this.id = id;
        this.dataHora = dataHora;
        this.local = local;
        this.descricao = descricao;
        this.assunto = assunto != null ? assunto : "";
        this.anexos = anexos != null ? anexos : new ArrayList<>();
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getDataHora() { return dataHora; }
    public void setDataHora(String dataHora) { this.dataHora = dataHora; }

    public String getLocal() { return local; }
    public void setLocal(String local) { this.local = local; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getAssunto() { return assunto; }
    public void setAssunto(String assunto) { this.assunto = assunto; }

    public List<Uri> getAnexos() { return anexos; }
    public void setAnexos(List<Uri> anexos) { this.anexos = anexos; }

    public JSONObject toJson() {
        try {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("datahora", dataHora);
            json.put("local", local);
            json.put("descricao", descricao);
            json.put("assunto", assunto);

            JSONArray jsonAnexos = new JSONArray();
            for (Uri uri : anexos) {
                jsonAnexos.put(uri.toString());
            }
            json.put("anexos", jsonAnexos.toString());

            return json;
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    public static Assembleia fromJson(JSONObject json) {
        Assembleia assembleia = new Assembleia();
        try {
            assembleia.setId(json.optLong("id", -1));
            assembleia.setDataHora(json.optString("datahora", ""));
            assembleia.setLocal(json.optString("local", ""));
            assembleia.setDescricao(json.optString("descricao", ""));
            assembleia.setAssunto(json.optString("assunto", ""));

            List<Uri> anexosList = new ArrayList<>();
            JSONArray jsonAnexos = new JSONArray(json.optString("anexos", "[]"));
            for (int i = 0; i < jsonAnexos.length(); i++) {
                anexosList.add(Uri.parse(jsonAnexos.getString(i)));
            }
            assembleia.setAnexos(anexosList);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return assembleia;
    }
}