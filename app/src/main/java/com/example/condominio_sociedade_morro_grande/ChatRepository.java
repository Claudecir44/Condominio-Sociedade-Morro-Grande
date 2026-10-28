package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Context;
import android.net.Uri;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat entre cada morador e a Administração (todos os admins respondem pela mesma conversa).
 * Mesmas funções do chat de admin do Caronas/Match: texto cifrado (ChatCrypto), foto, lida (✓✓),
 * contador de não lidas, apagar só pra mim / pra todos e apagar a conversa inteira.
 *
 * conversas/{moradorUid}: moradorUid, ultimaMensagem (cifrada), ultimoTimestamp, ultimoDeAdmin,
 *   naoLidasMorador (o que a Administração mandou e o morador não leu), naoLidasAdmin (o contrário).
 * conversas/{moradorUid}/mensagens/{id}: deAdmin, remetenteId, conteudo (cifrado), tipo,
 *   imagemUrl, timestamp, lida, deletadoParaTodos, deletadoParaMorador, deletadoParaAdmin.
 * Fotos no Storage em chat/{moradorUid}/.
 */
public class ChatRepository {

    public interface Ouvinte<T> { void mudou(T valor); }

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public static String meuUid() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return u != null ? u.getUid() : null;
    }

    private DocumentReference conversa(String moradorUid) {
        return db.collection("conversas").document(moradorUid);
    }

    /** Mensagens da conversa, mais antiga primeiro (já decifradas). */
    public ListenerRegistration escutarMensagens(String moradorUid, Ouvinte<List<MensagemChat>> ouvinte) {
        return conversa(moradorUid).collection("mensagens").orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, erro) -> {
                    if (snap == null) return;
                    List<MensagemChat> lista = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) lista.add(MensagemChat.de(d));
                    ouvinte.mudou(lista);
                });
    }

    /** Conversas (lado da Administração), mais recente primeiro. */
    public ListenerRegistration escutarConversas(Ouvinte<List<DocumentSnapshot>> ouvinte) {
        return db.collection("conversas").orderBy("ultimoTimestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, erro) -> {
                    if (snap == null) return;
                    ouvinte.mudou(new ArrayList<>(snap.getDocuments()));
                });
    }

    /** Selo do ícone do chat: não lidas do morador, ou a soma de todas as conversas pra Administração. */
    public ListenerRegistration escutarNaoLidas(boolean souAdmin, Ouvinte<Integer> ouvinte) {
        if (souAdmin) {
            return db.collection("conversas").whereGreaterThan("naoLidasAdmin", 0)
                    .addSnapshotListener((snap, erro) -> {
                        if (snap == null) return;
                        int total = 0;
                        for (QueryDocumentSnapshot d : snap) {
                            Long n = d.getLong("naoLidasAdmin");
                            if (n != null) total += n.intValue();
                        }
                        ouvinte.mudou(total);
                    });
        }
        String uid = meuUid();
        if (uid == null) return null;
        return conversa(uid).addSnapshotListener((doc, erro) -> {
            if (doc == null) return;
            Long n = doc.getLong("naoLidasMorador");
            ouvinte.mudou(n != null ? n.intValue() : 0);
        });
    }

    public void enviarTexto(String moradorUid, boolean souAdmin, String texto, ColecaoFirestore.Callback<Void> callback) {
        gravar(moradorUid, souAdmin, MensagemChat.TIPO_TEXTO, texto, null, texto, callback);
    }

    /** Sobe a foto pra chat/{moradorUid}/ e manda como mensagem. */
    public void enviarImagem(Context context, String moradorUid, boolean souAdmin, Uri foto,
                             ColecaoFirestore.Callback<Void> callback) {
        String nome = System.currentTimeMillis() + "_" + ArquivosStorage.nomeDoAnexo(context, foto.toString());
        ArquivosStorage.enviarNaPasta(context, "chat", moradorUid, nome, foto, new ColecaoFirestore.Callback<String>() {
            @Override
            public void onSucesso(String link) {
                gravar(moradorUid, souAdmin, MensagemChat.TIPO_IMAGEM, "", link, "📷 Foto", callback);
            }

            @Override
            public void onErro(Exception e) {
                callback.onErro(e);
            }
        });
    }

    private void gravar(String moradorUid, boolean souAdmin, String tipo, String texto, String imagemUrl,
                        String resumo, ColecaoFirestore.Callback<Void> callback) {
        DocumentReference conv = conversa(moradorUid);
        DocumentReference msg = conv.collection("mensagens").document();

        Map<String, Object> m = new HashMap<>();
        m.put("deAdmin", souAdmin);
        m.put("remetenteId", meuUid());
        m.put("conteudo", ChatCrypto.cifrar(texto));
        m.put("tipo", tipo);
        if (imagemUrl != null) m.put("imagemUrl", imagemUrl);
        m.put("timestamp", FieldValue.serverTimestamp());
        m.put("lida", false);
        m.put("deletadoParaTodos", false);
        m.put("deletadoParaMorador", false);
        m.put("deletadoParaAdmin", false);

        Map<String, Object> c = new HashMap<>();
        c.put("moradorUid", moradorUid);
        c.put("ultimaMensagem", ChatCrypto.cifrar(resumo));
        c.put("ultimoTimestamp", FieldValue.serverTimestamp());
        c.put("ultimoDeAdmin", souAdmin);
        c.put(souAdmin ? "naoLidasMorador" : "naoLidasAdmin", FieldValue.increment(1));

        WriteBatch batch = db.batch();
        batch.set(msg, m);
        batch.set(conv, c, SetOptions.merge());
        batch.commit()
                .addOnSuccessListener(v -> callback.onSucesso(null))
                .addOnFailureListener(callback::onErro);
    }

    /** Abriu a conversa: zera o contador do meu lado e marca como lidas as mensagens do outro lado. */
    public void marcarComoLida(String moradorUid, boolean souAdmin) {
        DocumentReference conv = conversa(moradorUid);
        String campo = souAdmin ? "naoLidasAdmin" : "naoLidasMorador";
        conv.get().addOnSuccessListener(doc -> {
            Long n = doc.getLong(campo);
            if (doc.exists() && n != null && n > 0) conv.update(campo, 0);
        });
        conv.collection("mensagens").whereEqualTo("deAdmin", !souAdmin).whereEqualTo("lida", false).get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) return;
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot d : snap.getDocuments()) batch.update(d.getReference(), "lida", true);
                    batch.commit();
                });
    }

    /** "Apagar só pra mim": a mensagem continua pro outro lado. */
    public void apagarParaMim(String moradorUid, MensagemChat m, boolean souAdmin, ColecaoFirestore.Callback<Void> callback) {
        conversa(moradorUid).collection("mensagens").document(m.id)
                .update(souAdmin ? "deletadoParaAdmin" : "deletadoParaMorador", true)
                .addOnSuccessListener(v -> callback.onSucesso(null))
                .addOnFailureListener(callback::onErro);
    }

    /** "Apagar pra todos": só quem mandou; troca o conteúdo pelo aviso de mensagem apagada. */
    public void apagarParaTodos(String moradorUid, MensagemChat m, ColecaoFirestore.Callback<Void> callback) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("deletadoParaTodos", true);
        dados.put("conteudo", ChatCrypto.cifrar(MensagemChat.TEXTO_APAGADA));
        dados.put("tipo", MensagemChat.TIPO_TEXTO);
        dados.put("imagemUrl", FieldValue.delete());
        conversa(moradorUid).collection("mensagens").document(m.id).update(dados)
                .addOnSuccessListener(v -> callback.onSucesso(null))
                .addOnFailureListener(callback::onErro);
    }

    /** Apaga a conversa inteira (só a Administração): todas as mensagens e o documento da conversa. */
    public void apagarConversa(String moradorUid, ColecaoFirestore.Callback<Void> callback) {
        DocumentReference conv = conversa(moradorUid);
        conv.collection("mensagens").get()
                .addOnSuccessListener(snap -> {
                    WriteBatch batch = db.batch();
                    int n = 0;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        batch.delete(d.getReference());
                        // Limite do Firestore: 500 operações por lote
                        if (++n == 450) {
                            batch.commit();
                            batch = db.batch();
                            n = 0;
                        }
                    }
                    batch.delete(conv);
                    batch.commit()
                            .addOnSuccessListener(v -> callback.onSucesso(null))
                            .addOnFailureListener(callback::onErro);
                })
                .addOnFailureListener(callback::onErro);
    }

    /**
     * Como a Administração vê o morador na conversa: "Q:A L:01" e o primeiro nome de quem usa o
     * app — o inquilino, se houver (Inquilino.nomeResponsavel), senão o morador.
     */
    public static String[] rotuloMorador(Map<String, Object> morador) {
        String quadra = texto(morador, "quadra").toUpperCase();
        String lote = texto(morador, "lote");
        if (lote.matches("\\d{1,9}")) lote = String.format(java.util.Locale.ROOT, "%02d", Integer.parseInt(lote));
        String nome = Inquilino.nomeResponsavel(morador);
        String primeiro = nome.isEmpty() ? "Morador" : nome.split("\\s+")[0];
        return new String[]{"Q:" + (quadra.isEmpty() ? "-" : quadra) + " L:" + (lote.isEmpty() ? "-" : lote), primeiro};
    }

    private static String texto(Map<String, Object> m, String campo) {
        Object v = m != null ? m.get(campo) : null;
        return v != null ? v.toString().trim() : "";
    }
}
