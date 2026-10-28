package com.cjstudio.condominio_sociedade_morro_grande;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;

/**
 * Play Billing só no que a assinatura precisa — mesmo desenho do GooglePlayBillingManager do
 * Caronas/Match, mas só Google Play (sem a escolha do Mercado Pago). A mensalidade é um produto
 * avulso consumível (Assinatura.PRODUTO_GOOGLE_PLAY), comprado de novo todo mês. A compra paga
 * chega em aoComprar e a tela manda pro servidor (confirmarCompraAssinaturaCondominio) liberar.
 */
public class GooglePlayBillingManager {

    public interface Callback {
        void onCompraConcluida(Purchase compra);
        void onErro(String mensagem);
    }

    private static final String TAG = "GooglePlayBilling";

    private final Callback callback;
    private final BillingClient cliente;
    private boolean conectado = false;
    private ProductDetails produto;

    public GooglePlayBillingManager(Context context, Callback callback) {
        this.callback = callback;
        PurchasesUpdatedListener ouvinte = (resultado, compras) -> {
            int codigo = resultado.getResponseCode();
            if (codigo == BillingClient.BillingResponseCode.OK) {
                if (compras != null) for (Purchase c : compras) callback.onCompraConcluida(c);
            } else if (codigo != BillingClient.BillingResponseCode.USER_CANCELED) {
                Log.e(TAG, "Erro na compra via Google Play: " + resultado.getDebugMessage());
                callback.onErro(resultado.getDebugMessage());
            }
        };
        cliente = BillingClient.newBuilder(context)
                .setListener(ouvinte)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
    }

    /** Idempotente: já conectado (e produto carregado) chama aoConectar na hora. */
    public void conectar(Runnable aoConectar) {
        if (conectado) {
            aoConectar.run();
            return;
        }
        cliente.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult resultado) {
                if (resultado.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    carregarProduto(() -> {
                        conectado = true;
                        aoConectar.run();
                    });
                } else {
                    Log.e(TAG, "Falha ao conectar à Play Billing: " + resultado.getDebugMessage());
                    callback.onErro(resultado.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                conectado = false;
            }
        });
    }

    private void carregarProduto(Runnable aoTerminar) {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(Assinatura.PRODUTO_GOOGLE_PLAY)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()))
                .build();
        cliente.queryProductDetailsAsync(params, (resultado, lista) -> {
            if (resultado.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && !lista.getProductDetailsList().isEmpty()) {
                produto = lista.getProductDetailsList().get(0);
            } else {
                // Sem produto: o app ainda não está no Play Console ou o produto não foi criado lá
                Log.w(TAG, "Produto " + Assinatura.PRODUTO_GOOGLE_PLAY + " não encontrado no Play Console.");
            }
            aoTerminar.run();
        });
    }

    /** Preço do Google (já no formato da loja), se o produto existir lá. */
    public String preco() {
        return produto != null && produto.getOneTimePurchaseOfferDetails() != null
                ? produto.getOneTimePurchaseOfferDetails().getFormattedPrice() : null;
    }

    /**
     * Abre a tela de pagamento do Google. uid vai como obfuscatedAccountId: o servidor confere
     * que a compra é da mesma conta que pede a confirmação. Devolve false se o produto não existe.
     */
    public boolean comprar(Activity activity, String uid) {
        if (produto == null) return false;
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(produto).build()))
                .setObfuscatedAccountId(uid)
                .build();
        BillingResult r = cliente.launchBillingFlow(activity, params);
        if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) callback.onErro(r.getDebugMessage());
        return true;
    }

    /**
     * Compras pagas que ainda não foram liberadas (Pix compensado depois, app fechado no meio).
     * Reprocessar não tem problema: o servidor é idempotente.
     */
    public void verificarComprasPendentes() {
        if (!conectado) return;
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build();
        cliente.queryPurchasesAsync(params, (resultado, compras) -> {
            if (resultado.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
            for (Purchase c : compras) {
                if (c.getPurchaseState() == Purchase.PurchaseState.PURCHASED) callback.onCompraConcluida(c);
            }
        });
    }

    public void encerrar() {
        cliente.endConnection();
    }
}
