// Cloud Functions do Condomínio Sociedade Morro Grande (projeto condominio-morro-grande).
// Por enquanto só a assinatura mensal do app, paga pela conta master no app Moradores pelo
// Google Play — mesmo fluxo do confirmarCompraGooglePlayMotorista do Caronas, sem o Mercado
// Pago: o app compra, manda o token pra cá, conferimos no Google, consumimos e gravamos o mês
// pago em config/assinatura. Regras no app em Assinatura.java (precisam bater com estas).
require('dotenv').config();

const functions = require('firebase-functions');
const admin = require('firebase-admin');
// FieldValue pelo módulo próprio: admin.firestore.FieldValue vem indefinido no firebase-admin 13.
const { FieldValue } = require('firebase-admin/firestore');
const { google } = require('googleapis');

admin.initializeApp();

// functions/.env (nunca no app nem no git):
//   GOOGLE_PLAY_PACKAGE_NAME=com.cjstudio.condominio_sociedade_morro_grande.morador
//   GOOGLE_PLAY_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}' (a chave JSON inteira, numa linha só)
const GOOGLE_PLAY_PACKAGE_NAME = process.env.GOOGLE_PLAY_PACKAGE_NAME || 'com.cjstudio.condominio_sociedade_morro_grande.morador';
const GOOGLE_PLAY_SERVICE_ACCOUNT_JSON = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON || '';

if (!GOOGLE_PLAY_SERVICE_ACCOUNT_JSON) {
    console.error('❌ Integração com o Google Play NÃO CONFIGURADA!');
    console.error('👉 Crie/edite o arquivo .env na pasta functions com GOOGLE_PLAY_SERVICE_ACCOUNT_JSON.');
    console.error('   Enquanto isso, compras da assinatura não são confirmadas.');
}

// Produto avulso (consumível) cadastrado no Play Console do app Moradores — id igual a
// Assinatura.PRODUTO_GOOGLE_PLAY; o preço lá precisa ser R$ 460,00.
const PRODUTO_ASSINATURA = 'condominio_assinatura_mensal';
const VALOR_ASSINATURA = 460;
// Primeiro mês cobrado (Assinatura.MES_INICIO_COBRANCA).
const MES_INICIO_COBRANCA = '2026-11';

let androidPublisherClient = null;
function obterAndroidPublisher() {
    if (androidPublisherClient) return androidPublisherClient;
    if (!GOOGLE_PLAY_SERVICE_ACCOUNT_JSON) return null;
    const auth = new google.auth.GoogleAuth({
        credentials: JSON.parse(GOOGLE_PLAY_SERVICE_ACCOUNT_JSON),
        scopes: ['https://www.googleapis.com/auth/androidpublisher'],
    });
    androidPublisherClient = google.androidpublisher({ version: 'v3', auth });
    return androidPublisherClient;
}

// "2026-11" no horário de Brasília (o mesmo do app).
function mesDeBrasilia(data) {
    const partes = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit' })
        .formatToParts(data);
    const ano = partes.find((p) => p.type === 'year').value;
    const mes = partes.find((p) => p.type === 'month').value;
    return `${ano}-${mes}`;
}

function proximoMes(mes) {
    const [ano, m] = mes.split('-').map(Number);
    return m === 12 ? `${ano + 1}-01` : `${ano}-${String(m + 1).padStart(2, '0')}`;
}

exports.confirmarCompraAssinaturaCondominio = functions.https.onCall(async (request) => {
    if (!request.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'Usuário não autenticado.');
    }
    const uid = request.auth.uid;
    const { purchaseToken, productId } = request.data || {};
    if (!purchaseToken || productId !== PRODUTO_ASSINATURA) {
        throw new functions.https.HttpsError('invalid-argument', 'Compra inválida.');
    }

    const db = admin.firestore();
    // Só a conta master do app Moradores ("Condominio sociedade Morro grande", verTodos) paga
    const masterDoc = await db.collection('moradores').doc(uid).get();
    if (!masterDoc.exists || masterDoc.get('verTodos') !== true) {
        throw new functions.https.HttpsError('permission-denied', 'Só a conta master paga a assinatura.');
    }

    const androidpublisher = obterAndroidPublisher();
    if (!androidpublisher) {
        throw new functions.https.HttpsError('failed-precondition', 'Integração com o Google Play não configurada no servidor.');
    }

    let compra;
    try {
        const resposta = await androidpublisher.purchases.products.get({
            packageName: GOOGLE_PLAY_PACKAGE_NAME,
            productId,
            token: purchaseToken,
        });
        compra = resposta.data;
    } catch (error) {
        console.error('❌ Erro ao verificar compra no Google Play:', error.message);
        throw new functions.https.HttpsError('internal', 'Não foi possível verificar a compra junto ao Google Play.');
    }

    // purchaseState: 0 = comprado, 1 = cancelado, 2 = pendente (Pix/boleto ainda não
    // confirmado) — pendente não é erro, o app tenta de novo.
    if (compra.purchaseState === 2) {
        return { success: false, pendente: true };
    }
    if (compra.purchaseState !== 0) {
        throw new functions.https.HttpsError('failed-precondition', 'Compra não concluída (purchaseState=' + compra.purchaseState + ').');
    }
    // A compra tem que ser desta conta: o app manda o uid como obfuscatedAccountId.
    if (compra.obfuscatedExternalAccountId && compra.obfuscatedExternalAccountId !== uid) {
        throw new functions.https.HttpsError('permission-denied', 'Esta compra pertence a outra conta.');
    }

    // Idempotência: o mesmo token só libera um mês. Trava só depois de confirmar "comprado".
    const travaRef = db.collection('pagamentosAssinaturaProcessados').doc(`gp_${purchaseToken}`);
    try {
        await travaRef.create({ uid, processadoEm: Date.now() });
    } catch (e) {
        if (e.code === 6) return { success: true, jaProcessado: true }; // ALREADY_EXISTS
        throw e;
    }

    // CONSUMIR (não só reconhecer): a mensalidade é comprada de novo todo mês; consumir também
    // conta como reconhecer (senão o Google estorna em 3 dias).
    if (compra.consumptionState !== 1) {
        try {
            await androidpublisher.purchases.products.consume({
                packageName: GOOGLE_PLAY_PACKAGE_NAME,
                productId,
                token: purchaseToken,
            });
        } catch (error) {
            console.error('❌ Erro ao consumir compra no Google Play (mês liberado mesmo assim):', error.message);
        }
    }

    // O pagamento vale o mês vigente inteiro. Se o mês vigente já estiver pago (compra repetida),
    // vale o mês seguinte.
    const configRef = db.collection('config').doc('assinatura');
    const mesPago = await db.runTransaction(async (tx) => {
        const atual = (await tx.get(configRef)).get('pagoAteMes') || null;
        let mes = mesDeBrasilia(new Date());
        if (mes < MES_INICIO_COBRANCA) mes = MES_INICIO_COBRANCA;
        if (atual && atual >= mes) mes = proximoMes(atual);
        tx.set(configRef, { pagoAteMes: mes, atualizadoEm: FieldValue.serverTimestamp() }, { merge: true });
        tx.set(db.collection('pagamentosAssinatura').doc(), {
            uid,
            nome: masterDoc.get('nome') || '',
            mes,
            valor: VALOR_ASSINATURA,
            produto: productId,
            googlePlayOrderId: compra.orderId || '',
            criadoEm: FieldValue.serverTimestamp(),
        });
        return mes;
    });
    return { success: true, mes: mesPago };
});
