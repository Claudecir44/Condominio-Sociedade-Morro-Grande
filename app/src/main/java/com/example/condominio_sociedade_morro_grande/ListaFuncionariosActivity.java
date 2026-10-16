package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.cjstudio.condominio_sociedade_morro_grande.data.FuncionarioDAO;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.cjstudio.condominio_sociedade_morro_grande.domain.model.Funcionario;

import java.util.List;

public class ListaFuncionariosActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private FuncionarioAdapter adapter;
    private FuncionarioDAO funcionarioDAO;
    private List<Funcionario> listaFuncionarios;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lista_funcionarios);

        recyclerView = findViewById(R.id.recyclerFuncionarios);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        funcionarioDAO = new FuncionarioDAO();
    }

    private void carregarLista() {
        funcionarioDAO.listarTodos(new ColecaoFirestore.Callback<List<Funcionario>>() {
            @Override
            public void onSucesso(List<Funcionario> lista) {
                listaFuncionarios = lista;
                mostrarLista();
            }

            @Override
            public void onErro(Exception e) {
                Toast.makeText(ListaFuncionariosActivity.this,
                        "Erro ao carregar funcionários: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void mostrarLista() {
        adapter = new FuncionarioAdapter(listaFuncionarios, new FuncionarioAdapter.OnItemClickListener() {
            @Override
            public void onEditar(Funcionario funcionario, int position) {
                Intent intent = new Intent(ListaFuncionariosActivity.this, CadastroFuncionarioActivity.class);
                intent.putExtra("funcionarioJSON", funcionarioDAO.paraTexto(funcionario));
                startActivity(intent);
            }

            @Override
            public void onExcluir(Funcionario funcionario, int position) {
                new AlertDialog.Builder(ListaFuncionariosActivity.this)
                        .setTitle("Confirmação")
                        .setMessage("Deseja realmente excluir o funcionário " + funcionario.getNome() + "?")
                        .setPositiveButton("Sim", (dialog, which) ->
                                funcionarioDAO.excluir(funcionario.getId(), new ColecaoFirestore.Callback<Void>() {
                                    @Override
                                    public void onSucesso(Void resultado) {
                                        Toast.makeText(ListaFuncionariosActivity.this,
                                                "Funcionário excluído!", Toast.LENGTH_SHORT).show();
                                        carregarLista();
                                    }

                                    @Override
                                    public void onErro(Exception e) {
                                        Toast.makeText(ListaFuncionariosActivity.this,
                                                "Erro ao excluir: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                    }
                                }))
                        .setNegativeButton("Não", null)
                        .show();
            }
        });

        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarLista();
    }
}