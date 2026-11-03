// Cloud Functions do Condomínio Sociedade Morro Grande (projeto condominio-morro-grande).
// - Assinatura mensal do app, paga pela conta master no app Moradores pelo Google Play — mesmo
//   fluxo do confirmarCompraGooglePlayMotorista do Caronas, sem o Mercado Pago (regras no app em
//   Assinatura.java, precisam bater com estas).
// - Notificação de mensagem nova no chat com a Administração (notificarMensagemChat).
// - Senha do lote quando muda o responsável (inquilino): definirSenhaResponsavelMorador.
// - Login do lote quando o admin muda a quadra/lote: alterarLoginMorador.
// - Notificação do Chat admin (notificarMensagemChatAdmin).
// - Backup diário e Pacote completo: backup.js.
require('dotenv').config();

const functions = require('firebase-functions');
const admin = require('firebase-admin');
// FieldValue pelo módulo próprio: admin.firestore.FieldValue vem indefinido no firebase-admin 13.
const { FieldValue } = require('firebase-admin/firestore');
const { google } = require('googleapis');

admin.initializeApp();

// Mesma região do Firestore (southamerica-east1) e do app (FirebaseFunctions.getInstance(REGIAO)).
// Conta de execução: a do SDK Admin do Firebase. As contas padrão do Compute e do App Engine
// nascem SEM papéis neste projeto (mesmo caso do Manicure), e a do Firebase já tem
// Firestore/Auth/Storage/Messaging.
const REGIAO = 'southamerica-east1';
const CONTA_EXECUCAO = 'firebase-adminsdk-fbsvc@condominio-morro-grande.iam.gserviceaccount.com';
require('firebase-functions/v2').setGlobalOptions({ region: REGIAO, maxInstances: 10, serviceAccount: CONTA_EXECUCAO });

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
const MES_INICIO_COBRANCA = '2027-01';

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


// ============================================================
// Chat morador x Administração: notificação de mensagem nova (mesmo desenho do
// notificarMensagem do Manicure — só "data", quem monta a notificação é o
// CondominioMessagingService no app). Token em dispositivos/{uid}: o morador recebe o
// que a Administração manda; admins e a conta master (administracao == true) recebem
// o que os moradores mandam.
// ============================================================
const functionsV1 = require('firebase-functions/v1');
const crypto = require('crypto');

// Mesma chave do ChatCrypto.java (o texto fica cifrado no Firestore).
const CHAVE_CHAT = Buffer.from('NIYJ8BQYYrEbXaBbFWKG/AcgGY2IhZfmY3wQHCi2A8s=', 'base64');

function decifrarChat(texto) {
    if (!texto || !texto.startsWith('ENC1:')) return texto || '';
    try {
        const tudo = Buffer.from(texto.slice(5), 'base64');
        const iv = tudo.subarray(0, 12);
        const tag = tudo.subarray(tudo.length - 16);
        const cifrado = tudo.subarray(12, tudo.length - 16);
        const d = crypto.createDecipheriv('aes-256-gcm', CHAVE_CHAT, iv);
        d.setAuthTag(tag);
        return Buffer.concat([d.update(cifrado), d.final()]).toString('utf8');
    } catch (e) {
        return 'Nova mensagem';
    }
}

// Quem usa o app no lote: o inquilino, se houver (Inquilino.java), senão o morador.
function nomeResponsavel(m) {
    const nome = m.temInquilino && m.inquilinoNome ? m.inquilinoNome : (m.nome || 'Morador');
    return String(nome).trim().split(/\s+/)[0];
}

// "Q:A L:01" (igual ao ChatRepository.rotuloMorador).
function rotuloMorador(m) {
    const q = String(m.quadra || '-').trim().toUpperCase() || '-';
    let l = String(m.lote || '-').trim() || '-';
    if (/^\d{1,9}$/.test(l)) l = l.padStart(2, '0');
    return `Q:${q} L:${l}`;
}

async function enviarPush(docs, dados) {
    const db = admin.firestore();
    await Promise.all(docs.map(async (doc) => {
        const token = doc.get('token');
        if (!token) return;
        try {
            await admin.messaging().send({ token, data: dados, android: { priority: 'high' } });
        } catch (error) {
            console.warn('Erro ao enviar push:', error.message);
            if (error.code === 'messaging/registration-token-not-registered') {
                await db.collection('dispositivos').doc(doc.id).delete();
            }
        }
    }));
}

exports.notificarMensagemChat = functionsV1.region(REGIAO).runWith({ serviceAccount: CONTA_EXECUCAO }).firestore
    .document('conversas/{moradorUid}/mensagens/{mensagemId}')
    .onCreate(async (snap, context) => {
        const msg = snap.data() || {};
        const db = admin.firestore();
        const moradorUid = context.params.moradorUid;
        const texto = msg.tipo === 'imagem' ? '📷 Foto' : decifrarChat(msg.conteudo);
        const corpo = texto.length > 120 ? texto.slice(0, 117) + '...' : texto;

        if (msg.deAdmin) {
            // Da Administração pro morador (só o aparelho dele)
            const doc = await db.collection('dispositivos').doc(moradorUid).get();
            if (doc.exists) await enviarPush([doc], { tipo: 'chat', moradorUid, titulo: 'Administração', corpo });
            return;
        }
        // Do morador pra Administração (admins e conta master)
        const morador = (await db.collection('moradores').doc(moradorUid).get()).data() || {};
        const titulo = `${rotuloMorador(morador)} · ${nomeResponsavel(morador)}`;
        const admins = await db.collection('dispositivos').where('administracao', '==', true).get();
        await enviarPush(admins.docs, { tipo: 'chat', moradorUid, titulo, corpo });
    });


// Chat admin: avisa só o outro administrador da conversa
exports.notificarMensagemChatAdmin = functionsV1.region(REGIAO).runWith({ serviceAccount: CONTA_EXECUCAO }).firestore
    .document('conversasAdmin/{conversaId}/mensagens/{mensagemId}')
    .onCreate(async (snap) => {
        const msg = snap.data() || {};
        const db = admin.firestore();
        const destino = await db.collection('dispositivos').doc(msg.destinatarioId).get();
        if (!destino.exists) return;
        const remetente = (await db.collection('administradores').doc(msg.remetenteId).get()).data() || {};
        const texto = decifrarChat(msg.conteudo);
        const corpo = texto.length > 120 ? texto.slice(0, 117) + '...' : texto;
        await enviarPush([destino], { tipo: 'chatAdmin', titulo: remetente.nome || 'Administrador', corpo });
    });

const backup = require('./backup');
exports.backupDiario = backup.backupDiario;
exports.gerarBackupAgora = backup.gerarBackupAgora;
exports.gerarPacoteCompleto = backup.gerarPacoteCompleto;

// ============================================================
// Inquilino: quando o admin põe/tira o inquilino ou muda o CPF de quem usa o app, a senha do
// login do lote passa a ser os 6 primeiros dígitos do CPF do novo responsável. Só o servidor
// troca a senha de outra conta. O CPF vem do próprio cadastro (não do app), e a sessão antiga
// é encerrada.
// ============================================================
exports.definirSenhaResponsavelMorador = functions.https.onCall(async (request) => {
    if (!request.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'Usuário não autenticado.');
    }
    const db = admin.firestore();
    const adminDoc = await db.collection('administradores').doc(request.auth.uid).get();
    if (!adminDoc.exists || adminDoc.get('tipo') !== 'admin') {
        throw new functions.https.HttpsError('permission-denied', 'Só administradores trocam a senha do morador.');
    }
    const { moradorUid } = request.data || {};
    if (!moradorUid) throw new functions.https.HttpsError('invalid-argument', 'Morador não informado.');

    const morador = (await db.collection('moradores').doc(moradorUid).get()).data();
    if (!morador) throw new functions.https.HttpsError('not-found', 'Morador não encontrado.');
    if (morador.verTodos === true) {
        throw new functions.https.HttpsError('permission-denied', 'A senha da conta master não é trocada por aqui.');
    }
    const cpf = String(morador.temInquilino && morador.inquilinoCpf ? morador.inquilinoCpf : (morador.cpf || ''))
        .replace(/\D/g, '');
    if (cpf.length < 6) throw new functions.https.HttpsError('failed-precondition', 'CPF incompleto no cadastro.');

    await admin.auth().updateUser(moradorUid, { password: cpf.slice(0, 6) });
    // Quem estava logado (o responsável anterior) sai, e o aparelho dele para de receber o chat
    await admin.auth().revokeRefreshTokens(moradorUid);
    await db.collection('dispositivos').doc(moradorUid).delete();
    return { success: true };
});


// ============================================================
// Login do lote quando o admin muda a quadra/lote na edição: o usuário (quadra+lote) é o e-mail
// interno da conta no Auth, então só o servidor troca. A senha continua a mesma. Mesmo formato
// de ContaHelper.emailDoMorador (app) e emailDoMorador (painel web).
// ============================================================
exports.alterarLoginMorador = functions.https.onCall(async (request) => {
    if (!request.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'Usuário não autenticado.');
    }
    const db = admin.firestore();
    const adminDoc = await db.collection('administradores').doc(request.auth.uid).get();
    if (!adminDoc.exists || adminDoc.get('tipo') !== 'admin') {
        throw new functions.https.HttpsError('permission-denied', 'Só administradores trocam o login do morador.');
    }
    const { moradorUid } = request.data || {};
    const usuario = String((request.data || {}).usuario || '').trim().toUpperCase();
    const login = usuario.normalize('NFD').replace(/[^A-Za-z0-9]/g, '').toLowerCase();
    if (!moradorUid || !login) throw new functions.https.HttpsError('invalid-argument', 'Morador ou login não informado.');

    const ref = db.collection('moradores').doc(moradorUid);
    const morador = (await ref.get()).data();
    if (!morador) throw new functions.https.HttpsError('not-found', 'Morador não encontrado.');
    if (morador.verTodos === true) {
        throw new functions.https.HttpsError('permission-denied', 'O login da conta master não é trocado por aqui.');
    }
    const loginEmail = login + '@moradores.morrogrande.app';
    const conta = await admin.auth().getUser(moradorUid);
    if (conta.email !== loginEmail) {
        const dono = await admin.auth().getUserByEmail(loginEmail).catch(() => null);
        if (dono && dono.uid !== moradorUid) {
            throw new functions.https.HttpsError('already-exists', `Já existe um morador com o login ${usuario}.`);
        }
        await admin.auth().updateUser(moradorUid, { email: loginEmail });
    }
    await ref.update({ usuario, loginEmail });
    return { success: true, usuario };
});
