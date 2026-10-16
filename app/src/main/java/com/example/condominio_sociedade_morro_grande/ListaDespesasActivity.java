package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.cjstudio.condominio_sociedade_morro_grande.data.DespesaDAO;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.cjstudio.condominio_sociedade_morro_grande.domain.model.Despesa;

import java.util.List;

public class ListaDespesasActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private com.cjstudio.condominio_sociedade_morro_grande.DespesaAdapter adapter;
    private DespesaDAO despesaDAO;
    private List<Despesa> listaDespesas;
    private String tipoUsuario;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_despesas);

        tipoUsuario = getIntent().getStringExtra("tipo_usuario");
        if (tipoUsuario == null) {
            tipoUsuario = BuildConfig.PERFIL;
        }

        recyclerView = findViewById(R.id.recyclerViewDespesas);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        despesaDAO = new DespesaDAO();
    }

    private void carregarLista() {
        despesaDAO.listarTodos(new ColecaoFirestore.Callback<List<Despesa>>() {
            @Override
            public void onSucesso(List<Despesa> lista) {
                listaDespesas = lista;
                mostrarLista();
            }

            @Override
            public void onErro(Exception e) {
                Toast.makeText(ListaDespesasActivity.this,
                        "Erro ao carregar despesas: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void mostrarLista() {
        adapter = new com.cjstudio.condominio_sociedade_morro_grande.DespesaAdapter(this, listaDespesas, new com.cjstudio.condominio_sociedade_morro_grande.DespesaAdapter.OnDespesaListener() {
            @Override
            public void onEditar(Despesa despesa, int position) {
                if ("morador".equalsIgnoreCase(tipoUsuario)) {
                    Toast.makeText(ListaDespesasActivity.this, "Acesso negado. Apenas administradores podem editar despesas.", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent(ListaDespesasActivity.this, com.cjstudio.condominio_sociedade_morro_grande.RegistroDespesasActivity.class);
                intent.putExtra("despesaJSON", despesaDAO.paraTexto(despesa));
                startActivity(intent);
            }

            @Override
            public void onExcluir(Despesa despesa, int position) {
                if ("morador".equalsIgnoreCase(tipoUsuario)) {
                    Toast.makeText(ListaDespesasActivity.this, "Acesso negado. Apenas administradores podem excluir despesas.", Toast.LENGTH_SHORT).show();
                    return;
                }

                new AlertDialog.Builder(ListaDespesasActivity.this)
                        .setTitle("Confirmação")
                        .setMessage("Deseja realmente excluir esta despesa?")
                        .setPositiveButton("Sim", (dialog, which) ->
                                despesaDAO.excluir(despesa.getId(), new ColecaoFirestore.Callback<Void>() {
                                    @Override
                                    public void onSucesso(Void resultado) {
                                        Toast.makeText(ListaDespesasActivity.this, "Despesa excluída!", Toast.LENGTH_SHORT).show();
                                        carregarLista();
                                    }

                                    @Override
                                    public void onErro(Exception e) {
                                        Toast.makeText(ListaDespesasActivity.this,
                                                "Erro ao excluir: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                }))
                        .setNegativeButton("Não", null)
                        .show();
            }
        }, tipoUsuario);

        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarLista();
    }
}