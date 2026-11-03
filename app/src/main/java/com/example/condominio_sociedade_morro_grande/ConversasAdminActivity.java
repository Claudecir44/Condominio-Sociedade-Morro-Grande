package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Chat admin": conversas privadas entre administradores (como o Chat Admin do Match). "Nova
 * conversa" escolhe o administrador; toque e segure apaga a conversa.
 */
public class ConversasAdminActivity extends AppCompatActivity {

    private final ChatAdminRepository repository = new ChatAdminRepository();
    private final List<DocumentSnapshot> conversas = new ArrayList<>();
    // Nome e foto atuais de cada administrador, pelo UID
    private final Map<String, DocumentSnapshot> admins = new HashMap<>();
    private final String meuUid = ChatRepository.meuUid();
    private ListenerRegistration ouvinte;
    private Adapter adapter;
    private TextView tvVazio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversas);
        if (meuUid == null) {
            finish();
            return;
        }
        TempoReal.observar(this, this::carregarAdmins, FirebaseFirestore.getInstance().collection("administradores"));

        ((TextView) findViewById(R.id.textTituloConversas)).setText("Chat admin");
        ((TextView) findViewById(R.id.textDicaConversas)).setText(
                "Conversas privadas entre administradores.\nToque e segure para apagar uma conversa.");
        tvVazio = findViewById(R.id.tvConversasVazio);
        RecyclerView recycler = findViewById(R.id.recyclerConversas);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        recycler.setAdapter(adapter);
        findViewById(R.id.btnNovaConversa).setOnClickListener(v -> escolherAdmin());
        carregarAdmins();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (meuUid == null) return;
        ouvinte = repository.escutarConversas(meuUid, lista -> {
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

    private void carregarAdmins() {
        FirebaseFirestore.getInstance().collection("administradores").get().addOnSuccessListener(snap -> {
            admins.clear();
            for (DocumentSnapshot d : snap.getDocuments()) admins.put(d.getId(), d);
            adapter.notifyDataSetChanged();
        });
    }

    private String nomeDe(String uid) {
        DocumentSnapshot admin = admins.get(uid);
        String nome = admin != null ? admin.getString("nome") : null;
        return nome != null && !nome.isEmpty() ? nome : "Administrador";
    }

    private void escolherAdmin() {
        List<String> uids = new ArrayList<>();
        for (String uid : admins.keySet()) if (!uid.equals(meuUid)) uids.add(uid);
        if (uids.isEmpty()) {
            Toast.makeText(this, "Nenhum outro administrador cadastrado.", Toast.LENGTH_SHORT).show();
            return;
        }
        uids.sort((a, b) -> nomeDe(a).compareToIgnoreCase(nomeDe(b)));
        String[] nomes = new String[uids.size()];
        for (int i = 0; i < uids.size(); i++) nomes[i] = nomeDe(uids.get(i));
        new AlertDialog.Builder(this)
                .setTitle("Conversar com")
                .setItems(nomes, (d, i) -> abrir(uids.get(i)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void abrir(String outroUid) {
        repository.abrir(meuUid, outroUid, new ColecaoFirestore.Callback<String>() {
            @Override
            public void onSucesso(String id) {
                startActivity(new Intent(ConversasAdminActivity.this, ChatAdminActivity.class)
                        .putExtra(ChatAdminActivity.EXTRA_CONVERSA, id)
                        .putExtra(ChatAdminActivity.EXTRA_OUTRO, outroUid)
                        .putExtra(ChatAdminActivity.EXTRA_NOME, nomeDe(outroUid)));
            }

            @Override
            public void onErro(Exception e) {
                Toast.makeText(ConversasAdminActivity.this, "Erro ao abrir a conversa: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void apagar(String conversaId, String nome) {
        new AlertDialog.Builder(this)
                .setTitle("Apagar conversa")
                .setMessage("Apagar toda a conversa com " + nome + "? As mensagens somem para os dois.")
                .setPositiveButton("Apagar", (d, w) -> repository.apagarConversa(conversaId, new ColecaoFirestore.Callback<Void>() {
                    @Override
                    public void onSucesso(Void v) {
                        Toast.makeText(ConversasAdminActivity.this, "Conversa apagada.", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onErro(Exception e) {
                        Toast.makeText(ConversasAdminActivity.this, "Erro ao apagar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            final android.widget.ImageView foto;
            final TextView titulo, nome, ultima, hora, naoLidas;

            VH(View v) {
                super(v);
                foto = v.findViewById(R.id.imgConversaFoto);
                titulo = v.findViewById(R.id.tvConversaQuadraLote);
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
            String outro = ChatAdminRepository.outroUid(c, meuUid);
            String nome = nomeDe(outro);
            h.titulo.setText(nome);
            h.nome.setVisibility(View.GONE);
            ArquivosStorage.carregarImagem(h.foto, null, android.R.drawable.ic_menu_camera);

            String ultima = ChatCrypto.decifrar(c.getString("ultimaMensagem"));
            boolean minha = meuUid.equals(c.getString("ultimoRemetenteId"));
            h.ultima.setText(ultima == null ? "" : (minha ? "Você: " : "") + ultima);

            Timestamp ts = c.getTimestamp("ultimoTimestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
            h.hora.setText(ts != null ? new SimpleDateFormat("dd/MM HH:mm", new Locale("pt", "BR")).format(ts.toDate()) : "");

            Long n = c.getLong(ChatAdminRepository.campoNaoLidas(c.getId(), meuUid));
            if (n != null && n > 0) {
                GradientDrawable fundo = new GradientDrawable();
                fundo.setColor(Color.parseColor("#DC2626"));
                fundo.setCornerRadius(ListaAnexos.dp(ConversasAdminActivity.this, 11));
                h.naoLidas.setBackground(fundo);
                h.naoLidas.setText(String.valueOf(n));
                h.naoLidas.setVisibility(View.VISIBLE);
            } else {
                h.naoLidas.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> abrir(outro));
            h.itemView.setOnLongClickListener(v -> {
                apagar(c.getId(), nome);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return conversas.size();
        }
    }
}
