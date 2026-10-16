// Cria dados de TESTE nos emuladores locais (nunca no Firebase de verdade).
// Uso: node scripts/emulador/semear.mjs   (com os emuladores rodando)
// Login de teste no painel (http://localhost:5000/?emulador):
//   e-mail: admin.teste@condominio.test   senha: 123456
const PROJETO = "condominio-morro-grande";
const AUTH = "http://127.0.0.1:9099";
const FS = `http://127.0.0.1:8085/v1/projects/${PROJETO}/databases/(default)/documents`;
const DONO = { "Content-Type": "application/json", Authorization: "Bearer owner" };

async function criarConta(email, senha) {
  const r = await fetch(`${AUTH}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=teste`, {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password: senha, returnSecureToken: true }),
  });
  const j = await r.json();
  if (!j.localId) throw new Error(JSON.stringify(j));
  return j.localId;
}
function campos(obj) {
  const f = {};
  for (const [k, v] of Object.entries(obj)) {
    if (Array.isArray(v)) f[k] = { arrayValue: { values: v.map((s) => ({ stringValue: s })) } };
    else if (typeof v === "number") f[k] = { doubleValue: v };
    else f[k] = { stringValue: String(v) };
  }
  return { fields: f };
}
async function gravar(caminho, obj) {
  const r = await fetch(`${FS}/${caminho}`, { method: "PATCH", headers: DONO, body: JSON.stringify(campos(obj)) });
  if (!r.ok) throw new Error(caminho + ": " + (await r.text()));
}

const uid = await criarConta("admin.teste@condominio.test", "123456");
await gravar(`administradores/${uid}`, {
  nome: "Admin Teste", nomeLowercase: "admin teste", primeiroNome: "Admin", primeiroNomeLowercase: "admin",
  email: "admin.teste@condominio.test", tipo: "admin", telefone: "",
});
await gravar("config/setup", { primeiroAdmin: uid });
await gravar("avisos/aviso1", {
  datahora: "16/10/2026 09:00", assunto: "Aviso de teste", descricao: "Texto do aviso de teste.",
  anexos: "[]", timestamp: Date.now(),
});
console.log("Emulador semeado. Admin de teste:", uid);
