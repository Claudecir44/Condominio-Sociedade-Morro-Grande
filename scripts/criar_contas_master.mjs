// Cria (ou atualiza a senha de) duas contas com o usuário "Condominio sociedade Morro grande":
//  - admin master: login no app Admins / painel web, todos os poderes, ninguém remove;
//  - morador de visualização: login no app Moradores, usado pelos admins pra ver as telas do
//    morador; vê a lista de todos os moradores (sem editar) e não precisa cadastrar foto.
// Depois entra com as duas contas e confere o que cada uma consegue fazer.
//
// Uso (a senha é pedida no terminal e não fica gravada em lugar nenhum):
//   node scripts/criar_contas_master.mjs              -> Firebase de verdade (usa o login do gcloud)
//   node scripts/criar_contas_master.mjs --emulador   -> emuladores locais (teste)
import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";

const PROJETO = "condominio-morro-grande";
const USUARIO = "Condominio sociedade Morro grande";
const LOGIN = USUARIO.normalize("NFD").replace(/[^A-Za-z0-9]/g, "").toLowerCase();
const EMAIL_ADMIN = LOGIN + "@admins.morrogrande.app";
const EMAIL_MORADOR = LOGIN + "@moradores.morrogrande.app";

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
  process.stdout.write(`Senha para "${USUARIO}": `);
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

function campos(obj) {
  const f = {};
  for (const [k, v] of Object.entries(obj)) {
    f[k] = typeof v === "boolean" ? { booleanValue: v } : { stringValue: String(v) };
  }
  return { fields: f };
}

/** Cria a conta no Auth ou, se já existir, troca a senha. Devolve o UID. */
async function contaComSenha(dono, email, senha) {
  const criada = await chamar(`${AUTH}/v1/projects/${PROJETO}/accounts`, { email, password: senha, displayName: USUARIO }, dono);
  if (criada.ok) return criada.json.localId;
  if (!JSON.stringify(criada.json).includes("EMAIL_EXISTS")) throw new Error(`${email}: ${JSON.stringify(criada.json)}`);
  const achada = await chamar(`${AUTH}/v1/projects/${PROJETO}/accounts:lookup`, { email: [email] }, dono);
  const uid = achada.json.users?.[0]?.localId;
  if (!uid) throw new Error(`${email}: conta existe mas não foi encontrada`);
  const trocada = await chamar(`${AUTH}/v1/projects/${PROJETO}/accounts:update`, { localId: uid, password: senha }, dono);
  if (!trocada.ok) throw new Error(`${email}: ${JSON.stringify(trocada.json)}`);
  return uid;
}

async function gravar(dono, caminho, obj) {
  const r = await chamar(`${FS}/${caminho}`, campos(obj), dono, "PATCH");
  if (!r.ok) throw new Error(`${caminho}: ${JSON.stringify(r.json)}`);
}

async function entrar(email, senha) {
  const r = await chamar(`${AUTH}/v1/accounts:signInWithPassword?key=${CHAVE_API}`, { email, password: senha, returnSecureToken: true }, {});
  if (!r.ok) throw new Error(`Login de ${email} falhou: ${r.json.error?.message}`);
  return { Authorization: `Bearer ${r.json.idToken}` };
}

const senha = await pedirSenha();
if (senha.length < 6) throw new Error("A senha precisa ter pelo menos 6 caracteres.");
const dono = { Authorization: `Bearer ${tokenDoDono()}`, ...(emulador ? {} : { "x-goog-user-project": PROJETO }) };
console.log(emulador ? "Alvo: EMULADORES locais" : `Alvo: Firebase ${PROJETO} (produção)`);

const uidAdmin = await contaComSenha(dono, EMAIL_ADMIN, senha);
await gravar(dono, `administradores/${uidAdmin}`, {
  nome: USUARIO, nomeLowercase: USUARIO.toLowerCase(), primeiroNome: "Condominio", primeiroNomeLowercase: "condominio",
  email: EMAIL_ADMIN, usuario: USUARIO, telefone: "", tipo: "admin", master: true,
});
console.log("Admin master pronto:", uidAdmin);

const uidMorador = await contaComSenha(dono, EMAIL_MORADOR, senha);
const cadastroMorador = {
  nome: USUARIO, cod: uidMorador, usuario: USUARIO, loginEmail: EMAIL_MORADOR,
  cpf: "", email: "", rua: "", numero: "", telefone: "", quadra: "", lote: "", foto: "", verTodos: true,
};
await gravar(dono, `moradores/${uidMorador}`, cadastroMorador);
console.log("Morador de visualização pronto:", uidMorador);

// ---- Validação: entra com as duas contas e confere o que cada uma pode fazer
const comoAdmin = await entrar(EMAIL_ADMIN, senha);
const doc = await chamar(`${FS}/administradores/${uidAdmin}`, null, comoAdmin, "GET");
console.log(doc.ok && doc.json.fields?.master?.booleanValue ? "OK  admin master entra e é master" : "ERRO admin master", doc.status);

const comoMorador = await entrar(EMAIL_MORADOR, senha);
const lista = await chamar(`${FS}/moradores?pageSize=300`, null, comoMorador, "GET");
const qtd = (lista.json.documents || []).filter((d) => !d.fields?.verTodos?.booleanValue).length;
console.log(lista.ok ? `OK  morador de visualização entra e vê a lista (${qtd} moradores)` : "ERRO lista de moradores", lista.status);

const editar = await chamar(`${FS}/moradores/${uidMorador}?updateMask.fieldPaths=nome`, campos({ nome: "teste" }), comoMorador, "PATCH");
console.log(!editar.ok ? "OK  morador de visualização não consegue editar cadastros" : "ERRO morador conseguiu editar!", editar.status);
if (editar.ok) await gravar(dono, `moradores/${uidMorador}`, cadastroMorador);
