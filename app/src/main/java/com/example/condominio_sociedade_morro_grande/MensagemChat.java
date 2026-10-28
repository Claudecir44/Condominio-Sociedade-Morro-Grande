package com.cjstudio.condominio_sociedade_morro_grande;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Mensagem do chat morador x Administração (conversas/{moradorUid}/mensagens/{id}). "conteudo"
 * fica cifrado (ChatCrypto); aqui já vem decifrado. Do lado da Administração qualquer admin
 * responde, então "de quem" é deAdmin (Administração) ou não (morador).
 * Apagar: "só pra mim" (deletadoParaMorador / deletadoParaAdmin) ou "pra todos" (só quem mandou).
 */
public class MensagemChat {

    public static final String TIPO_TEXTO = "texto";
    public static final String TIPO_IMAGEM = "imagem";
    public static final String TEXTO_APAGADA = "🚫 Mensagem apagada";

    public final String id;
    public final boolean deAdmin;
    public final String remetenteId;
    public final String texto;
    public final String tipo;
    public final String imagemUrl;
    public final Date quando;
    public final boolean lida;
    public final boolean deletadoParaTodos;
    public final boolean deletadoParaMorador;
    public final boolean deletadoParaAdmin;

    private MensagemChat(DocumentSnapshot doc) {
        id = doc.getId();
        deAdmin = Boolean.TRUE.equals(doc.getBoolean("deAdmin"));
        remetenteId = doc.getString("remetenteId");
        texto = ChatCrypto.decifrar(doc.getString("conteudo"));
        String t = doc.getString("tipo");
        tipo = t != null ? t : TIPO_TEXTO;
        imagemUrl = doc.getString("imagemUrl");
        Timestamp ts = doc.getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
        quando = ts != null ? ts.toDate() : new Date();
        lida = Boolean.TRUE.equals(doc.getBoolean("lida"));
        deletadoParaTodos = Boolean.TRUE.equals(doc.getBoolean("deletadoParaTodos"));
        deletadoParaMorador = Boolean.TRUE.equals(doc.getBoolean("deletadoParaMorador"));
        deletadoParaAdmin = Boolean.TRUE.equals(doc.getBoolean("deletadoParaAdmin"));
    }

    public static MensagemChat de(DocumentSnapshot doc) {
        return new MensagemChat(doc);
    }

    /** Mensagem minha: do lado da Administração, qualquer mensagem de admin conta como "minha". */
    public boolean minha(boolean souAdmin) {
        return deAdmin == souAdmin;
    }

    /** Apagada só pra mim: some da conversa. */
    public boolean ocultaPara(boolean souAdmin) {
        return souAdmin ? deletadoParaAdmin : deletadoParaMorador;
    }

    public boolean ehImagem() {
        return TIPO_IMAGEM.equals(tipo) && imagemUrl != null && !deletadoParaTodos;
    }

    public String hora() {
        return new SimpleDateFormat("dd/MM HH:mm", new Locale("pt", "BR")).format(quando);
    }
}
