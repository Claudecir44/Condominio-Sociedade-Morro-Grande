package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.presentation.dashboard.DashBoardActivity;
import com.cjstudio.condominio_sociedade_morro_grande.presentation.loginadmin.LoginAdminActivity;
import com.cjstudio.condominio_sociedade_morro_grande.presentation.loginmorador.LoginMoradorActivity;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 🔥 INICIALIZA O FIREBASE (obrigatório!)
        FirebaseApp.initializeApp(this);

        // Teste rápido de conexão com o Firestore
        testarFirestore();

        // Aguarda 1.5 segundos; se a sessão continua (não apertou "Sair"), entra direto no painel
        new Handler().postDelayed(this::retomarSessaoOuLogin, 1500);
    }

    private void retomarSessaoOuLogin() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            irParaLogin();
            return;
        }
        boolean admin = "admin".equals(BuildConfig.PERFIL);
        FirebaseFirestore.getInstance().collection(admin ? "administradores" : "moradores")
                .document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (admin && doc.exists() && "admin".equals(doc.getString("tipo"))) {
                        Intent intent = new Intent(this, DashBoardActivity.class);
                        intent.putExtra("tipo_usuario", "admin");
                        intent.putExtra("morador_nome", doc.getString("nome"));
                        abrir(intent);
                    } else if (!admin && doc.exists() && doc.getData() != null) {
                        JSONObject morador = new JSONObject(doc.getData());
                        try { morador.put("cod", doc.getId()); } catch (Exception ignored) {}
                        boolean semFoto = morador.optString("foto", "").trim().isEmpty()
                                && !morador.optBoolean("verTodos"); // conta de visualização dos admins
                        Intent intent = new Intent(this, semFoto ? FotoMoradorActivity.class : DashBoardActivity.class);
                        intent.putExtra("tipo_usuario", "morador");
                        intent.putExtra("morador_nome", morador.optString("nome"));
                        intent.putExtra("morador_cod", doc.getId());
                        intent.putExtra("morador_json", morador.toString());
                        abrir(intent);
                    } else {
                        // Conta sem cadastro (excluído ou admin recusado): encerra a sessão
                        FirebaseAuth.getInstance().signOut();
                        irParaLogin();
                    }
                })
                // Sem internet: cai no login, mas a sessão continua salva
                .addOnFailureListener(e -> irParaLogin());
    }

    private void irParaLogin() {
        String perfil = BuildConfig.PERFIL; // "admin" ou "morador"
        Log.d(TAG, "Perfil detectado: " + perfil);
        abrir(new Intent(this, "admin".equals(perfil) ? LoginAdminActivity.class : LoginMoradorActivity.class));
    }

    private void abrir(Intent intent) {
        startActivity(intent);
        finish();
    }

    private void testarFirestore() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("administradores").limit(1).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "✅ Conectado ao Firestore! Administradores encontrados: " +
                            queryDocumentSnapshots.size());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "❌ Erro ao conectar ao Firestore: " + e.getMessage());
                });
    }
}
