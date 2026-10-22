package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ListasActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listas);

        Button btnListaMoradores = findViewById(R.id.btnListaMoradores);
        Button btnListaAssembleias = findViewById(R.id.btnListaAssembleias);
        Button btnListaDespesas = findViewById(R.id.btnListaDespesas);
        Button btnListaAvisos = findViewById(R.id.btnListaAvisos);

        // Dados pessoais dos moradores (CPF, telefone) só para o administrador e para a conta de
        // visualização dos admins (morador com verTodos), que vê a lista sem editar
        boolean admin = "admin".equals(BuildConfig.PERFIL);
        if (!admin) {
            btnListaMoradores.setVisibility(View.GONE);
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                FirebaseFirestore.getInstance().collection("moradores").document(user.getUid()).get()
                        .addOnSuccessListener(doc -> {
                            if (Boolean.TRUE.equals(doc.getBoolean("verTodos"))) {
                                btnListaMoradores.setVisibility(View.VISIBLE);
                            }
                        });
            }
        }

        btnListaMoradores.setOnClickListener(v -> startActivity(new Intent(this, ListaMoradoresActivity.class)
                .putExtra("somenteLeitura", !admin)));
        btnListaAssembleias.setOnClickListener(v -> startActivity(new Intent(this, ListaAssembleiasActivity.class)));
        btnListaDespesas.setOnClickListener(v -> startActivity(new Intent(this, ListaDespesasActivity.class)));
        btnListaAvisos.setOnClickListener(v -> startActivity(new Intent(this, ListaAvisosActivity.class)));
    }
}