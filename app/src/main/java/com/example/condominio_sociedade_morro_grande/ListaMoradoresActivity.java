package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.MoradorFirestoreRepository;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListaMoradoresActivity extends AppCompatActivity {

    private RecyclerView recyclerMoradores;
    private com.cjstudio.condominio_sociedade_morro_grande.MoradorAdapter adapter;
    private List<JSONObject> moradores = new ArrayList<>();
    private MoradorFirestoreRepository repository;
    // Quadro "Boletos Morador" do app Admins: cards compactos por quadra/lote, sem Editar/Excluir
    private boolean somenteLeitura;
    private final BoletosRepository boletosRepository = new BoletosRepository();
    private ActivityResultLauncher<String[]> escolherPdf;
    // UID do morador que vai receber o boleto escolhido no seletor de arquivos
    private String moradorDoBoleto;
    private String mesDoBoleto;

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

            @Override
            public void onAdicionarBoleto(JSONObject morador, String mes) {
                moradorDoBoleto = morador.optString("cod");
                mesDoBoleto = mes;
                escolherPdf.launch(new String[]{"application/pdf"});
            }

            @Override
            public void onCriarMes(JSONObject morador, String mes) {
                boletosRepository.criarMes(morador.optString("cod"), mes, aoTerminar("Mês criado."));
            }

            @Override
            public void onMarcarBoletoNaoPago(Boleto boleto, boolean naoPago) {
                boletosRepository.marcarNaoPago(boleto.getId(), naoPago, aoTerminar(
                        naoPago ? "Boleto marcado como não pago." : "Marcação de não pago removida."));
            }

            @Override
            public void onExcluirBoleto(Boleto boleto) {
                boletosRepository.excluir(boleto.getId(), aoTerminar("Boleto excluído."));
            }
        });

        escolherPdf = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri == null || moradorDoBoleto == null) return;
            Toast.makeText(this, "Enviando boleto…", Toast.LENGTH_SHORT).show();
            boletosRepository.postar(this, moradorDoBoleto, mesDoBoleto, uri, aoTerminar("Boleto postado!"));
        });

        somenteLeitura = getIntent().getBooleanExtra("somenteLeitura", false);
        if (somenteLeitura) {
            TempoReal.observar(this, this::carregarLista, com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("moradores"), com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("boletos"), com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("boletos_meses"));
        } else {
            TempoReal.observar(this, this::carregarLista, com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("moradores"));
        }
        adapter.setSomenteLeitura(somenteLeitura);
        recyclerMoradores.setAdapter(adapter);
        if (somenteLeitura) ((android.widget.TextView) findViewById(R.id.textTituloMoradores)).setText("Boletos Morador");

        carregarLista();
    }

    private void carregarLista() {
        repository.getListaMoradores(new MoradorFirestoreRepository.OnCompleteListener<List<JSONObject>>() {
            @Override
            public void onComplete(List<JSONObject> lista) {
                runOnUiThread(() -> {
                    moradores.clear();
                    moradores.addAll(lista);
                    // Nas duas listas: quadra em ordem alfabética, depois lote
                    MoradorAdapter.ordenarPorQuadraELote(moradores);
                    adapter.notifyDataSetChanged();
                    // Boletos só na lista "Boletos Morador"
                    if (somenteLeitura) carregarBoletos();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> Toast.makeText(ListaMoradoresActivity.this,
                        "Erro ao carregar lista: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void carregarBoletos() {
        boletosRepository.listarPorMorador(new ColecaoFirestore.Callback<Map<String, List<Boleto>>>() {
            @Override
            public void onSucesso(Map<String, List<Boleto>> porMorador) {
                adapter.setBoletos(porMorador);
            }

            @Override
            public void onErro(Exception e) {
                Toast.makeText(ListaMoradoresActivity.this,
                        "Erro ao carregar boletos: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        boletosRepository.listarMesesCriados(new ColecaoFirestore.Callback<Map<String, java.util.Set<String>>>() {
            @Override
            public void onSucesso(Map<String, java.util.Set<String>> meses) {
                adapter.setMesesCriados(meses);
            }

            @Override
            public void onErro(Exception e) {
                Toast.makeText(ListaMoradoresActivity.this,
                        "Erro ao carregar os meses: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Depois de postar/marcar/excluir boleto: avisa e recarrega os boletos. */
    private ColecaoFirestore.Callback<Void> aoTerminar(String mensagem) {
        return new ColecaoFirestore.Callback<Void>() {
            @Override
            public void onSucesso(Void nada) {
                Toast.makeText(ListaMoradoresActivity.this, mensagem, Toast.LENGTH_SHORT).show();
                carregarBoletos();
            }

            @Override
            public void onErro(Exception e) {
                Toast.makeText(ListaMoradoresActivity.this,
                        "Erro no boleto: " + e.getMessage(), Toast.LENGTH_LONG).show();
                carregarBoletos();
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregarLista();
    }
}