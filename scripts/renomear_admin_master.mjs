// Troca o usuário das duas contas master de "Condominio sociedade Morro grande" para
// "Condomínio Morro Grande" e define a senha nova (a mesma nas duas):
//  - admin master (app Admins / painel web);
//  - morador de visualização (app Moradores, verTodos).
// São as mesmas contas (mesmo UID), então continuam com tudo o que tinham.
// Depois entra com o usuário novo nas duas e confere.
//
// Uso (a senha é pedida no terminal e não fica gravada em lugar nenhum):
//   node scripts/renomear_admin_master.mjs              -> Firebase de verdade (usa o login do gcloud)
//   node scripts/renomear_admin_master.mjs --emulador   -> emuladores locais (teste)
import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";

const PROJETO = "condominio-morro-grande";
const USUARIO = "Condomínio Morro Grande";
const login = (u) => u.normalize("NFD").replace(/[^A-Za-z0-9]/g, "").toLowerCase();
const EMAIL_ANTIGO = login("Condominio sociedade Morro grande") + "@admins.morrogrande.app";
const EMAIL_NOVO = login(USUARIO) + "@admins.morrogrande.app";

const emulador = process.argv.includes("--emulador");
const AUTH = emulador ? "http://127.0.0.1:9099/identitytoolkit.googleapis.com" : "https://identitytoolkit.googleapis.com";
const FS = (emulador ? "http://127.0.0.1:8085" : "https://firestore.googleapis.com")
  + `/v1/projects/${PROJETO}/databases/(default)/documents`;
const CHAVE_API = emulador ? "teste"
  : JSON.parse(readFileSync(new URL("../app/google-services.json", import.meta.url))).client[0].api_key[0].current_key;

function tokenDoDono() {
  if (emulador) return "owner";
  const sdk = `${process.env.LOCALAPPDATA}/Google/Cloud SDK/google-cloud-sdk`;
  return execSync(`"${sdk}/bin/gcloud.cmd" auth print-access-token`, {
    env: { ...process.env, CLOUDSDK_PYTHON: `${sdk}/platform/bundledpython/python.exe` },
  }).toString().trim();
}

async function pedirSenha() {
  if (process.env.SENHA_MASTER) return process.env.SENHA_MASTER;
  process.stdout.write(`Senha nova para "${USUARIO}": `);
  return new Promise((ok) => {
    let senha = "";
    process.stdin.setRawMode?.(true);
    process.stdin.resume();
    process.stdin.setEncoding("utf8");
    process.stdin.on("data", function tecla(c) {
      if (c === "\r" || c === "\n") {
        process.stdin.setRawMode?.(false);
        process.stdin.pause();
        process.stdin.off("data", tecla);
        process.stdout.write("\n");
        ok(senha);
      } else if (c === "\u0003") {
        process.exit(1);
      } else if (c === "\u0008" || c === "\u007f") {
        senha = senha.slice(0, -1);
      } else {
        senha += c;
      }
    });
  });
}

async function chamar(url, corpo, cabecalhos, metodo = "POST") {
  const r = await fetch(url, {
    method: metodo,
    headers: { "Content-Type": "application/json", ...cabecalhos },
    body: corpo ? JSON.stringify(corpo) : undefined,
  });
  const texto = await r.text();
  return { ok: r.ok, status: r.status, json: texto ? JSON.parse(texto) : {} };
}

async function uidDoEmail(dono, email) {
  const r = await chamar(`${AUTH}/v1/projects/${PROJETO}/accounts:lookup`, { email: [email] }, dono);
  return r.json.users?.[0]?.localId || null;
}

const senha = await pedirSenha();
if (senha.length < 6) throw new Error("A senha precisa ter pelo menos 6 caracteres.");
const dono = { Authorization: `Bearer ${tokenDoDono()}`, ...(emulador ? {} : { "x-goog-user-project": PROJETO }) };
console.log(emulador ? "Alvo: EMULADORES locais" : `Alvo: Firebase ${PROJETO} (produção)`);

// Já renomeado antes? Então só troca a senha.
const uidAntigo = await uidDoEmail(dono, EMAIL_ANTIGO), uidNovo = await uidDoEmail(dono, EMAIL_NOVO);
if (uidAntigo && uidNovo && uidAntigo !== uidNovo) throw new Error(`Já existe outra conta com o usuário "${USUARIO}" no app Admins (${uidNovo}).`);
const uid = uidAntigo || uidNovo;
if (!uid) throw new Error(`Conta do admin master não encontrada (${EMAIL_ANTIGO}).`);
const doc = await chamar(`${FS}/administradores/${uid}`, null, dono, "GET");
if (!doc.json.fields?.master?.booleanValue) throw new Error(`administradores/${uid} não é o admin master.`);

const trocada = await chamar(`${AUTH}/v1/projects/${PROJETO}/accounts:update`,
  { localId: uid, email: EMAIL_NOVO, password: senha, displayName: USUARIO }, dono);
if (!trocada.ok) throw new Error(`Auth: ${JSON.stringify(trocada.json)}`);

const novos = {
  nome: USUARIO, nomeLowercase: USUARIO.toLowerCase(), primeiroNome: "Condomínio", primeiroNomeLowercase: "condomínio",
  email: EMAIL_NOVO, usuario: USUARIO,
};
const mascara = Object.keys(novos).map((k) => `updateMask.fieldPaths=${k}`).join("&");
const campos = Object.fromEntries(Object.entries(novos).map(([k, v]) => [k, { stringValue: v }]));
const gravado = await chamar(`${FS}/administradores/${uid}?${mascara}`, { fields: campos }, dono, "PATCH");
if (!gravado.ok) throw new Error(`administradores/${uid}: ${JSON.stringify(gravado.json)}`);
console.log(`Admin master renomeado para "${USUARIO}":`, uid);

// ---- Validação: entra com o usuário novo e a senha nova
const entrou = await chamar(`${AUTH}/v1/accounts:signInWithPassword?key=${CHAVE_API}`,
  { email: EMAIL_NOVO, password: senha, returnSecureToken: true }, {});
if (!entrou.ok) throw new Error(`Login com o usuário novo falhou: ${entrou.json.error?.message}`);
const conferido = await chamar(`${FS}/administradores/${uid}`, null, { Authorization: `Bearer ${entrou.json.idToken}` }, "GET");
console.log(conferido.ok && conferido.json.fields?.master?.booleanValue
  ? `OK  "${USUARIO}" entra com a senha nova e é master` : "ERRO admin master", conferido.status);

// ---- Morador de visualização (app Moradores)
const MORADOR_ANTIGO = login("Condominio sociedade Morro grande") + "@moradores.morrogrande.app";
const MORADOR_NOVO = login(USUARIO) + "@moradores.morrogrande.app";
const morAntigo = await uidDoEmail(dono, MORADOR_ANTIGO), morNovo = await uidDoEmail(dono, MORADOR_NOVO);
if (morAntigo && morNovo && morAntigo !== morNovo) throw new Error(`Já existe outra conta com o usuário "${USUARIO}" no app Moradores (${morNovo}).`);
const uidMorador = morAntigo || morNovo;
if (!uidMorador) throw new Error(`Conta master do app Moradores não encontrada (${MORADOR_ANTIGO}).`);
const cadastro = await chamar(`${FS}/moradores/${uidMorador}`, null, dono, "GET");
if (!cadastro.json.fields?.verTodos?.booleanValue) throw new Error(`moradores/${uidMorador} não é a conta master.`);

const trocadaMorador = await chamar(`${AUTH}/v1/projects/${PROJETO}/accounts:update`,
  { localId: uidMorador, email: MORADOR_NOVO, password: senha, displayName: USUARIO }, dono);
if (!trocadaMorador.ok) throw new Error(`Auth (morador): ${JSON.stringify(trocadaMorador.json)}`);
const novosMorador = { nome: USUARIO, usuario: USUARIO, loginEmail: MORADOR_NOVO };
const mascaraMorador = Object.keys(novosMorador).map((k) => `updateMask.fieldPaths=${k}`).join("&");
const camposMorador = Object.fromEntries(Object.entries(novosMorador).map(([k, v]) => [k, { stringValue: v }]));
const gravadoMorador = await chamar(`${FS}/moradores/${uidMorador}?${mascaraMorador}`, { fields: camposMorador }, dono, "PATCH");
if (!gravadoMorador.ok) throw new Error(`moradores/${uidMorador}: ${JSON.stringify(gravadoMorador.json)}`);
console.log(`Master do app Moradores renomeado para "${USUARIO}":`, uidMorador);

const entrouMorador = await chamar(`${AUTH}/v1/accounts:signInWithPassword?key=${CHAVE_API}`,
  { email: MORADOR_NOVO, password: senha, returnSecureToken: true }, {});
if (!entrouMorador.ok) throw new Error(`Login no app Moradores falhou: ${entrouMorador.json.error?.message}`);
const conferidoMorador = await chamar(`${FS}/moradores/${uidMorador}`, null, { Authorization: `Bearer ${entrouMorador.json.idToken}` }, "GET");
console.log(conferidoMorador.ok && conferidoMorador.json.fields?.verTodos?.booleanValue
  ? `OK  "${USUARIO}" entra no app Moradores com a senha nova e é a conta master` : "ERRO master do app Moradores", conferidoMorador.status);
