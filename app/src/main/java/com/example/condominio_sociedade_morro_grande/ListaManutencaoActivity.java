package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.cjstudio.condominio_sociedade_morro_grande.data.ManutencaoDAO;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.cjstudio.condominio_sociedade_morro_grande.domain.model.Manutencao;
import com.cjstudio.condominio_sociedade_morro_grande.ManutencaoAdapter; // Correção: pacote raiz

import java.util.List;

public class ListaManutencaoActivity extends AppCompatActivity {

    private RecyclerView recyclerViewManutencao;
    private ManutencaoDAO manutencaoDAO;
    private List<Manutencao> listaManutencoes;
    private ManutencaoAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_manutencao);

        recyclerViewManutencao = findViewById(R.id.recyclerViewManutencao);
        recyclerViewManutencao.setLayoutManager(new LinearLayoutManager(this));

        manutencaoDAO = new ManutencaoDAO();
    }

    private void carregarLista() {
        manutencaoDAO.getTodasManutencoes(new ColecaoFirestore.Callback<List<Manutencao>>() {
            @Override
            public void onSucesso(List<Manutencao> lista) {
                listaManutencoes = lista;
                mostrarLista();
            }

            @Override
            public void onErro(Exception e) {
                Toast.makeText(ListaManutencaoActivity.this,
                        "Erro ao carregar manutenções: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void mostrarLista() {
        if (listaManutencoes.isEmpty()) {
            Toast.makeText(this, "Nenhuma manutenção cadastrada", Toast.LENGTH_SHORT).show();
        }

        adapter = new ManutencaoAdapter(this, listaManutencoes, new ManutencaoAdapter.OnItemClickListener() {
            @Override
            public void onEditar(int position) {
                Intent intent = new Intent(ListaManutencaoActivity.this, com.cjstudio.condominio_sociedade_morro_grande.CadastroManutencaoActivity.class);
                intent.putExtra("manutencaoJSON", manutencaoDAO.paraTexto(listaManutencoes.get(position)));
                startActivity(intent);
            }

            @Override
            public void onExcluir(int position) {
                confirmarExclusao(listaManutencoes.get(position), position);
            }
        });

        recyclerViewManutencao.setAdapter(adapter);
    }

    private void confirmarExclusao(final Manutencao manutencao, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Excluir Manutenção");
        builder.setMessage("Deseja realmente excluir esta manutenção?");
        builder.setPositiveButton("Sim", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                manutencaoDAO.excluirManutencao(manutencao.getId(), new ColecaoFirestore.Callback<Void>() {
                    @Override
                    public void onSucesso(Void resultado) {
                        Toast.makeText(ListaManutencaoActivity.this, "Manutenção excluída!", Toast.LENGTH_SHORT).show();
                        carregarLista();
                    }

                    @Override
                    public void onErro(Exception e) {
                        Toast.makeText(ListaManutencaoActivity.this,
                                "Erro ao excluir manutenção: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
        builder.setNegativeButton("Não", null);
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarLista();
    }
}