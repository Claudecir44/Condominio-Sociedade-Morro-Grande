package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.presentation.loginadmin.LoginAdminActivity;
import com.cjstudio.condominio_sociedade_morro_grande.presentation.loginmorador.LoginMoradorActivity;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.FirebaseFirestore;

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

        // Aguarda 1.5 segundos e redireciona conforme o perfil
        new Handler().postDelayed(() -> {
            String perfil = BuildConfig.PERFIL; // "admin" ou "morador"
            Log.d(TAG, "Perfil detectado: " + perfil);

            if ("admin".equals(perfil)) {
                startActivity(new Intent(MainActivity.this, LoginAdminActivity.class));
            } else {
                startActivity(new Intent(MainActivity.this, LoginMoradorActivity.class));
            }
            finish();
        }, 1500);
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