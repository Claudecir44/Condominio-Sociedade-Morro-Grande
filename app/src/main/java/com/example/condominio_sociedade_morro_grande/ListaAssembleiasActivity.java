package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.cjstudio.condominio_sociedade_morro_grande.data.AssembleiaDAO;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.cjstudio.condominio_sociedade_morro_grande.AssembleiaAdapter;  // corrigido: pacote raiz
import com.cjstudio.condominio_sociedade_morro_grande.AnexosAdapter;      // corrigido: pacote raiz

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ListaAssembleiasActivity extends AppCompatActivity implements
        AssembleiaAdapter.OnVisualizarClickListener,
        AssembleiaAdapter.OnExcluirClickListener,
        AssembleiaAdapter.OnEditarClickListener {

    private RecyclerView recyclerView;
    private AssembleiaAdapter adapter;
    private AssembleiaDAO assembleiaDAO;
    private String tipoUsuario;

    private View panelVisualizacao;
    private TextView tvDataHora, tvAssunto, tvLocal, tvDescricao;
    private RecyclerView recyclerAnexos;
    private Button btnFecharVisualizacao, btnEditarVisualizacao, btnExcluirVisualizacao;
    private AnexosAdapter anexosAdapter;
    private List<Uri> listaAnexos = new ArrayList<>();

    private JSONObject assembleiaAtual;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_assembleias);

        tipoUsuario = getIntent().getStringExtra("tipo_usuario");
        if (tipoUsuario == null) {
            tipoUsuario = BuildConfig.PERFIL;
        }

        recyclerView = findViewById(R.id.recyclerAssembleias);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        panelVisualizacao = findViewById(R.id.panelVisualizacao);
        tvDataHora = findViewById(R.id.tvDataHoraVisualizacao);
        tvAssunto = findViewById(R.id.tvAssuntoVisualizacao);
        tvLocal = findViewById(R.id.tvLocalVisualizacao);
        tvDescricao = findViewById(R.id.tvDescricaoVisualizacao);
        recyclerAnexos = findViewById(R.id.recyclerAnexos);
        btnFecharVisualizacao = findViewById(R.id.btnFecharVisualizacao);
        btnEditarVisualizacao = findViewById(R.id.btnEditarVisualizacao);
        btnExcluirVisualizacao = findViewById(R.id.btnExcluirVisualizacao);

        if ("morador".equalsIgnoreCase(tipoUsuario)) {
            btnEditarVisualizacao.setVisibility(View.GONE);
            btnExcluirVisualizacao.setVisibility(View.GONE);
        }

        recyclerAnexos.setLayoutManager(new LinearLayoutManager(this));
        anexosAdapter = new AnexosAdapter(listaAnexos);
        recyclerAnexos.setAdapter(anexosAdapter);

        assembleiaDAO = new AssembleiaDAO();

        btnFecharVisualizacao.setOnClickListener(v -> {
            panelVisualizacao.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        });

        btnEditarVisualizacao.setOnClickListener(v -> {
            if (assembleiaAtual != null) {
                onEditar(assembleiaAtual);
            }
        });

        btnExcluirVisualizacao.setOnClickListener(v -> {
            if (assembleiaAtual != null) {
                excluirAssembleia(assembleiaAtual);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarAssembleias();
    }

    private void carregarAssembleias() {
        assembleiaDAO.listarAssembleias(new ColecaoFirestore.Callback<List<JSONObject>>() {
            @Override
            public void onSucesso(List<JSONObject> lista) {
                adapter = new AssembleiaAdapter(ListaAssembleiasActivity.this, lista,
                        ListaAssembleiasActivity.this, ListaAssembleiasActivity.this,
                        ListaAssembleiasActivity.this, tipoUsuario);
                recyclerView.setAdapter(adapter);
            }

            @Override
            public void onErro(Exception e) {
                Toast.makeText(ListaAssembleiasActivity.this,
                        "Erro ao carregar assembleias: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onVisualizar(JSONObject assembleia) {
        mostrarDetalhesAssembleia(assembleia);
    }

    @Override
    public void onExcluir(JSONObject assembleia) {
        excluirAssembleia(assembleia);
    }

    @Override
    public void onEditar(JSONObject assembleia) {
        try {
            Intent intent = new Intent(this, RegistroAssembleiasActivity.class);
            intent.putExtra("assembleiaJSON", assembleia.toString());
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Erro ao abrir assembleia para edição", Toast.LENGTH_SHORT).show();
        }
    }

    private void mostrarDetalhesAssembleia(JSONObject assembleia) {
        try {
            assembleiaAtual = assembleia;

            tvDataHora.setText(assembleia.optString("datahora", "Data/Hora não informada"));
            tvAssunto.setText(assembleia.optString("assunto", "Assunto não informado"));
            tvLocal.setText(assembleia.optString("local", "Local não informado"));
            tvDescricao.setText(assembleia.optString("descricao", "Descrição não informada"));

            listaAnexos.clear();
            listaAnexos.addAll(AssembleiaDAO.jsonParaAnexos(assembleia.optString("anexos", "[]")));
            anexosAdapter.notifyDataSetChanged();

            panelVisualizacao.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Erro ao carregar dados da assembleia", Toast.LENGTH_SHORT).show();
        }
    }

    private void excluirAssembleia(JSONObject assembleia) {
        try {
            String id = assembleia.getString("id");

            new AlertDialog.Builder(this)
                    .setTitle("Confirmar exclusão")
                    .setMessage("Deseja realmente excluir esta assembleia?")
                    .setPositiveButton("Sim", (dialog, which) ->
                            assembleiaDAO.excluirAssembleia(id, new ColecaoFirestore.Callback<Void>() {
                                @Override
                                public void onSucesso(Void resultado) {
                                    Toast.makeText(ListaAssembleiasActivity.this, "Assembleia excluída", Toast.LENGTH_SHORT).show();
                                    carregarAssembleias();
                                    panelVisualizacao.setVisibility(View.GONE);
                                    recyclerView.setVisibility(View.VISIBLE);
                                }

                                @Override
                                public void onErro(Exception e) {
                                    Toast.makeText(ListaAssembleiasActivity.this,
                                            "Erro ao excluir assembleia: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                }
                            }))
                    .setNegativeButton("Não", null)
                    .show();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Erro ao excluir assembleia", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (panelVisualizacao.getVisibility() == View.VISIBLE) {
            panelVisualizacao.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        } else {
            super.onBackPressed();
        }
    }
}