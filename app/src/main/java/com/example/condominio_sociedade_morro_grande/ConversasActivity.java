package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Conversas da Administração com os moradores (app Admins). Cada linha: "Q:A L:01", primeiro
 * nome, última mensagem e não lidas. "Nova conversa" escolhe o morador (por quadra e lote);
 * toque e segure apaga a conversa inteira.
 */
public class ConversasActivity extends AppCompatActivity {

    private final ChatRepository repository = new ChatRepository();
    private final List<DocumentSnapshot> conversas = new ArrayList<>();
    // Cadastro de cada morador (pelo UID) pra montar "Q:A L:01" e o nome sempre atualizados
    private final Map<String, Map<String, Object>> moradores = new HashMap<>();
    private ListenerRegistration ouvinte;
    private Adapter adapter;
    private TextView tvVazio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversas);

        tvVazio = findViewById(R.id.tvConversasVazio);
        RecyclerView recycler = findViewById(R.id.recyclerConversas);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        recycler.setAdapter(adapter);

        findViewById(R.id.btnNovaConversa).setOnClickListener(v -> escolherMorador());
        carregarMoradores();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ouvinte = repository.escutarConversas(lista -> {
            conversas.clear();
            conversas.addAll(lista);
            adapter.notifyDataSetChanged();
            tvVazio.setVisibility(conversas.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    protected void onStop() {
        if (ouvinte != null) ouvinte.remove();
        super.onStop();
    }

    private void carregarMoradores() {
        FirebaseFirestore.getInstance().collection("moradores").get().addOnSuccessListener(snap -> {
            moradores.clear();
            for (DocumentSnapshot d : snap.getDocuments()) {
                // A conta master não é morador de verdade
                if (Boolean.TRUE.equals(d.getBoolean("verTodos")) || d.getData() == null) continue;
                moradores.put(d.getId(), d.getData());
            }
            adapter.notifyDataSetChanged();
        });
    }

    /** Lista os moradores por quadra e lote pra abrir (ou começar) a conversa. */
    private void escolherMorador() {
        if (moradores.isEmpty()) {
            Toast.makeText(this, "Carregando moradores…", Toast.LENGTH_SHORT).show();
            carregarMoradores();
            return;
        }
        List<JSONObject> lista = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : moradores.entrySet()) {
            JSONObject o = new JSONObject(e.getValue());
            try { o.put("cod", e.getKey()); } catch (Exception ignored) { }
            lista.add(o);
        }
        MoradorAdapter.ordenarPorQuadraELote(lista);
        String[] nomes = new String[lista.size()];
        for (int i = 0; i < lista.size(); i++) {
            String[] r = ChatRepository.rotuloMorador(moradores.get(lista.get(i).optString("cod")));
            nomes[i] = r[0] + " — " + r[1];
        }
        new AlertDialog.Builder(this)
                .setTitle("Conversar com")
                .setItems(nomes, (d, i) -> abrir(lista.get(i).optString("cod")))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void abrir(String moradorUid) {
        startActivity(new Intent(this, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_MORADOR_UID, moradorUid)
                .putExtra(ChatActivity.EXTRA_ADMINISTRACAO, true));
    }

    private void apagar(String moradorUid, String rotulo) {
        new AlertDialog.Builder(this)
                .setTitle("Apagar conversa")
                .setMessage("Apagar toda a conversa com " + rotulo + "? As mensagens somem para todos.")
                .setPositiveButton("Apagar", (d, w) -> repository.apagarConversa(moradorUid, new ColecaoFirestore.Callback<Void>() {
                    @Override
                    public void onSucesso(Void v) {
                        Toast.makeText(ConversasActivity.this, "Conversa apagada.", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onErro(Exception e) {
                        Toast.makeText(ConversasActivity.this, "Erro ao apagar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            final ImageView foto;
            final TextView quadraLote, nome, ultima, hora, naoLidas;

            VH(View v) {
                super(v);
                foto = v.findViewById(R.id.imgConversaFoto);
                quadraLote = v.findViewById(R.id.tvConversaQuadraLote);
                nome = v.findViewById(R.id.tvConversaNome);
                ultima = v.findViewById(R.id.tvConversaUltima);
                hora = v.findViewById(R.id.tvConversaHora);
                naoLidas = v.findViewById(R.id.tvConversaNaoLidas);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversa, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            DocumentSnapshot c = conversas.get(position);
            String uid = c.getId();
            Map<String, Object> morador = moradores.get(uid);
            String[] rotulo = ChatRepository.rotuloMorador(morador);
            h.quadraLote.setText(rotulo[0]);
            h.nome.setText(morador == null && !moradores.isEmpty() ? "Morador excluído" : rotulo[1]);
            Object foto = morador != null ? morador.get("foto") : null;
            ArquivosStorage.carregarImagem(h.foto, foto != null ? foto.toString() : null, android.R.drawable.ic_menu_camera);

            String ultima = ChatCrypto.decifrar(c.getString("ultimaMensagem"));
            boolean deAdmin = Boolean.TRUE.equals(c.getBoolean("ultimoDeAdmin"));
            h.ultima.setText(ultima == null ? "" : (deAdmin ? "Você: " : "") + ultima);

            Timestamp ts = c.getTimestamp("ultimoTimestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
            h.hora.setText(ts != null ? new SimpleDateFormat("dd/MM HH:mm", new Locale("pt", "BR")).format(ts.toDate()) : "");

            Long n = c.getLong("naoLidasAdmin");
            if (n != null && n > 0) {
                GradientDrawable fundo = new GradientDrawable();
                fundo.setColor(Color.parseColor("#DC2626"));
                fundo.setCornerRadius(ListaAnexos.dp(ConversasActivity.this, 11));
                h.naoLidas.setBackground(fundo);
                h.naoLidas.setText(String.valueOf(n));
                h.naoLidas.setVisibility(View.VISIBLE);
            } else {
                h.naoLidas.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> abrir(uid));
            h.itemView.setOnLongClickListener(v -> {
                apagar(uid, rotulo[0] + " " + rotulo[1]);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return conversas.size();
        }
    }
}
