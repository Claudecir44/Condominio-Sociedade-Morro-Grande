package com.cjstudio.condominio_sociedade_morro_grande.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {

    private static final String PREF_NAME = "condominio_prefs";
    private static final String KEY_FIRST_RUN = "first_run";
    private static final String KEY_USER_TYPE = "user_type";
    private static final String KEY_MORADOR_NOME = "morador_nome";
    private static final String KEY_MORADOR_COD = "morador_cod";
    private static final String KEY_MORADOR_JSON = "morador_json";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public PreferenceManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    public boolean isFirstRun() {
        return prefs.getBoolean(KEY_FIRST_RUN, true);
    }

    public void setFirstRunComplete() {
        editor.putBoolean(KEY_FIRST_RUN, false);
        editor.apply();
    }

    public void setUserType(String tipoUsuario) {
        editor.putString(KEY_USER_TYPE, tipoUsuario);
        editor.apply();
    }

    public String getUserType() {
        return prefs.getString(KEY_USER_TYPE, null);
    }

    public boolean isMorador() {
        return "morador".equalsIgnoreCase(getUserType());
    }

    public boolean isAdmin() {
        return "admin".equalsIgnoreCase(getUserType());
    }

    public void setMoradorData(String nome, String cod, String json) {
        editor.putString(KEY_MORADOR_NOME, nome);
        editor.putString(KEY_MORADOR_COD, cod);
        editor.putString(KEY_MORADOR_JSON, json);
        editor.apply();
    }

    public String getMoradorNome() {
        return prefs.getString(KEY_MORADOR_NOME, null);
    }

    public String getMoradorCod() {
        return prefs.getString(KEY_MORADOR_COD, null);
    }

    public String getMoradorJson() {
        return prefs.getString(KEY_MORADOR_JSON, null);
    }

    public void clear() {
        editor.clear();
        editor.apply();
    }

    public void logoutMorador() {
        editor.remove(KEY_MORADOR_NOME);
        editor.remove(KEY_MORADOR_COD);
        editor.remove(KEY_MORADOR_JSON);
        editor.remove(KEY_USER_TYPE);
        editor.apply();
    }
}