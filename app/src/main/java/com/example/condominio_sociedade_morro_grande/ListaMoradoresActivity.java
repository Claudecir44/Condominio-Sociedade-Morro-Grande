package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.MoradorFirestoreRepository;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ListaMoradoresActivity extends AppCompatActivity {

    private RecyclerView recyclerMoradores;
    private com.cjstudio.condominio_sociedade_morro_grande.MoradorAdapter adapter;
    private List<JSONObject> moradores = new ArrayList<>();
    private MoradorFirestoreRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_moradores);

        recyclerMoradores = findViewById(R.id.recyclerMoradores);
        recyclerMoradores.setLayoutManager(new LinearLayoutManager(this));

        repository = new MoradorFirestoreRepository();

        adapter = new com.cjstudio.condominio_sociedade_morro_grande.MoradorAdapter(this, moradores, new MoradorAdapter.OnItemClickListener() {
            @Override
            public void onEditar(JSONObject morador, int position) {
                String cod = morador.optString("cod");
                Intent intent = new Intent(ListaMoradoresActivity.this, com.cjstudio.condominio_sociedade_morro_grande.CadastroMoradorActivity.class);
                intent.putExtra("editarCod", cod);
                startActivity(intent);
            }

            @Override
            public void onExcluir(JSONObject morador, int position) {
                String cod = morador.optString("cod");
                new AlertDialog.Builder(ListaMoradoresActivity.this)
                        .setTitle("Excluir morador")
                        .setMessage("Deseja realmente excluir " + morador.optString("nome") + "?")
                        .setPositiveButton("Sim", (dialog, which) -> {
                            repository.excluirMorador(cod, new MoradorFirestoreRepository.OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(Void result) {
                                    runOnUiThread(() -> {
                                        Toast.makeText(ListaMoradoresActivity.this,
                                                "Morador excluído!", Toast.LENGTH_SHORT).show();
                                        carregarLista();
                                    });
                                }

                                @Override
                                public void onError(Exception e) {
                                    runOnUiThread(() -> Toast.makeText(ListaMoradoresActivity.this,
                                            "Erro ao excluir: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                                }
                            });
                        })
                        .setNegativeButton("Não", null)
                        .show();
            }
        });

        recyclerMoradores.setAdapter(adapter);

        carregarLista();
    }

    private void carregarLista() {
        repository.getListaMoradores(new MoradorFirestoreRepository.OnCompleteListener<List<JSONObject>>() {
            @Override
            public void onComplete(List<JSONObject> lista) {
                runOnUiThread(() -> {
                    moradores.clear();
                    moradores.addAll(lista);
                    adapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> Toast.makeText(ListaMoradoresActivity.this,
                        "Erro ao carregar lista: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarLista();
    }
}