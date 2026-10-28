package com.cjstudio.condominio_sociedade_morro_grande;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.android.billingclient.api.Purchase;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashSet;
import java.util.Set;

/**
 * Quadro "Assinaturas" da conta master (app Moradores): situação do mês e "Pagamentos", que abre a tela de
 * pagamento do Google Play (só Google Play, sem Mercado Pago). A compra paga é confirmada no
 * servidor (confirmarCompraAssinaturaCondominio), que grava o mês como pago — quem libera o app
 * é sempre o servidor, nunca o retorno da tela do Google.
 */
public class AssinaturasActivity extends AppCompatActivity implements GooglePlayBillingManager.Callback {

    private final AssinaturaRepository repository = new AssinaturaRepository();
    private GooglePlayBillingManager billing;
    private Assinatura.Estado estado;
    private boolean confirmando = false;
    // Tokens já em confirmação: o mesmo pode chegar pela compra e pela reconsulta do onResume
    private final Set<String> emConfirmacao = new HashSet<>();

    private LinearLayout layoutSituacao;
    private ImageView imgSituacao;
    private TextView tvSituacaoTitulo, tvSituacaoDetalhe, tvValor, tvAviso;
    private Button btnPagamentos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assinaturas);

        layoutSituacao = findViewById(R.id.layoutSituacao);
        imgSituacao = findViewById(R.id.imgSituacao);
        tvSituacaoTitulo = findViewById(R.id.tvSituacaoTitulo);
        tvSituacaoDetalhe = findViewById(R.id.tvSituacaoDetalhe);
        tvValor = findViewById(R.id.tvValor);
        tvAviso = findViewById(R.id.tvAvisoPagamento);
        btnPagamentos = findViewById(R.id.btnPagamentos);

        billing = new GooglePlayBillingManager(this, this);
        billing.conectar(() -> runOnUiThread(() -> {
            mostrarPreco();
            billing.verificarComprasPendentes();
        }));

        btnPagamentos.setOnClickListener(v -> pagar());
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregar();
        billing.verificarComprasPendentes();
    }

    @Override
    protected void onDestroy() {
        billing.encerrar();
        super.onDestroy();
    }

    private void carregar() {
        repository.estado(new ColecaoFirestore.Callback<Assinatura.Estado>() {
            @Override
            public void onSucesso(Assinatura.Estado e) {
                estado = e;
                mostrar();
            }

            @Override
            public void onErro(Exception e) {
                tvSituacaoTitulo.setText("Não foi possível carregar a assinatura");
                tvSituacaoDetalhe.setText(e.getMessage());
            }
        });
    }

    private void mostrarPreco() {
        String preco = billing.preco();
        tvValor.setText((preco != null ? preco : Assinatura.VALOR) + " por mês");
    }

    private void mostrar() {
        String mes = Assinatura.mesTexto(estado.mesAtual);
        switch (estado.situacao) {
            case EM_DIA:
                cores("#DCFCE7", "#166534", R.drawable.ic_boleto_em_dia);
                tvSituacaoTitulo.setText("Assinatura em dia");
                tvSituacaoDetalhe.setText(estado.dia >= Assinatura.DIA_LEMBRETE
                        ? "Mês " + mes + " pago. Lembrete: a próxima vence dia 01."
                        : "Mês " + mes + " pago.");
                break;
            case AGUARDANDO_PAGAMENTO:
                cores("#FFEDD5", "#9A3412", R.drawable.ic_boleto_vencido);
                tvSituacaoTitulo.setText("Aguardando pagamento de " + mes);
                tvSituacaoDetalhe.setText("O vencimento da sua assinatura é até o dia 05. Depois disso o app trava.");
                break;
            case BLOQUEADA:
                cores("#FEE2E2", "#B91C1C", R.drawable.ic_boleto_vencido);
                tvSituacaoTitulo.setText("Assinatura vencida — app travado");
                tvSituacaoDetalhe.setText("Pague o mês " + mes + " para liberar o app para todos.");
                break;
        }
        boolean pode = estado.podePagar() && !confirmando;
        btnPagamentos.setEnabled(pode);
        btnPagamentos.setAlpha(pode ? 1f : 0.45f);
        tvAviso.setText(confirmando ? "Confirmando o pagamento…"
                : estado.podePagar() ? "Pagamento pelo Google Play."
                : "O próximo pagamento fica liberado no dia 01.");
    }

    private void cores(String fundo, String texto, int icone) {
        layoutSituacao.setBackgroundColor(Color.parseColor(fundo));
        tvSituacaoTitulo.setTextColor(Color.parseColor(texto));
        imgSituacao.setImageResource(icone);
    }

    private void pagar() {
        if (estado == null || !estado.podePagar()) return;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        billing.conectar(() -> runOnUiThread(() -> {
            if (!billing.comprar(this, user.getUid())) {
                new AlertDialog.Builder(this)
                        .setTitle("Pagamentos")
                        .setMessage("O pagamento pelo Google Play ainda não está disponível. "
                                + "O app precisa estar publicado na Play Store com o produto da assinatura cadastrado.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        }));
    }

    @Override
    public void onCompraConcluida(Purchase compra) {
        runOnUiThread(() -> {
            if (!emConfirmacao.add(compra.getPurchaseToken())) return;
            confirmando = true;
            if (estado != null) mostrar();
            repository.confirmarCompra(compra.getPurchaseToken(), new ColecaoFirestore.Callback<Integer>() {
                @Override
                public void onSucesso(Integer resultado) {
                    emConfirmacao.remove(compra.getPurchaseToken());
                    confirmando = false;
                    Toast.makeText(AssinaturasActivity.this,
                            resultado == AssinaturaRepository.RESULTADO_PENDENTE
                                    ? "Pagamento em processamento. O app libera assim que o Google confirmar."
                                    : "Pagamento confirmado! Assinatura em dia.",
                            Toast.LENGTH_LONG).show();
                    carregar();
                }

                @Override
                public void onErro(Exception e) {
                    emConfirmacao.remove(compra.getPurchaseToken());
                    confirmando = false;
                    Toast.makeText(AssinaturasActivity.this,
                            "Erro ao confirmar o pagamento: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    carregar();
                }
            });
        });
    }

    @Override
    public void onErro(String mensagem) {
        runOnUiThread(() -> Toast.makeText(this, "Google Play: " + mensagem, Toast.LENGTH_LONG).show());
    }
}
