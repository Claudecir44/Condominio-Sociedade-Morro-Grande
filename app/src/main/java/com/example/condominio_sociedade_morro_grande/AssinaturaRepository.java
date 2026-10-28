package com.cjstudio.condominio_sociedade_morro_grande;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.HashMap;
import java.util.Map;

/**
 * Assinatura do app: lê config/assinatura (todos os membros) e confirma no servidor a compra feita
 * no Google Play (confirmarCompraAssinaturaCondominio, que é quem grava o mês pago).
 */
public class AssinaturaRepository {

    public static final int RESULTADO_LIBERADO = 0;
    /** Pix/boleto ainda não compensou: o app confere de novo depois. */
    public static final int RESULTADO_PENDENTE = 1;

    /** Último mês pago ("2026-11") ou null se nunca foi paga. */
    public void mesPago(ColecaoFirestore.Callback<String> callback) {
        FirebaseFirestore.getInstance().collection("config").document("assinatura").get()
                .addOnSuccessListener(doc -> callback.onSucesso(doc.getString("pagoAteMes")))
                .addOnFailureListener(callback::onErro);
    }

    public void estado(ColecaoFirestore.Callback<Assinatura.Estado> callback) {
        mesPago(new ColecaoFirestore.Callback<String>() {
            @Override
            public void onSucesso(String pagoAteMes) {
                callback.onSucesso(Assinatura.estado(pagoAteMes));
            }

            @Override
            public void onErro(Exception e) {
                callback.onErro(e);
            }
        });
    }

    /** O quadro "Assinaturas" e o pagamento são só do admin master (administradores/{uid}.master). */
    public void ehAdminMaster(ColecaoFirestore.Callback<Boolean> callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || !"admin".equals(BuildConfig.PERFIL)) {
            callback.onSucesso(false);
            return;
        }
        FirebaseFirestore.getInstance().collection("administradores").document(user.getUid()).get()
                .addOnSuccessListener(doc -> callback.onSucesso(Boolean.TRUE.equals(doc.getBoolean("master"))))
                .addOnFailureListener(callback::onErro);
    }

    public void confirmarCompra(String purchaseToken, ColecaoFirestore.Callback<Integer> callback) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("purchaseToken", purchaseToken);
        dados.put("productId", Assinatura.PRODUTO_GOOGLE_PLAY);
        FirebaseFunctions.getInstance().getHttpsCallable("confirmarCompraAssinaturaCondominio").call(dados)
                .addOnSuccessListener(r -> {
                    Object resposta = r.getData();
                    boolean pendente = resposta instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) resposta).get("pendente"));
                    callback.onSucesso(pendente ? RESULTADO_PENDENTE : RESULTADO_LIBERADO);
                })
                .addOnFailureListener(callback::onErro);
    }
}
