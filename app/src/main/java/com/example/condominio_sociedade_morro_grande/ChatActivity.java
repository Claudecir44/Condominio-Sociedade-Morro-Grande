package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Conversa entre um morador e a Administração. O morador vê "Administração"; os admins veem
 * "Q:A L:01" e o primeiro nome do morador (ou do responsável). Texto e foto, ✓/✓✓ de leitura,
 * toque e segure pra apagar (só pra mim / pra todos), foto abre em tela cheia.
 * Extras: EXTRA_MORADOR_UID (lado da Administração); o morador abre a própria conversa.
 */
public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_MORADOR_UID = "moradorUid";
    /** Aberta pela lista de conversas: fala como "Administração" (admins e conta master). */
    public static final String EXTRA_ADMINISTRACAO = "administracao";

    private final ChatRepository repository = new ChatRepository();
    private final List<MensagemChat> mensagens = new ArrayList<>();
    private ListenerRegistration ouvinte;
    private boolean souAdmin;
    private String moradorUid;
    private Adapter adapter;
    private RecyclerView recycler;
    private TextView tvVazio;
    private EditText etMensagem;
    private View btnEnviar;
    private ActivityResultLauncher<PickVisualMediaRequest> escolherFoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        souAdmin = "admin".equals(BuildConfig.PERFIL) || getIntent().getBooleanExtra(EXTRA_ADMINISTRACAO, false);
        moradorUid = souAdmin ? getIntent().getStringExtra(EXTRA_MORADOR_UID) : ChatRepository.meuUid();
        if (moradorUid == null) {
            finish();
            return;
        }

        recycler = findViewById(R.id.recyclerMensagens);
        tvVazio = findViewById(R.id.tvChatVazio);
        etMensagem = findViewById(R.id.etChatMensagem);
        btnEnviar = findViewById(R.id.btnChatEnviar);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        recycler.setLayoutManager(lm);
        adapter = new Adapter();
        recycler.setAdapter(adapter);

        if (souAdmin) carregarCabecalhoDoMorador();

        btnEnviar.setOnClickListener(v -> enviarTexto());
        escolherFoto = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri == null) return;
            Toast.makeText(this, "Enviando foto…", Toast.LENGTH_SHORT).show();
            repository.enviarImagem(this, moradorUid, souAdmin, uri, aoEnviar());
        });
        findViewById(R.id.btnChatFoto).setOnClickListener(v -> escolherFoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
    }

    @Override
    protected void onStart() {
        super.onStart();
        ouvinte = repository.escutarMensagens(moradorUid, lista -> {
            mensagens.clear();
            for (MensagemChat m : lista) if (!m.ocultaPara(souAdmin)) mensagens.add(m);
            adapter.notifyDataSetChanged();
            tvVazio.setVisibility(mensagens.isEmpty() ? View.VISIBLE : View.GONE);
            if (!mensagens.isEmpty()) recycler.scrollToPosition(mensagens.size() - 1);
            // Conversa aberta: o que chegou já conta como lido
            repository.marcarComoLida(moradorUid, souAdmin);
        });
    }

    @Override
    protected void onStop() {
        if (ouvinte != null) ouvinte.remove();
        super.onStop();
    }

    /** Admin: título "Q:A L:01", subtítulo com o primeiro nome, foto do morador. */
    private void carregarCabecalhoDoMorador() {
        FirebaseFirestore.getInstance().collection("moradores").document(moradorUid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.getData() == null) return;
                    String[] rotulo = ChatRepository.rotuloMorador(doc.getData());
                    ((TextView) findViewById(R.id.tvChatTitulo)).setText(rotulo[0]);
                    ((TextView) findViewById(R.id.tvChatSubtitulo)).setText(rotulo[1]);
                    String foto = doc.getString("foto");
                    if (foto != null && !foto.isEmpty()) {
                        ImageView img = findViewById(R.id.imgChatContato);
                        img.setPadding(0, 0, 0, 0);
                        ArquivosStorage.carregarImagem(img, foto, R.drawable.ic_chat);
                    }
                });
    }

    private void enviarTexto() {
        String texto = etMensagem.getText().toString().trim();
        if (texto.isEmpty()) return;
        etMensagem.setText("");
        repository.enviarTexto(moradorUid, souAdmin, texto, aoEnviar());
    }

    private ColecaoFirestore.Callback<Void> aoEnviar() {
        return new ColecaoFirestore.Callback<Void>() {
            @Override
            public void onSucesso(Void v) { }

            @Override
            public void onErro(Exception e) {
                Toast.makeText(ChatActivity.this, "Não foi possível enviar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        };
    }

    private void opcoes(MensagemChat m) {
        boolean podeParaTodos = m.minha(souAdmin) && !m.deletadoParaTodos;
        String[] itens = podeParaTodos
                ? new String[]{"Apagar só pra mim", "Apagar pra todos"}
                : new String[]{"Apagar só pra mim"};
        new AlertDialog.Builder(this)
                .setTitle("Apagar mensagem")
                .setItems(itens, (d, qual) -> {
                    if (qual == 0) repository.apagarParaMim(moradorUid, m, souAdmin, aoApagar());
                    else repository.apagarParaTodos(moradorUid, m, aoApagar());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private ColecaoFirestore.Callback<Void> aoApagar() {
        return new ColecaoFirestore.Callback<Void>() {
            @Override
            public void onSucesso(Void v) { }

            @Override
            public void onErro(Exception e) {
                Toast.makeText(ChatActivity.this, "Não foi possível apagar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        };
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            final LinearLayout linha, balao;
            final ImageView img;
            final TextView texto, hora;

            VH(View v) {
                super(v);
                linha = v.findViewById(R.id.linhaMensagem);
                balao = v.findViewById(R.id.balaoMensagem);
                img = v.findViewById(R.id.imgMensagem);
                texto = v.findViewById(R.id.tvMensagemTexto);
                hora = v.findViewById(R.id.tvMensagemHora);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mensagem_chat, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            MensagemChat m = mensagens.get(position);
            boolean minha = m.minha(souAdmin);
            h.linha.setGravity(minha ? Gravity.END : Gravity.START);

            GradientDrawable fundo = new GradientDrawable();
            fundo.setCornerRadius(ListaAnexos.dp(ChatActivity.this, 14));
            fundo.setColor(Color.parseColor(minha ? "#DBEAFE" : "#FFFFFF"));
            h.balao.setBackground(fundo);

            if (m.ehImagem()) {
                h.img.setVisibility(View.VISIBLE);
                ArquivosStorage.carregarImagem(h.img, m.imagemUrl, android.R.drawable.ic_menu_gallery);
                h.img.setOnClickListener(v -> startActivity(new Intent(ChatActivity.this, ImageViewerActivity.class)
                        .putExtra("imagePath", m.imagemUrl)));
                h.texto.setVisibility(View.GONE);
            } else {
                h.img.setVisibility(View.GONE);
                h.img.setOnClickListener(null);
                h.texto.setVisibility(View.VISIBLE);
                h.texto.setText(m.texto);
                h.texto.setTextColor(Color.parseColor(m.deletadoParaTodos ? "#9CA3AF" : "#1F2937"));
            }

            // ✓ enviada, ✓✓ lida (só nas minhas)
            String status = minha ? (m.lida ? "  ✓✓" : "  ✓") : "";
            h.hora.setText(m.hora() + status);
            h.hora.setTextColor(Color.parseColor(minha && m.lida ? "#2563EB" : "#6B7280"));

            h.balao.setOnLongClickListener(v -> {
                opcoes(m);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mensagens.size();
        }
    }
}
