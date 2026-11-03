// Backup do Condomínio Sociedade Morro Grande.
// - backupDiario: todo dia à meia-noite (Brasília) grava o banco inteiro em JSON no Storage e guarda
//   os últimos 15.
// - gerarBackupAgora: o mesmo backup, na hora, pelo botão "Backup" (só admin).
// - gerarPacoteCompleto: um ZIP para levar os dados embora: banco, planilhas, todos os arquivos do
//   Storage e o LEIA-ME. A restauração em outro projeto é o scripts/restaurar_backup.mjs.
const admin = require('firebase-admin');
const { randomUUID } = require('crypto');
const { Timestamp, GeoPoint, DocumentReference } = require('firebase-admin/firestore');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const archiver = require('archiver');

const BUCKET = 'condominio-morro-grande.firebasestorage.app';
const DIAS_GUARDADOS = 15;
const PACOTES_GUARDADOS = 3;
// backups: os próprios backups; dispositivos: tokens de notificação, que não valem em outro app
const FORA_DO_BACKUP = ['backups', 'dispositivos'];

function agoraEmBrasilia() {
    const partes = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
        timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit', hour12: false,
    }).formatToParts(new Date()).map((p) => [p.type, p.value]));
    return { data: `${partes.year}-${partes.month}-${partes.day}`, hora: `${partes.hour}${partes.minute}` };
}

// Tipos do Firestore viram JSON comum com a marca "__tipo", que a restauração desfaz
function paraJson(valor) {
    if (valor instanceof Timestamp) return { __tipo: 'data', valor: valor.toDate().toISOString() };
    if (valor instanceof GeoPoint) return { __tipo: 'local', latitude: valor.latitude, longitude: valor.longitude };
    if (valor instanceof DocumentReference) return { __tipo: 'referencia', caminho: valor.path };
    if (Array.isArray(valor)) return valor.map(paraJson);
    if (valor && typeof valor === 'object') {
        return Object.fromEntries(Object.entries(valor).map(([k, v]) => [k, paraJson(v)]));
    }
    return valor;
}

async function lerColecao(colecao) {
    const snap = await colecao.get();
    return Promise.all(snap.docs.map(async (doc) => {
        const subcolecoes = {};
        for (const sub of await doc.ref.listCollections()) subcolecoes[sub.id] = await lerColecao(sub);
        return { id: doc.id, dados: paraJson(doc.data()), subcolecoes };
    }));
}

function contar(documentos) {
    return documentos.reduce((total, d) => total
        + 1 + Object.values(d.subcolecoes).reduce((n, sub) => n + contar(sub), 0), 0);
}

// Contas de login, sem senhas: o Firebase não entrega a senha de ninguém
async function lerUsuarios() {
    const usuarios = [];
    let pagina;
    do {
        const lista = await admin.auth().listUsers(1000, pagina);
        for (const u of lista.users) {
            usuarios.push({
                uid: u.uid, email: u.email || '', nome: u.displayName || '', desativado: u.disabled,
                criadoEm: u.metadata.creationTime, ultimoAcesso: u.metadata.lastSignInTime,
            });
        }
        pagina = lista.pageToken;
    } while (pagina);
    return usuarios;
}

async function lerArquivos(bucket) {
    const [arquivos] = await bucket.getFiles();
    return arquivos.filter((f) => !f.name.startsWith('backups/') && !f.name.endsWith('/'));
}

async function montarBanco(bucket) {
    const db = admin.firestore();
    const colecoes = {};
    for (const colecao of await db.listCollections()) {
        if (!FORA_DO_BACKUP.includes(colecao.id)) colecoes[colecao.id] = await lerColecao(colecao);
    }
    const arquivos = await lerArquivos(bucket);
    return {
        conteudo: {
            formato: 'morro-grande-backup-v1',
            geradoEm: new Date().toISOString(),
            projeto: process.env.GCLOUD_PROJECT || 'condominio-morro-grande',
            bucket: bucket.name,
            colecoes,
            usuarios: await lerUsuarios(),
            arquivos: arquivos.map((f) => ({
                caminho: f.name, tamanho: Number(f.metadata.size || 0), tipo: f.metadata.contentType || '',
                token: f.metadata.metadata?.firebaseStorageDownloadTokens || '',
            })),
        },
        arquivos,
    };
}

function totalDeRegistros(colecoes) {
    return Object.values(colecoes).reduce((n, docs) => n + contar(docs), 0);
}

async function registrar(id, tipo, titulo, caminho, tamanho, registros) {
    await admin.firestore().collection('backups').doc(id).set({
        tipo, titulo, caminho, tamanho, registros, criadoEm: new Date(),
    });
}

async function apagarAntigos(bucket, tipo, manter) {
    const db = admin.firestore();
    const snap = await db.collection('backups').where('tipo', '==', tipo).get();
    const antigos = snap.docs.sort((a, b) => b.get('criadoEm').toMillis() - a.get('criadoEm').toMillis()).slice(manter);
    for (const doc of antigos) {
        await bucket.file(doc.get('caminho')).delete({ ignoreNotFound: true });
        await doc.ref.delete();
    }
}

async function gravarBackupDoBanco() {
    const bucket = admin.storage().bucket(BUCKET);
    const { conteudo } = await montarBanco(bucket);
    const { data, hora } = agoraEmBrasilia();
    const texto = JSON.stringify(conteudo, null, 1);
    const caminho = `backups/diario/banco-${data}.json`;
    await bucket.file(caminho).save(texto, {
        resumable: false,
        contentType: 'application/json; charset=utf-8',
        metadata: {
            contentDisposition: `attachment; filename="banco-${data}.json"`,
            metadata: { firebaseStorageDownloadTokens: randomUUID() },
        },
    });
    const registros = totalDeRegistros(conteudo.colecoes);
    const titulo = `${data.split('-').reverse().join('/')} ${hora.slice(0, 2)}:${hora.slice(2)}`;
    await registrar(`diario-${data}`, 'diario', titulo, caminho, Buffer.byteLength(texto), registros);
    await apagarAntigos(bucket, 'diario', DIAS_GUARDADOS);
    return { caminho, registros };
}

function celula(valor) {
    const texto = valor !== null && typeof valor === 'object' ? JSON.stringify(valor) : String(valor ?? '');
    return `"${texto.replace(/"/g, '""')}"`;
}

// Planilha por coleção (abre no Excel)
function paraCsv(documentos) {
    const colunas = [...new Set(documentos.flatMap((d) => Object.keys(d.dados)))];
    const linhas = [['id', ...colunas].map(celula).join(';')];
    for (const d of documentos) linhas.push([d.id, ...colunas.map((c) => d.dados[c])].map(celula).join(';'));
    return '﻿' + linhas.join('\r\n');
}

const LEIA_ME = `BACKUP COMPLETO — CONDOMÍNIO SOCIEDADE MORRO GRANDE
====================================================

Este pacote contém TODOS os dados do aplicativo, para que o condomínio nunca dependa de uma única
empresa ou pessoa. Guarde-o em um lugar seguro: ele contém dados pessoais (nomes, CPFs, telefones),
protegidos pela LGPD. Entregue-o somente a quem o condomínio autorizar.

O QUE TEM AQUI
- banco.json ........ todo o banco de dados, com todas as coleções e subcoleções.
- planilhas/ ........ uma planilha (CSV) por coleção, para abrir no Excel e consultar sem programar.
- arquivos/ ......... todos os arquivos enviados: boletos, fotos, atas, notas e anexos, nas mesmas
                      pastas em que estavam.
- restaurar_backup.mjs  programa que coloca tudo em um novo projeto do Firebase.
- LEIA-ME.txt ....... este texto.

COMO LER O banco.json
- "colecoes": cada coleção é uma lista de documentos com "id", "dados" e "subcolecoes".
- "usuarios": contas de login, SEM senhas (ver abaixo).
- "arquivos": lista dos arquivos, com caminho, tamanho e tipo.
- Datas aparecem como {"__tipo":"data","valor":"2026-10-30T12:00:00.000Z"}.

COLEÇÕES PRINCIPAIS
- moradores, administradores, solicitacoes_admin .. pessoas e acessos
- boletos, boletos_meses .......................... boletos por morador e meses criados
- leituras_agua ................................... leituras do hidrômetro
- avisos, assembleias, despesas, ocorrencias, manutencoes .. cadastros do condomínio
- meses_cadastro .................................. meses criados em cada cadastro
- funcionarios .................................... funcionários
- conversas, conversasAdmin ....................... chats (com a subcoleção "mensagens")
- config, pagamentosAssinatura .................... configurações e assinatura do app

MENSAGENS DOS CHATS
O texto das mensagens fica cifrado, começando com "ENC1:" (AES-256-GCM: base64 de 12 bytes de IV +
texto cifrado + 16 bytes de tag). Para ler, use a chave em base64:
NIYJ8BQYYrEbXaBbFWKG/AcgGY2IhZfmY3wQHCi2A8s=

SENHAS
Nenhum sistema entrega as senhas das pessoas, nem este. Em um app novo cada pessoa precisa de uma
senha nova. Moradores: o login é quadra + lote, e a senha inicial do app são os 6 primeiros dígitos
do CPF do responsável.

COMO LEVAR PARA OUTRO APLICATIVO
Caminho 1, outro app que use Firebase (o mais simples):
  1. Crie um projeto novo em console.firebase.google.com, com Firestore e Storage ativados.
  2. Em Configurações do projeto > Contas de serviço, gere uma chave privada (arquivo .json).
  3. Instale o Node.js 20 ou mais novo. Nesta pasta, rode:
       npm install firebase-admin
       node restaurar_backup.mjs banco.json chave.json meu-projeto.firebasestorage.app arquivos
  4. O programa recria as coleções, envia os arquivos e corrige os links deles.
  5. Crie as contas de login no Authentication do projeto novo.

Caminho 2, outro sistema qualquer:
  Entregue este pacote à empresa escolhida. banco.json e as planilhas são formatos abertos, que
  qualquer desenvolvedor consegue importar; este LEIA-ME explica a estrutura.

Os backups diários (banco-AAAA-MM-DD.json) têm só o banco, sem os arquivos. Para levar tudo embora,
use sempre o Pacote completo.
`;

async function gravarPacoteCompleto(scriptRestauracao) {
    const bucket = admin.storage().bucket(BUCKET);
    const { conteudo, arquivos } = await montarBanco(bucket);
    const { data, hora } = agoraEmBrasilia();
    const nome = `pacote-completo-${data}-${hora}.zip`;
    const caminho = `backups/pacotes/${nome}`;
    const destino = bucket.file(caminho).createWriteStream({
        resumable: true,
        contentType: 'application/zip',
        metadata: {
            contentDisposition: `attachment; filename="${nome}"`,
            metadata: { firebaseStorageDownloadTokens: randomUUID() },
        },
    });
    const zip = archiver('zip', { zlib: { level: 6 } });
    const fim = new Promise((resolve, reject) => {
        destino.on('finish', resolve);
        destino.on('error', reject);
        zip.on('error', reject);
    });
    zip.pipe(destino);
    zip.append(LEIA_ME, { name: 'LEIA-ME.txt' });
    zip.append(JSON.stringify(conteudo, null, 1), { name: 'banco.json' });
    zip.append(scriptRestauracao, { name: 'restaurar_backup.mjs' });
    for (const [colecao, documentos] of Object.entries(conteudo.colecoes)) {
        zip.append(paraCsv(documentos), { name: `planilhas/${colecao}.csv` });
    }
    for (const arquivo of arquivos) zip.append(arquivo.createReadStream(), { name: `arquivos/${arquivo.name}` });
    await zip.finalize();
    await fim;

    const [metadados] = await bucket.file(caminho).getMetadata();
    const titulo = `${data.split('-').reverse().join('/')} ${hora.slice(0, 2)}:${hora.slice(2)}`;
    await registrar(`pacote-${data}-${hora}`, 'pacote', titulo, caminho, Number(metadados.size), totalDeRegistros(conteudo.colecoes));
    await apagarAntigos(bucket, 'pacote', PACOTES_GUARDADOS);
    return { caminho };
}

async function exigirAdmin(request) {
    if (!request.auth) throw new HttpsError('unauthenticated', 'Usuário não autenticado.');
    const doc = await admin.firestore().collection('administradores').doc(request.auth.uid).get();
    if (!doc.exists || doc.get('tipo') !== 'admin') throw new HttpsError('permission-denied', 'Só administradores fazem backup.');
}

exports.gravarBackupDoBanco = gravarBackupDoBanco;
exports.gravarPacoteCompleto = gravarPacoteCompleto;

exports.backupDiario = onSchedule({
    schedule: '0 0 * * *', timeZone: 'America/Sao_Paulo', memory: '1GiB', timeoutSeconds: 540, retryCount: 2,
}, async () => {
    const { caminho, registros } = await gravarBackupDoBanco();
    console.log(`Backup diário: ${caminho} (${registros} registros)`);
});

exports.gerarBackupAgora = onCall({ memory: '1GiB', timeoutSeconds: 540 }, async (request) => {
    await exigirAdmin(request);
    return gravarBackupDoBanco();
});

exports.gerarPacoteCompleto = onCall({ memory: '2GiB', timeoutSeconds: 540 }, async (request) => {
    await exigirAdmin(request);
    const script = require('fs').readFileSync(require('path').join(__dirname, 'restaurar_backup.mjs'), 'utf8');
    return gravarPacoteCompleto(script);
});
