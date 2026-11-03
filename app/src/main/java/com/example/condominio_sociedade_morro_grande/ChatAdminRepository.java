package com.cjstudio.condominio_sociedade_morro_grande;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat privado entre dois administradores (mesmo modelo do Chat Admin do Match): só texto, cifrado
 * com ChatCrypto, com ✓/✓✓, não lidas e apagar só pra mim / pra todos.
 * conversasAdmin/{uidMenor_uidMaior}: participantes, ultimaMensagem, ultimoTimestamp,
 *   ultimoRemetenteId, naoLidas1 (do primeiro uid), naoLidas2 (do segundo).
 * conversasAdmin/{id}/mensagens/{id}: remetenteId, destinatarioId, conteudo, tipo, timestamp, lida,
 *   deletadoParaTodos, deletadoParaRemetente, deletadoParaDestinatario.
 */
public class ChatAdminRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private DocumentReference conversa(String id) {
        return db.collection("conversasAdmin").document(id);
    }

    public static String idConversa(String uidA, String uidB) {
        return uidA.compareTo(uidB) < 0 ? uidA + "_" + uidB : uidB + "_" + uidA;
    }

    /** "naoLidas1" para o primeiro uid do id da conversa, "naoLidas2" para o segundo. */
    public static String campoNaoLidas(String conversaId, String uid) {
        return conversaId.startsWith(uid + "_") ? "naoLidas1" : "naoLidas2";
    }

    public static String outroUid(DocumentSnapshot conversa, String meuUid) {
        Object lista = conversa.get("participantes");
        if (lista instanceof List) {
            for (Object uid : (List<?>) lista) if (!meuUid.equals(uid)) return String.valueOf(uid);
        }
        return "";
    }

    /** Cria a conversa, se for a primeira vez, e devolve o id. */
    public void abrir(String meuUid, String outroUid, ColecaoFirestore.Callback<String> callback) {
        String id = idConversa(meuUid, outroUid);
        conversa(id).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        callback.onSucesso(id);
                        return;
                    }
                    Map<String, Object> nova = new HashMap<>();
                    nova.put("participantes", Arrays.asList(meuUid, outroUid));
                    nova.put("ultimaMensagem", "");
                    nova.put("ultimoTimestamp", FieldValue.serverTimestamp());
                    nova.put("ultimoRemetenteId", "");
                    nova.put("naoLidas1", 0);
                    nova.put("naoLidas2", 0);
                    conversa(id).set(nova)
                            .addOnSuccessListener(v -> callback.onSucesso(id))
                            .addOnFailureListener(callback::onErro);
                })
                .addOnFailureListener(callback::onErro);
    }

    /** Minhas conversas, mais recente primeiro (ordem feita aqui, sem índice composto). */
    public ListenerRegistration escutarConversas(String meuUid, ChatRepository.Ouvinte<List<DocumentSnapshot>> ouvinte) {
        return db.collection("conversasAdmin").whereArrayContains("participantes", meuUid)
                .addSnapshotListener((snap, erro) -> {
                    if (snap == null) return;
                    List<DocumentSnapshot> lista = new ArrayList<>(snap.getDocuments());
                    lista.sort((a, b) -> Long.compare(segundos(b), segundos(a)));
                    ouvinte.mudou(lista);
                });
    }

    private static long segundos(DocumentSnapshot c) {
        Timestamp ts = c.getTimestamp("ultimoTimestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        return ts != null ? ts.getSeconds() : 0;
    }

    public ListenerRegistration escutarNaoLidas(String meuUid, ChatRepository.Ouvinte<Integer> ouvinte) {
        return escutarConversas(meuUid, lista -> {
            int total = 0;
            for (DocumentSnapshot c : lista) {
                Long n = c.getLong(campoNaoLidas(c.getId(), meuUid));
                if (n != null) total += n.intValue();
            }
            ouvinte.mudou(total);
        });
    }

    public ListenerRegistration escutarMensagens(String conversaId, ChatRepository.Ouvinte<List<DocumentSnapshot>> ouvinte) {
        return conversa(conversaId).collection("mensagens").orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snap, erro) -> {
                    if (snap != null) ouvinte.mudou(snap.getDocuments());
                });
    }

    public void enviar(String conversaId, String meuUid, String outroUid, String texto, ColecaoFirestore.Callback<Void> callback) {
        Map<String, Object> m = new HashMap<>();
        m.put("remetenteId", meuUid);
        m.put("destinatarioId", outroUid);
        m.put("conteudo", ChatCrypto.cifrar(texto));
        m.put("tipo", MensagemChat.TIPO_TEXTO);
        m.put("timestamp", FieldValue.serverTimestamp());
        m.put("lida", false);
        m.put("deletadoParaTodos", false);
        m.put("deletadoParaRemetente", false);
        m.put("deletadoParaDestinatario", false);

        Map<String, Object> c = new HashMap<>();
        c.put("ultimaMensagem", ChatCrypto.cifrar(texto));
        c.put("ultimoTimestamp", FieldValue.serverTimestamp());
        c.put("ultimoRemetenteId", meuUid);
        c.put(campoNaoLidas(conversaId, outroUid), FieldValue.increment(1));

        WriteBatch batch = db.batch();
        batch.set(conversa(conversaId).collection("mensagens").document(), m);
        batch.update(conversa(conversaId), c);
        batch.commit()
                .addOnSuccessListener(v -> callback.onSucesso(null))
                .addOnFailureListener(callback::onErro);
    }

    /** Conversa aberta: zera meu contador e marca como lidas as mensagens que recebi. */
    public void marcarComoLida(String conversaId, String meuUid) {
        String campo = campoNaoLidas(conversaId, meuUid);
        conversa(conversaId).get().addOnSuccessListener(doc -> {
            Long n = doc.getLong(campo);
            if (n != null && n > 0) conversa(conversaId).update(campo, 0);
        });
        conversa(conversaId).collection("mensagens")
                .whereEqualTo("destinatarioId", meuUid).whereEqualTo("lida", false).get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) return;
                    WriteBatch batch = db.batch();
                    for (DocumentSnapshot d : snap.getDocuments()) batch.update(d.getReference(), "lida", true);
                    batch.commit();
                });
    }

    public void apagarParaMim(DocumentSnapshot mensagem, String meuUid, ColecaoFirestore.Callback<Void> callback) {
        String campo = meuUid.equals(mensagem.getString("remetenteId")) ? "deletadoParaRemetente" : "deletadoParaDestinatario";
        mensagem.getReference().update(campo, true)
                .addOnSuccessListener(v -> callback.onSucesso(null))
                .addOnFailureListener(callback::onErro);
    }

    public void apagarParaTodos(DocumentSnapshot mensagem, ColecaoFirestore.Callback<Void> callback) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("deletadoParaTodos", true);
        dados.put("conteudo", ChatCrypto.cifrar(MensagemChat.TEXTO_APAGADA));
        mensagem.getReference().update(dados)
                .addOnSuccessListener(v -> callback.onSucesso(null))
                .addOnFailureListener(callback::onErro);
    }

    public void apagarConversa(String conversaId, ColecaoFirestore.Callback<Void> callback) {
        conversa(conversaId).collection("mensagens").get()
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
                    batch.delete(conversa(conversaId));
                    batch.commit()
                            .addOnSuccessListener(v -> callback.onSucesso(null))
                            .addOnFailureListener(callback::onErro);
                })
                .addOnFailureListener(callback::onErro);
    }
}
