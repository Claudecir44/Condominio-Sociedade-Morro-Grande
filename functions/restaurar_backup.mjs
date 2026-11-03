// Restaura um backup do Condomínio Sociedade Morro Grande em OUTRO projeto do Firebase.
// Vai dentro do Pacote completo; as instruções estão no LEIA-ME.txt.
//
// Uso:
//   npm install firebase-admin
//   node restaurar_backup.mjs banco.json chave.json meu-projeto.firebasestorage.app [pasta-arquivos]
//
// banco.json: o backup; chave.json: chave da conta de serviço do projeto NOVO; o terceiro argumento
// é o bucket do Storage do projeto novo; pasta-arquivos: a pasta "arquivos" do Pacote completo.
// Não apaga nada do projeto novo. Documentos com o mesmo id são substituídos.
import { createRequire } from 'module';
import fs from 'fs';
import path from 'path';

const require = createRequire(path.join(process.cwd(), 'x.js'));
const admin = require('firebase-admin');
const { Timestamp, GeoPoint } = require('firebase-admin/firestore');

const [arquivoBanco, arquivoChave, bucketNovo, pastaArquivos] = process.argv.slice(2);
if (!arquivoBanco || !arquivoChave || !bucketNovo) {
    console.error('Uso: node restaurar_backup.mjs banco.json chave.json meu-projeto.firebasestorage.app [pasta-arquivos]');
    process.exit(1);
}

const backup = JSON.parse(fs.readFileSync(arquivoBanco, 'utf8'));
const chave = JSON.parse(fs.readFileSync(arquivoChave, 'utf8'));
if (backup.formato !== 'morro-grande-backup-v1') throw new Error('Este arquivo não é um backup do Morro Grande.');

admin.initializeApp({ credential: admin.credential.cert(chave), storageBucket: bucketNovo });
const db = admin.firestore();
const bucket = admin.storage().bucket();

// Desfaz as marcas "__tipo" e troca o bucket antigo pelo novo nos links dos arquivos
function restaurar(valor) {
    if (typeof valor === 'string') return valor.split(backup.bucket).join(bucketNovo);
    if (Array.isArray(valor)) return valor.map(restaurar);
    if (valor && typeof valor === 'object') {
        if (valor.__tipo === 'data') return Timestamp.fromDate(new Date(valor.valor));
        if (valor.__tipo === 'local') return new GeoPoint(valor.latitude, valor.longitude);
        if (valor.__tipo === 'referencia') return db.doc(valor.caminho);
        return Object.fromEntries(Object.entries(valor).map(([k, v]) => [k, restaurar(v)]));
    }
    return valor;
}

let gravados = 0;
async function gravarColecao(colecao, documentos) {
    for (let i = 0; i < documentos.length; i += 400) {
        const lote = db.batch();
        for (const d of documentos.slice(i, i + 400)) lote.set(colecao.doc(d.id), restaurar(d.dados));
        await lote.commit();
        gravados += Math.min(400, documentos.length - i);
    }
    for (const d of documentos) {
        for (const [nome, sub] of Object.entries(d.subcolecoes || {})) {
            await gravarColecao(colecao.doc(d.id).collection(nome), sub);
        }
    }
}

for (const [nome, documentos] of Object.entries(backup.colecoes)) {
    console.log(`Coleção ${nome}: ${documentos.length} documento(s)`);
    await gravarColecao(db.collection(nome), documentos);
}
console.log(`Banco restaurado: ${gravados} registros.`);

if (pastaArquivos) {
    let enviados = 0;
    for (const a of backup.arquivos) {
        const local = path.join(pastaArquivos, a.caminho);
        if (!fs.existsSync(local)) {
            console.warn(`Arquivo não encontrado: ${a.caminho}`);
            continue;
        }
        // Mesmo token de antes: assim os links gravados no banco continuam valendo
        await bucket.upload(local, {
            destination: a.caminho,
            metadata: { contentType: a.tipo, metadata: a.token ? { firebaseStorageDownloadTokens: a.token } : {} },
        });
        enviados += 1;
    }
    console.log(`Arquivos enviados: ${enviados} de ${backup.arquivos.length}.`);
}
console.log(`Falta criar as contas de login no Authentication do projeto novo (${backup.usuarios.length} no backup, sem senhas).`);
process.exit(0);
