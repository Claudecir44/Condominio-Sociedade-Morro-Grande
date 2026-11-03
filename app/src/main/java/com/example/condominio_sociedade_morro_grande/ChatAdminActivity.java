package com.cjstudio.condominio_sociedade_morro_grande;

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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Conversa privada entre dois administradores: só texto, ✓/✓✓, toque e segure pra apagar. */
public class ChatAdminActivity extends AppCompatActivity {

    public static final String EXTRA_CONVERSA = "conversaId";
    public static final String EXTRA_OUTRO = "outroUid";
    public static final String EXTRA_NOME = "outroNome";

    private final ChatAdminRepository repository = new ChatAdminRepository();
    private final List<DocumentSnapshot> mensagens = new ArrayList<>();
    private final String meuUid = ChatRepository.meuUid();
    private String conversaId;
    private String outroUid;
    private ListenerRegistration ouvinte;
    private Adapter adapter;
    private RecyclerView recycler;
    private TextView tvVazio;
    private EditText etMensagem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        conversaId = getIntent().getStringExtra(EXTRA_CONVERSA);
        outroUid = getIntent().getStringExtra(EXTRA_OUTRO);
        if (conversaId == null || outroUid == null || meuUid == null) {
            finish();
            return;
        }
        ((TextView) findViewById(R.id.tvChatTitulo)).setText(getIntent().getStringExtra(EXTRA_NOME));
        ((TextView) findViewById(R.id.tvChatSubtitulo)).setText("Administrador");
        findViewById(R.id.btnChatFoto).setVisibility(View.GONE);

        recycler = findViewById(R.id.recyclerMensagens);
        tvVazio = findViewById(R.id.tvChatVazio);
        etMensagem = findViewById(R.id.etChatMensagem);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        recycler.setLayoutManager(lm);
        adapter = new Adapter();
        recycler.setAdapter(adapter);
        findViewById(R.id.btnChatEnviar).setOnClickListener(v -> enviar());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (conversaId == null) return;
        ouvinte = repository.escutarMensagens(conversaId, lista -> {
            mensagens.clear();
            for (DocumentSnapshot m : lista) if (!ocultaParaMim(m)) mensagens.add(m);
            adapter.notifyDataSetChanged();
            tvVazio.setVisibility(mensagens.isEmpty() ? View.VISIBLE : View.GONE);
            if (!mensagens.isEmpty()) recycler.scrollToPosition(mensagens.size() - 1);
            repository.marcarComoLida(conversaId, meuUid);
        });
    }

    @Override
    protected void onStop() {
        if (ouvinte != null) ouvinte.remove();
        super.onStop();
    }

    private boolean minha(DocumentSnapshot m) {
        return meuUid.equals(m.getString("remetenteId"));
    }

    private boolean ocultaParaMim(DocumentSnapshot m) {
        return Boolean.TRUE.equals(m.getBoolean(minha(m) ? "deletadoParaRemetente" : "deletadoParaDestinatario"));
    }

    private void enviar() {
        String texto = etMensagem.getText().toString().trim();
        if (texto.isEmpty()) return;
        etMensagem.setText("");
        repository.enviar(conversaId, meuUid, outroUid, texto, aoTerminar("Não foi possível enviar: "));
    }

    private ColecaoFirestore.Callback<Void> aoTerminar(String erro) {
        return new ColecaoFirestore.Callback<Void>() {
            @Override
            public void onSucesso(Void v) { }

            @Override
            public void onErro(Exception e) {
                Toast.makeText(ChatAdminActivity.this, erro + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        };
    }

    private void opcoes(DocumentSnapshot m) {
        boolean paraTodos = minha(m) && !Boolean.TRUE.equals(m.getBoolean("deletadoParaTodos"));
        String[] itens = paraTodos
                ? new String[]{"Apagar só pra mim", "Apagar pra todos"}
                : new String[]{"Apagar só pra mim"};
        new AlertDialog.Builder(this)
                .setTitle("Apagar mensagem")
                .setItems(itens, (d, qual) -> {
                    if (qual == 0) repository.apagarParaMim(m, meuUid, aoTerminar("Não foi possível apagar: "));
                    else repository.apagarParaTodos(m, aoTerminar("Não foi possível apagar: "));
                })
                .setNegativeButton("Cancelar", null)
                .show();
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
            DocumentSnapshot m = mensagens.get(position);
            boolean minha = minha(m);
            boolean lida = Boolean.TRUE.equals(m.getBoolean("lida"));
            boolean apagada = Boolean.TRUE.equals(m.getBoolean("deletadoParaTodos"));
            h.linha.setGravity(minha ? Gravity.END : Gravity.START);

            GradientDrawable fundo = new GradientDrawable();
            fundo.setCornerRadius(ListaAnexos.dp(ChatAdminActivity.this, 14));
            fundo.setColor(Color.parseColor(minha ? "#DBEAFE" : "#FFFFFF"));
            h.balao.setBackground(fundo);

            h.img.setVisibility(View.GONE);
            h.texto.setText(ChatCrypto.decifrar(m.getString("conteudo")));
            h.texto.setTextColor(Color.parseColor(apagada ? "#9CA3AF" : "#1F2937"));

            Timestamp ts = m.getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
            String hora = ts != null ? new SimpleDateFormat("dd/MM HH:mm", new Locale("pt", "BR")).format(ts.toDate()) : "";
            h.hora.setText(hora + (minha ? (lida ? "  ✓✓" : "  ✓") : ""));
            h.hora.setTextColor(Color.parseColor(minha && lida ? "#2563EB" : "#6B7280"));

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
