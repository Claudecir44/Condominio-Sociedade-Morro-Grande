package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Context;
import android.net.Uri;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Boletos em boletos/{id} (moradorUid, arquivo, nomeArquivo, postadoEm, naoPago) e o PDF em
 * Storage boletos/{moradorUid}/{arquivo}. Só o admin posta, marca "não pago" ou exclui.
 */
public class BoletosRepository {

    private final CollectionReference boletos = FirebaseFirestore.getInstance().collection("boletos");

    /** Todos os boletos (admin e conta master), agrupados pelo UID do morador, mais novo primeiro. */
    public void listarPorMorador(ColecaoFirestore.Callback<Map<String, List<Boleto>>> callback) {
        boletos.get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, List<Boleto>> mapa = new HashMap<>();
                    for (Boleto b : paraLista(snapshot)) {
                        List<Boleto> doMorador = mapa.get(b.getMoradorUid());
                        if (doMorador == null) {
                            doMorador = new ArrayList<>();
                            mapa.put(b.getMoradorUid(), doMorador);
                        }
                        doMorador.add(b);
                    }
                    callback.onSucesso(mapa);
                })
                .addOnFailureListener(callback::onErro);
    }

    /** Boletos de um morador (tela Pagamentos), mais novo primeiro. */
    public void listarDoMorador(String moradorUid, ColecaoFirestore.Callback<List<Boleto>> callback) {
        boletos.whereEqualTo("moradorUid", moradorUid).get()
                .addOnSuccessListener(snapshot -> callback.onSucesso(paraLista(snapshot)))
                .addOnFailureListener(callback::onErro);
    }

    /** Sobe o PDF para boletos/{moradorUid}/ e grava o boleto com a data/hora da postagem. */
    public void postar(Context context, String moradorUid, Uri pdf, ColecaoFirestore.Callback<Void> callback) {
        String nome = ArquivosStorage.nomeDoAnexo(context, pdf.toString());
        if (!nome.toLowerCase().endsWith(".pdf")) nome = nome + ".pdf";
        String nomeFinal = nome;
        ArquivosStorage.enviarNaPasta(context, "boletos", moradorUid,
                System.currentTimeMillis() + "_" + nomeFinal, pdf, new ColecaoFirestore.Callback<String>() {
                    @Override
                    public void onSucesso(String link) {
                        Map<String, Object> dados = new HashMap<>();
                        dados.put("moradorUid", moradorUid);
                        dados.put("arquivo", link);
                        dados.put("nomeArquivo", nomeFinal);
                        dados.put("postadoEm", FieldValue.serverTimestamp());
                        dados.put("naoPago", false);
                        boletos.add(dados)
                                .addOnSuccessListener(ref -> callback.onSucesso(null))
                                .addOnFailureListener(callback::onErro);
                    }

                    @Override
                    public void onErro(Exception e) {
                        callback.onErro(e);
                    }
                });
    }

    public void marcarNaoPago(String id, boolean naoPago, ColecaoFirestore.Callback<Void> callback) {
        boletos.document(id).update("naoPago", naoPago)
                .addOnSuccessListener(v -> callback.onSucesso(null))
                .addOnFailureListener(callback::onErro);
    }

    public void excluir(String id, ColecaoFirestore.Callback<Void> callback) {
        boletos.document(id).delete()
                .addOnSuccessListener(v -> callback.onSucesso(null))
                .addOnFailureListener(callback::onErro);
    }

    private static List<Boleto> paraLista(QuerySnapshot snapshot) {
        List<Boleto> lista = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Timestamp postado = doc.getTimestamp("postadoEm", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE);
            lista.add(new Boleto(doc.getId(),
                    doc.getString("moradorUid"),
                    doc.getString("arquivo"),
                    doc.getString("nomeArquivo"),
                    postado != null ? postado.toDate() : null,
                    Boolean.TRUE.equals(doc.getBoolean("naoPago"))));
        }
        Collections.sort(lista, (a, b) -> b.getPostadoEm().compareTo(a.getPostadoEm()));
        return lista;
    }
}
