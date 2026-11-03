package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Mês a mês de um módulo, como em "Boletos Morador": cada mês abre e mostra seus registros.
 * Em Cadastro cada mês tem "Adicionar" e, embaixo, "Criar próximo mês". Em Listas (EXTRA_SO_VER)
 * é só consulta.
 */
public class MesesCadastroActivity extends AppCompatActivity {

    public static final String EXTRA_MODULO = "modulo";
    public static final String EXTRA_SO_VER = "so_ver";

    public enum Modulo {
        ASSEMBLEIAS("assembleias", "Assembleias", "datahora", "assunto", RegistroAssembleiasActivity.class, ListaAssembleiasActivity.class),
        DESPESAS("despesas", "Despesas", "dataHora", "nome", RegistroDespesasActivity.class, ListaDespesasActivity.class),
        AVISOS("avisos", "Avisos", "datahora", "assunto", CadastroAvisosActivity.class, ListaAvisosActivity.class),
        OCORRENCIAS("ocorrencias", "Ocorrências", "datahora", "tipo", RegistroOcorrenciasActivity.class, ListaOcorrenciasActivity.class),
        MANUTENCAO("manutencoes", "Manutenção", "dataHora", "tipo", CadastroManutencaoActivity.class, ListaManutencaoActivity.class);

        final String colecao, titulo, campoData, campoTitulo;
        final Class<?> formulario, lista;

        Modulo(String colecao, String titulo, String campoData, String campoTitulo, Class<?> formulario, Class<?> lista) {
            this.colecao = colecao;
            this.titulo = titulo;
            this.campoData = campoData;
            this.campoTitulo = campoTitulo;
            this.formulario = formulario;
            this.lista = lista;
        }
    }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Set<String> mesesAbertos = new HashSet<>();
    private Modulo modulo;
    private boolean soVer;
    private LinearLayout layoutMeses;
    private Button btnCriar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meses_cadastro);

        modulo = Modulo.valueOf(getIntent().getStringExtra(EXTRA_MODULO));
        soVer = getIntent().getBooleanExtra(EXTRA_SO_VER, false);
        layoutMeses = findViewById(R.id.layoutMeses);
        btnCriar = findViewById(R.id.btnCriarMes);
        ((TextView) findViewById(R.id.textTituloMeses)).setText(modulo.titulo);
        if (soVer) btnCriar.setVisibility(View.GONE);

        TempoReal.observar(this, this::carregar, db.collection(modulo.colecao));
        TempoReal.observar(this, this::carregar, mesesCriados());
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregar();
    }

    private DocumentReference mesesCriados() {
        return db.collection("meses_cadastro").document(modulo.colecao);
    }

    private void carregar() {
        db.collection(modulo.colecao).get()
                .addOnSuccessListener(registros -> mesesCriados().get()
                        .addOnSuccessListener(criados -> mostrar(registros.getDocuments(), criados))
                        .addOnFailureListener(this::avisarErro))
                .addOnFailureListener(this::avisarErro);
    }

    private void avisarErro(Exception e) {
        Toast.makeText(this, "Erro ao carregar: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void mostrar(List<DocumentSnapshot> registros, DocumentSnapshot criados) {
        Map<String, List<DocumentSnapshot>> porMes = new TreeMap<>();
        porMes.put(Boleto.mesDe(new java.util.Date()), new ArrayList<>());
        Object lista = criados.get("meses");
        if (lista instanceof List) {
            for (Object mes : (List<?>) lista) porMes.put(String.valueOf(mes), new ArrayList<>());
        }
        for (DocumentSnapshot doc : registros) {
            String mes = MesCadastro.mesDe(doc.get("mes"), doc.get(modulo.campoData));
            if (!porMes.containsKey(mes)) porMes.put(mes, new ArrayList<>());
            porMes.get(mes).add(doc);
        }
        // Dentro do mês, em ordem de data
        for (List<DocumentSnapshot> doMes : porMes.values()) {
            doMes.sort((a, b) -> MesCadastro.chaveDeOrdem(a.get(modulo.campoData))
                    .compareTo(MesCadastro.chaveDeOrdem(b.get(modulo.campoData))));
        }

        layoutMeses.removeAllViews();
        String ultimo = null;
        for (Map.Entry<String, List<DocumentSnapshot>> entrada : porMes.entrySet()) {
            ultimo = entrada.getKey();
            LinearLayout grupo = new LinearLayout(this);
            grupo.setOrientation(LinearLayout.VERTICAL);
            for (DocumentSnapshot doc : entrada.getValue()) grupo.addView(linha(doc, ultimo));
            if (entrada.getValue().isEmpty()) grupo.addView(texto("Nenhum registro neste mês."));
            else grupo.addView(botao("Ver registros", v -> abrirLista(entrada.getKey())));
            if (!soVer) grupo.addView(botao("Adicionar", v -> adicionar(entrada.getKey())));
            layoutMeses.addView(ListaBoletos.tituloMes(this, Boleto.textoDoMes(ultimo), ultimo, grupo, mesesAbertos));
            layoutMeses.addView(grupo);
            layoutMeses.addView(ListaBoletos.separador(this));
        }

        String proximo = Boleto.proximoMes(ultimo);
        btnCriar.setText("Criar " + Boleto.textoDoMes(proximo));
        btnCriar.setOnClickListener(v -> criarMes(proximo));
    }

    private View linha(DocumentSnapshot doc, String mes) {
        String data = doc.get(modulo.campoData) != null ? String.valueOf(doc.get(modulo.campoData)) : "";
        String titulo = doc.get(modulo.campoTitulo) != null ? String.valueOf(doc.get(modulo.campoTitulo)) : "";
        TextView linha = texto(data.isEmpty() ? titulo : data + "  —  " + titulo);
        linha.setTextColor(Color.parseColor("#1F2937"));
        linha.setOnClickListener(v -> abrirLista(mes));
        return linha;
    }

    private TextView texto(String conteudo) {
        TextView texto = new TextView(this);
        texto.setText(conteudo);
        texto.setTextSize(14);
        texto.setTextColor(Color.parseColor("#6B7280"));
        int margem = ListaAnexos.dp(this, 6);
        texto.setPadding(0, margem, 0, margem);
        return texto;
    }

    private View botao(String texto, View.OnClickListener aoTocar) {
        MaterialButton botao = new MaterialButton(this);
        botao.setText(texto);
        botao.setAllCaps(false);
        botao.setTextSize(13);
        botao.setCornerRadius(ListaAnexos.dp(this, 14));
        botao.setBackgroundTintList(ColorStateList.valueOf(getColor(R.color.botaoSucesso)));
        botao.setOnClickListener(aoTocar);
        return botao;
    }

    private void abrirLista(String mes) {
        startActivity(new Intent(this, modulo.lista).putExtra(MesCadastro.EXTRA_MES, mes));
    }

    private void adicionar(String mes) {
        startActivity(new Intent(this, modulo.formulario).putExtra(MesCadastro.EXTRA_MES, mes));
    }

    private void criarMes(String mes) {
        mesesAbertos.add(mes);
        Map<String, Object> dados = new HashMap<>();
        dados.put("meses", FieldValue.arrayUnion(mes));
        mesesCriados().set(dados, SetOptions.merge())
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Mês criado.", Toast.LENGTH_SHORT).show();
                    carregar();
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Erro ao criar o mês: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
