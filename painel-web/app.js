// Painel web do Condomínio Sociedade Morro Grande.
// Mesmo Firebase e mesmo formato de dados do app Android (Administradores/Moradores),
// então o que é salvo aqui aparece no app e vice-versa.
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut, createUserWithEmailAndPassword, connectAuthEmulator
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import {
  getFirestore, collection, doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, writeBatch, query, where, orderBy, onSnapshot,
  serverTimestamp, increment, deleteField, arrayUnion, connectFirestoreEmulator
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";
import {
  getStorage, ref as refArquivo, uploadBytes, getDownloadURL, connectStorageEmulator
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-storage.js";
import { getFunctions, httpsCallable } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-functions.js";

const config = {
  apiKey: "AIzaSyARNrw2L-AMvnj9DMWV93tJ1n_MUqiwfIQ",
  authDomain: "condominio-morro-grande.firebaseapp.com",
  projectId: "condominio-morro-grande",
  storageBucket: "condominio-morro-grande.firebasestorage.app",
  messagingSenderId: "1076953386789",
  appId: "1:1076953386789:web:5145196c5b1f4b58c394cc",
};

const app = initializeApp(config);
const auth = getAuth(app);
const db = getFirestore(app);
const storage = getStorage(app);
const funcoes = getFunctions(app, "southamerica-east1");
// Instância separada para criar contas (morador/admin) sem deslogar quem está no painel
const authCadastro = getAuth(initializeApp(config, "cadastro-contas"));

// Só para testes no computador: http://localhost:5000/?emulador usa os emuladores locais
if (location.hostname === "localhost" && new URLSearchParams(location.search).has("emulador")) {
  connectAuthEmulator(auth, "http://localhost:9099", { disableWarnings: true });
  connectAuthEmulator(authCadastro, "http://localhost:9099", { disableWarnings: true });
  connectFirestoreEmulator(db, "localhost", 8085);
  connectStorageEmulator(storage, "localhost", 9199);
}

const $ = (id) => document.getElementById(id);
const LIMITE_ARQUIVO = 20 * 1024 * 1024;
const DOMINIO_MORADOR = "@moradores.morrogrande.app";
const DOMINIO_ADMIN = "@admins.morrogrande.app";

const FUNCOES = [
  "Síndico", "Subsíndico", "Administrador(a)", "Gerente predial", "Porteiro", "Segurança",
  "Vigia noturno", "Controlador de acesso", "Zelador", "Serviços gerais", "Auxiliar de limpeza",
  "Jardineiro", "Piscineiro", "Manutenção", "Eletricista", "Encanador", "Recepcionista",
  "Contador", "Financeiro", "Outros",
];

// ---------------------------------------------------------------- seções
// campos: k (nome no banco), r (rótulo), t (tipo), largo, obrig
// anexos: "texto" = JSON em texto (avisos/assembleias/ocorrências, como no app); "lista" = array
const SECOES = [
  {
    id: "avisos", titulo: "Avisos", cor: "#EF6C00", colecao: "avisos", modulo: "avisos", anexos: "texto",
    campoData: "datahora", gravaId: false,
    campos: [
      { k: "datahora", r: "Data/Hora", t: "datahora", obrig: true },
      { k: "assunto", r: "Assunto", obrig: true },
      { k: "descricao", r: "Descrição", t: "textarea", largo: true, obrig: true },
    ],
    extra: () => ({ timestamp: Date.now() }),
    titulo_: (r) => r.assunto, sub: (r) => r.datahora, longo: (r) => r.descricao,
  },
  {
    id: "assembleias", titulo: "Assembleias", cor: "#8E24AA", colecao: "assembleias", modulo: "assembleias", anexos: "texto",
    campoData: "datahora", gravaId: true,
    campos: [
      { k: "datahora", r: "Data/Hora", t: "datahora", obrig: true },
      { k: "assunto", r: "Assunto", obrig: true },
      { k: "local", r: "Local", obrig: true },
      { k: "descricao", r: "Descrição / ata", t: "textarea", largo: true, obrig: true },
    ],
    extra: (r) => ({ documento: r.documento || "[]" }),
    titulo_: (r) => r.assunto, sub: (r) => `${r.datahora || ""} · ${r.local || ""}`, longo: (r) => r.descricao,
  },
  {
    id: "despesas", titulo: "Despesas", cor: "#2E7D32", colecao: "despesas", modulo: "despesas", anexos: "lista",
    campoData: "dataHora", gravaId: true,
    campos: [
      { k: "dataHora", r: "Data/Hora", t: "datahora", obrig: true },
      { k: "nome", r: "Nome da despesa", obrig: true },
      { k: "valor", r: "Valor (R$)", t: "dinheiro", obrig: true },
      { k: "descricao", r: "Descrição", t: "textarea", largo: true, obrig: true },
    ],
    titulo_: (r) => r.nome, sub: (r) => r.dataHora, longo: (r) => r.descricao,
    detalhes: (r) => [["Valor", dinheiro(r.valor)]],
  },
  {
    id: "ocorrencias", titulo: "Ocorrências", cor: "#D32F2F", colecao: "ocorrencias", modulo: "ocorrencias", anexos: "texto",
    campoData: "datahora", gravaId: true,
    campos: [
      { k: "tipo", r: "Tipo", obrig: true },
      { k: "datahora", r: "Data/Hora", t: "datahora", obrig: true },
      { k: "envolvidos", r: "Envolvidos (até 5)", t: "envolvidos", largo: true },
      { k: "descricao", r: "Descrição", t: "textarea", largo: true, obrig: true },
    ],
    titulo_: (r) => r.tipo, sub: (r) => r.datahora, longo: (r) => r.descricao,
    detalhes: (r) => [["Envolvidos", listaDeTexto(r.envolvidos).join(", ")]],
  },
  {
    id: "manutencoes", titulo: "Manutenção", cor: "#6D4C41", colecao: "manutencoes", modulo: "manutencoes", anexos: "lista",
    campoData: "dataHora", gravaId: true,
    campos: [
      { k: "tipo", r: "Tipo", obrig: true },
      { k: "dataHora", r: "Data/Hora", t: "datahora", obrig: true },
      { k: "local", r: "Local" },
      { k: "servico", r: "Serviço" },
      { k: "responsavel", r: "Responsável" },
      { k: "valor", r: "Valor" },
      { k: "notas", r: "Notas", t: "textarea", largo: true },
    ],
    titulo_: (r) => r.tipo, sub: (r) => r.dataHora, longo: (r) => r.notas,
    detalhes: (r) => [["Local", r.local], ["Serviço", r.servico], ["Responsável", r.responsavel], ["Valor", r.valor]],
  },
  {
    id: "funcionarios", titulo: "Funcionários", cor: "#00897B", colecao: "funcionarios", modulo: "funcionarios", anexos: "lista",
    campoNome: "nome", gravaId: true, foto: "imagemUri",
    campos: [
      { k: "nome", r: "Nome", obrig: true },
      { k: "cargo", r: "Função", t: "funcao", obrig: true },
      { k: "imagemUri", r: "Foto", t: "foto", largo: true },
      { k: "telefone", r: "Telefone", t: "tel" },
      { k: "email", r: "E-mail", t: "email" },
      { k: "rg", r: "RG" },
      { k: "cpf", r: "CPF" },
      { k: "rua", r: "Rua" },
      { k: "numero", r: "Número" },
      { k: "bairro", r: "Bairro" },
      { k: "cep", r: "CEP" },
      { k: "cidade", r: "Cidade" },
      { k: "estado", r: "Estado" },
      { k: "pais", r: "País" },
      { k: "cargaHoraria", r: "Carga horária mensal" },
      { k: "turno", r: "Turno diário" },
      { k: "horaEntrada", r: "Hora de entrada", t: "hora" },
      { k: "horaSaida", r: "Hora de saída", t: "hora" },
    ],
    titulo_: (r) => r.nome, sub: (r) => r.cargo ? "Função: " + r.cargo : "",
    detalhes: (r) => [["Telefone", r.telefone], ["E-mail", r.email], ["CPF", r.cpf], ["Turno", r.turno],
      ["Horário", [r.horaEntrada, r.horaSaida].filter(Boolean).join(" às ")],
      ["Endereço", [r.rua, r.numero, r.bairro, r.cidade, r.estado].filter(Boolean).join(", ")]],
  },
  {
    id: "moradores", titulo: "Moradores", cor: "#1E88E5", colecao: "moradores", modulo: "moradores", anexos: "lista",
    campoNome: "nome", foto: "foto", especial: "morador",
    campos: [
      { k: "nome", r: "Nome", obrig: true },
      { k: "cpf", r: "CPF", obrig: true },
      { k: "quadra", r: "Quadra", obrig: true, travaNaEdicao: true },
      { k: "lote", r: "Lote", obrig: true, travaNaEdicao: true },
      { k: "foto", r: "Foto (opcional — o morador cadastra no 1º acesso)", t: "foto", largo: true },
      { k: "telefone", r: "Telefone", t: "tel" },
      { k: "email", r: "E-mail", t: "email" },
      { k: "rua", r: "Rua" },
      { k: "numero", r: "Número" },
    ],
    titulo_: (r) => r.nome, sub: (r) => `Quadra ${r.quadra || "—"} · Lote ${r.lote || "—"}`,
    detalhes: (r) => [["Login", r.usuario], ["CPF", r.cpf], ["Telefone", r.telefone], ["E-mail", r.email],
      ["Endereço", [r.rua, r.numero].filter(Boolean).join(", ")]],
  },
  { id: "leituras", titulo: "Leitura d'Água", cor: "#0284C7", colecao: "leituras_agua", especial: "leituras", semNovo: true },
  { id: "administradores", titulo: "Administradores", cor: "#3949AB", colecao: "administradores", especial: "admins" },
  { id: "cadastro", titulo: "Cadastro", cor: "#3949AB", especial: "cadastro", semNovo: true },
  { id: "boletos", titulo: "Boletos Morador", cor: "#059669", colecao: "moradores", especial: "boletos", semNovo: true },
  { id: "listas", titulo: "Listas", cor: "#455A64", especial: "listas", semNovo: true },
  { id: "vermorador", titulo: "Ver como morador", cor: "#1E88E5", especial: "vermorador", semNovo: true },
  // Como no app, o Chat não fica junto dos quadros: abre pelo botão no alto, ao lado de "Sair"
  { id: "chat", titulo: "Chat com Moradores", cor: "#2563EB", especial: "chat", semNovo: true, semMenu: true },
  { id: "chatadmin", titulo: "Chat admin", cor: "#3949AB", especial: "chatadmin", semNovo: true, semMenu: true },
  { id: "backup", titulo: "Backup", cor: "#455A64", colecao: "backups", especial: "backup", semNovo: true },
];
// Mesma ordem dos quadros do painel do app Admins
// Dentro do quadro "Cadastro", na mesma ordem do app
const CADASTROS = ["administradores", "assembleias", "despesas", "avisos", "moradores", "ocorrencias", "manutencoes", "funcionarios"];
const ORDEM_DO_APP = ["cadastro", "boletos", "leituras", "listas", "vermorador", "backup", "chat", "chatadmin", ...CADASTROS];
for (const s of SECOES) if (CADASTROS.includes(s.id)) { s.semMenu = true; s.noCadastro = true; }
// Mês a mês, como em "Boletos Morador"
for (const s of SECOES) if (["assembleias", "despesas", "avisos", "ocorrencias", "manutencoes"].includes(s.id)) s.porMes = true;
SECOES.sort((a, b) => ORDEM_DO_APP.indexOf(a.id) - ORDEM_DO_APP.indexOf(b.id));
// Quadro "Listas": mesmos títulos do app; só consulta
const LISTAS = [["moradores", "Lista de Moradores"], ["assembleias", "Lista de Assembleias"],
  ["despesas", "Prestação de Contas"], ["avisos", "Avisos"]];
// Quadro "Ver como morador": mesmos quadros e mesma ordem do painel do morador; só consulta
const QUADROS_MORADOR = [
  { id: "despesas", titulo: "Prestação de Contas", cor: "#2E7D32" },
  { id: "avisos", titulo: "Avisos", cor: "#EF6C00" },
  { previa: previaLeitura, titulo: "Leitura d'Água", cor: "#0284C7" },
  { previa: previaPagamentos, titulo: "Pagamentos", cor: "#059669" },
  { id: "assembleias", titulo: "Assembleias", cor: "#8E24AA" },
];

// ---------------------------------------------------------------- utilidades
function aviso(texto) {
  const el = $("aviso");
  el.textContent = texto;
  el.classList.add("visivel");
  clearTimeout(aviso.t);
  aviso.t = setTimeout(() => el.classList.remove("visivel"), 3500);
}
function esc(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
function dinheiro(v) {
  const n = typeof v === "number" ? v : parseFloat(String(v ?? "").replace(/\./g, "").replace(",", "."));
  return isNaN(n) ? (v ?? "") : n.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}
function paraNumero(texto) {
  let s = String(texto).trim();
  if (s.includes(",")) s = s.replace(/\./g, "").replace(",", ".");
  return parseFloat(s);
}
// "dd/MM/yyyy HH:mm" <-> input datetime-local ("yyyy-MM-ddTHH:mm")
function dataParaInput(s) {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})\s+(\d{2}):(\d{2})/.exec(s || "");
  return m ? `${m[3]}-${m[2]}-${m[1]}T${m[4]}:${m[5]}` : "";
}
function inputParaData(v) {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(v || "");
  return m ? `${m[3]}/${m[2]}/${m[1]} ${m[4]}:${m[5]}` : "";
}
function agoraFormatado() {
  const d = new Date(), p = (n) => String(n).padStart(2, "0");
  return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
}
function dataEmMs(s) {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})(?:\s+(\d{2}):(\d{2}))?/.exec(s || "");
  return m ? new Date(+m[3], +m[2] - 1, +m[1], +(m[4] || 0), +(m[5] || 0)).getTime() : 0;
}
// Anexos podem estar como array ou como texto JSON
function listaDeTexto(v) {
  if (!v) return [];
  if (Array.isArray(v)) return v.filter(Boolean).map(String);
  try {
    const a = JSON.parse(v);
    return Array.isArray(a) ? a.filter(Boolean).map(String) : [String(v)];
  } catch { return String(v).trim() ? [String(v)] : []; }
}
function ehLink(s) { return /^https?:\/\//.test(s || ""); }
function nomeDoAnexo(s) {
  if (s instanceof File) return s.name;
  const i = (s || "").indexOf("/o/");
  if (i >= 0) {
    let r = s.substring(i + 3).split("?")[0];
    try { r = decodeURIComponent(r); } catch { /* mantém */ }
    return r.substring(r.lastIndexOf("/") + 1) || "arquivo";
  }
  return (s || "").split(/[\\/]/).pop() || "arquivo";
}
function tipoDoAnexo(nome) {
  const e = (nome.split(".").pop() || "").toLowerCase();
  if (["jpg", "jpeg", "png", "gif", "webp", "bmp", "heic"].includes(e)) return { img: true, rot: "IMG", cor: "#9333EA" };
  if (e === "pdf") return { rot: "PDF", cor: "#DC2626" };
  if (["doc", "docx", "odt", "rtf", "txt"].includes(e)) return { rot: "DOC", cor: "#2563EB" };
  if (["xls", "xlsx", "ods", "csv"].includes(e)) return { rot: "XLS", cor: "#16A34A" };
  return { rot: (e || "ARQ").slice(0, 4).toUpperCase(), cor: "#6B7280" };
}
function emailDoMorador(usuario) {
  return usuario.normalize("NFD").replace(/[^A-Za-z0-9]/g, "").toLowerCase() + DOMINIO_MORADOR;
}
// Login do admin: e-mail, ou nome de usuário sem @ (admin master) convertido no e-mail interno
function emailDoAdmin(login) {
  return login.includes("@") ? login : login.normalize("NFD").replace(/[^A-Za-z0-9]/g, "").toLowerCase() + DOMINIO_ADMIN;
}

// ---------------------------------------------------------------- arquivos (Storage)
async function enviarArquivo(modulo, arquivo) {
  return enviarNaPasta(modulo, crypto.randomUUID(), arquivo.name, arquivo);
}
async function enviarNaPasta(modulo, id, nomeArquivo, arquivo) {
  if (arquivo.size > LIMITE_ARQUIVO) throw new Error(`"${arquivo.name}" passa de 20 MB`);
  const nome = nomeArquivo.replace(/[\\/]/g, "_");
  const r = refArquivo(storage, `${modulo}/${id}/${nome}`);
  await uploadBytes(r, arquivo, {
    contentType: arquivo.type || "application/octet-stream",
    contentDisposition: `inline; filename="${nome.replace(/"/g, "")}"`,
  });
  return getDownloadURL(r);
}
async function enviarTodos(modulo, itens, progresso) {
  const links = [];
  let n = 0;
  for (const item of itens) {
    if (item instanceof File) {
      progresso?.(`Enviando ${++n}…`);
      links.push(await enviarArquivo(modulo, item));
    } else if (item) links.push(item);
  }
  return links;
}
async function baixar(link) {
  const nome = nomeDoAnexo(link);
  try {
    const resp = await fetch(link);
    if (!resp.ok) throw new Error(resp.status);
    const url = URL.createObjectURL(await resp.blob());
    const a = Object.assign(document.createElement("a"), { href: url, download: nome });
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
  } catch {
    window.open(link, "_blank", "noopener");
  }
}
function verImagem(src) {
  const v = document.createElement("div");
  v.className = "visualizador";
  v.innerHTML = `<img alt="" src="${esc(src)}">`;
  v.onclick = () => v.remove();
  document.body.appendChild(v);
}

// Lista de anexos: miniatura/ícone, nome, Abrir, Baixar (e Remover na edição)
function htmlAnexo(item, i, editando) {
  const nome = nomeDoAnexo(item);
  const tipo = tipoDoAnexo(nome);
  const local = item instanceof File;
  const src = local ? (item._url ||= URL.createObjectURL(item)) : item;
  const icone = tipo.img && (local || ehLink(item))
    ? `<img class="miniatura" data-ver="${i}" alt="" src="${esc(src)}">`
    : `<span class="tipo" data-abrir="${i}" style="background:${tipo.cor}">${tipo.rot}</span>`;
  const extra = local ? "<small>novo — será enviado ao salvar</small>" : (!ehLink(item) ? "<small>arquivo só no celular de origem</small>" : "");
  const acoes = local ? "" : `<button type="button" class="btn pequeno" data-abrir="${i}">Abrir</button>
      <button type="button" class="btn pequeno" data-baixar="${i}">Baixar</button>`;
  return `<div class="anexo">${icone}<span class="nome" title="${esc(nome)}">${esc(nome)}${extra}</span>
    <span class="mini-acoes">${acoes}${editando ? `<button type="button" class="btn pequeno perigo" data-remover="${i}">Remover</button>` : ""}</span></div>`;
}
function montarAnexos(container, itens, aoRemover) {
  container.innerHTML = itens.map((it, i) => htmlAnexo(it, i, !!aoRemover)).join("");
  container.onclick = (e) => {
    const b = e.target.closest("[data-abrir],[data-baixar],[data-remover],[data-ver]");
    if (!b) return;
    const i = +(b.dataset.abrir ?? b.dataset.baixar ?? b.dataset.remover ?? b.dataset.ver);
    const item = itens[i];
    if (b.dataset.remover !== undefined) aoRemover(i);
    else if (b.dataset.ver !== undefined) verImagem(item instanceof File ? item._url : item);
    else if (b.dataset.baixar !== undefined) baixar(item);
    else if (ehLink(item)) window.open(item, "_blank", "noopener");
  };
}

// ---------------------------------------------------------------- estado
let secaoAtual = SECOES[0];
let registros = [];
let adminAtual = null;
let subLista = null; // lista aberta dentro do quadro "Listas"

// ---------------------------------------------------------------- login
onAuthStateChanged(auth, async (user) => {
  if (!user) return mostrarLogin();
  try {
    const d = await getDoc(doc(db, "administradores", user.uid));
    if (!d.exists() || d.data().tipo !== "admin") {
      await signOut(auth);
      return mostrarLogin("Esta conta não é de administrador.");
    }
    adminAtual = { uid: user.uid, ...d.data() };
    $("olaAdmin").textContent = `Olá, ${adminAtual.primeiroNome || adminAtual.nome || user.email}`;
    $("telaLogin").hidden = true;
    $("telaPainel").hidden = false;
    escutarSeloChat();
    escutarSeloChatAdmin();
    abrirSecao(secaoAtual.id);
  } catch (e) {
    mostrarLogin("Erro ao verificar o acesso: " + e.message);
  }
});
function mostrarLogin(erro = "") {
  pararChat();
  ouvinteSelo?.();
  ouvinteSeloAdmin?.();
  ouvinteSelo = ouvinteSeloAdmin = null;
  $("telaPainel").hidden = true;
  $("telaLogin").hidden = false;
  $("erroLogin").textContent = erro;
  $("btnEntrar").disabled = false;
}
$("formLogin").onsubmit = async (e) => {
  e.preventDefault();
  $("btnEntrar").disabled = true;
  $("erroLogin").textContent = "";
  try {
    // E-mail em minúsculas e senha sem espaços nas pontas, igual ao app (o iPhone põe a 1ª letra
    // maiúscula e às vezes deixa um espaço no fim)
    await signInWithEmailAndPassword(auth, emailDoAdmin($("loginEmail").value.trim().toLowerCase()), $("loginSenha").value.trim());
  } catch (err) {
    $("btnEntrar").disabled = false;
    const codigo = err.code || "";
    $("erroLogin").textContent =
      /invalid-credential|invalid-login|wrong-password|user-not-found|invalid-email/.test(codigo) ? "E-mail ou senha incorretos."
      : codigo.includes("too-many-requests") ? "Muitas tentativas seguidas. Espere alguns minutos e tente de novo."
      : codigo.includes("network-request-failed") ? "Sem conexão com o servidor. Confira a internet e tente de novo."
      : `Não foi possível entrar (${codigo || err.message}).`;
  }
};
$("btnSair").onclick = () => { if (confirm("Deseja sair do painel?")) signOut(auth); };

// ---------------------------------------------------------------- menu
$("navSecoes").innerHTML = SECOES.filter((s) => !s.semMenu).map((s) =>
  `<button data-secao="${s.id}"><span class="ponto" style="background:${s.cor}"></span>${esc(s.titulo)}</button>`).join("");
$("navSecoes").onclick = (e) => {
  const b = e.target.closest("[data-secao]");
  if (b) { abrirSecao(b.dataset.secao); fecharMenu(); }
};
$("btnMenu").onclick = () => { $("menu").classList.add("aberto"); $("fundoMenu").classList.add("aberto"); };
$("fundoMenu").onclick = fecharMenu;
function fecharMenu() { $("menu").classList.remove("aberto"); $("fundoMenu").classList.remove("aberto"); }

$("btnChat").onclick = () => abrirSecao("chat");
$("btnChatAdmin").onclick = () => abrirSecao("chatadmin");
$("btnVoltar").onclick = () => abrirSecao(subLista ? secaoAtual.id : "cadastro");
$("busca").oninput = () => renderizar();
$("btnNovo").onclick = () => (secaoAtual.especial === "admins" ? formularioAdmin() : formulario(null));
$("btnExportar").onclick = () => exportar();

async function abrirSecao(id) {
  pararChat();
  subLista = null;
  secaoAtual = SECOES.find((s) => s.id === id) || SECOES[0];
  document.querySelectorAll("#navSecoes button").forEach((b) => b.classList.toggle("ativo", b.dataset.secao === (secaoAtual.noCadastro ? "cadastro" : secaoAtual.id)));
  $("tituloSecao").textContent = secaoAtual.titulo;
  $("btnNovo").hidden = !!secaoAtual.semNovo || !!secaoAtual.porMes;
  $("btnNovo").textContent = secaoAtual.especial === "admins" ? "+ Novo administrador" : "+ Novo";
  mostrarBusca(!["cadastro", "listas", "vermorador", "chat", "chatadmin", "backup"].includes(secaoAtual.especial));
  $("btnVoltar").hidden = !secaoAtual.noCadastro;
  $("btnVoltar").textContent = "← Cadastro";
  $("busca").value = "";
  $("listaSecao").innerHTML = `<p class="vazio">Carregando…</p>`;
  $("resumoSecao").textContent = "";
  try {
    registros = await carregar(secaoAtual);
    renderizar();
    observarEmTempoReal(secaoAtual);
  } catch (e) {
    $("listaSecao").innerHTML = `<p class="vazio">Erro ao carregar: ${esc(e.message)}</p>`;
  }
}

// ---------------------------------------------------------------- tempo real
// Enquanto a seção está aberta, qualquer mudança no banco recarrega a lista. O primeiro retorno de
// cada escuta é ignorado (a carga inicial já foi feita) e mudanças seguidas recarregam uma vez só.
let ouvintesSecao = [];
let esperaRecarga = null;

function fontesDaSecao(s) {
  if (s.especial === "boletos") return [collection(db, "moradores"), collection(db, "boletos"), collection(db, "boletos_meses")];
  if (s.especial === "admins") {
    return [collection(db, s.colecao), query(collection(db, "solicitacoes_admin"), where("status", "==", "pendente"))];
  }
  if (s.porMes) return [collection(db, s.colecao), doc(db, "meses_cadastro", s.colecao)];
  return s.colecao ? [collection(db, s.colecao)] : [];
}
function observarEmTempoReal(s) {
  for (const fonte of fontesDaSecao(s)) {
    let primeira = true;
    ouvintesSecao.push(onSnapshot(fonte, () => {
      if (primeira) { primeira = false; return; }
      clearTimeout(esperaRecarga);
      esperaRecarga = setTimeout(() => recarregarSecao(s), 200);
    }));
  }
}
async function recarregarSecao(s) {
  const lista = await carregar(s);
  if (s !== (subLista || secaoAtual)) return; // saiu da seção enquanto carregava
  registros = lista;
  renderizar();
}
function pararTempoReal() {
  ouvintesSecao.forEach((parar) => parar());
  ouvintesSecao = [];
  clearTimeout(esperaRecarga);
}

function mostrarBusca(sim) {
  $("busca").hidden = !sim;
  $("btnExportar").hidden = !sim;
}

async function carregar(s) {
  if (!s.colecao) return [];
  const snap = await getDocs(collection(db, s.colecao));
  let lista = snap.docs.map((d) => ({ ...d.data(), _id: d.id }));
  // A conta de visualização dos admins (morador com verTodos) não é um morador de verdade
  if (s.colecao === "moradores") {
    lista = lista.filter((r) => r.verTodos !== true);
    ordenarPorQuadraELote(lista);
    if (s.especial === "boletos") s._boletos = await carregarBoletos();
  } else if (s.especial === "leituras") {
    lista.sort((a, b) => (b.criadoEm?.toMillis?.() || 0) - (a.criadoEm?.toMillis?.() || 0));
    anotarAnteriores(lista);
  } else if (s.especial === "admins") {
    const pedidos = await getDocs(query(collection(db, "solicitacoes_admin"), where("status", "==", "pendente")));
    s._pedidos = pedidos.docs.map((d) => ({ ...d.data(), _id: d.id }));
    lista.sort((a, b) => (a.nome || "").localeCompare(b.nome || "", "pt-BR"));
  } else if (s.campoData) {
    lista.sort((a, b) => dataEmMs(b[s.campoData]) - dataEmMs(a[s.campoData]));
  } else if (s.campoNome) {
    lista.sort((a, b) => (a[s.campoNome] || "").localeCompare(b[s.campoNome] || "", "pt-BR"));
  }
  if (s.porMes) s._meses = (await getDoc(doc(db, "meses_cadastro", s.colecao))).data()?.meses || [];
  return lista;
}

function filtrados() {
  const termo = $("busca").value.trim().toLowerCase();
  if (!termo) return registros;
  return registros.filter((r) => JSON.stringify(r).toLowerCase().includes(termo));
}

// ---------------------------------------------------------------- listas
function renderizar() {
  const s = subLista || secaoAtual;
  const el = $("listaSecao");
  el.style.setProperty("--cor", s.cor);
  if (s.especial === "chat") return renderizarChat();
  if (s.especial === "chatadmin") return renderizarChatAdmin();
  if (s.especial === "backup") return renderizarBackup();
  if (s.especial === "listas") return renderizarListas();
  if (s.especial === "cadastro") return renderizarCadastro();
  if (s.especial === "vermorador") return renderizarVerComoMorador();
  if (s.previa) { $("resumoSecao").textContent = ""; el.onclick = null; el.innerHTML = s.previa(); return; }
  const lista = filtrados();
  if (s.especial === "admins") return renderizarAdmins(lista);
  $("resumoSecao").textContent = `${lista.length} de ${registros.length} ${registros.length === 1 ? "registro" : "registros"}`;
  if (s.porMes && (!subLista || secaoAtual.especial === "listas")) return renderizarPorMes(s, lista);
  if (!lista.length) { el.innerHTML = `<p class="vazio">Nada por aqui ainda.</p>`; return; }
  el.innerHTML = "";
  for (const r of lista) {
    el.appendChild(s.especial === "leituras" ? cartaoLeitura(r)
      : s.especial === "boletos" ? cartaoBoletos(r) : cartao(s, r, !!subLista));
  }
}

// ---------------------------------------------------------------- listas (só consulta, como no app)
function renderizarCadastro() {
  const el = $("listaSecao");
  $("resumoSecao").textContent = "";
  el.innerHTML = CADASTROS.map((id) => {
    const s = SECOES.find((x) => x.id === id);
    return `<button class="ladrilho" style="--cor:${s.cor}" data-secao="${s.id}">${esc(s.titulo)}</button>`;
  }).join("");
  el.onclick = (e) => {
    const b = e.target.closest("[data-secao]");
    if (b) abrirSecao(b.dataset.secao);
  };
}
// Mês a mês: cada mês abre e mostra os registros. Em Cadastro tem "Adicionar" e "Criar próximo mês"
function mesDoRegistro(s, r) {
  if (r.mes) return r.mes;
  const ms = dataEmMs(r[s.campoData]);
  return chaveDoMes(ms ? new Date(ms) : new Date());
}
function renderizarPorMes(s, lista) {
  const el = $("listaSecao");
  const soLer = !!subLista;
  const buscando = !!$("busca").value.trim();
  const meses = [...new Set([...(buscando ? [] : [chaveDoMes(new Date()), ...s._meses]), ...lista.map((r) => mesDoRegistro(s, r))])].sort();
  el.onclick = null;
  el.innerHTML = "";
  if (!meses.length) el.innerHTML = `<p class="vazio">Nada encontrado.</p>`;
  for (const mes of meses) {
    const chave = `${s.id}|${mes}`;
    const doMes = lista.filter((r) => mesDoRegistro(s, r) === mes);
    const grupo = document.createElement("details");
    grupo.className = "mes-boleto mes-cadastro";
    grupo.open = buscando || mesesAbertos.has(chave);
    grupo.innerHTML = `<summary>${esc(textoDoMes(mes))} (${doMes.length})</summary><div class="lista"></div>`;
    grupo.ontoggle = (e) => (e.target.open ? mesesAbertos.add(chave) : mesesAbertos.delete(chave));
    const cartoes = grupo.querySelector(".lista");
    doMes.forEach((r) => cartoes.appendChild(cartao(s, r, soLer)));
    if (!doMes.length) cartoes.innerHTML = `<p class="sub">Nenhum registro neste mês.</p>`;
    if (!soLer) {
      const novo = document.createElement("button");
      novo.className = "btn primario pequeno";
      novo.textContent = "Adicionar";
      novo.onclick = () => formulario(null, mes);
      grupo.appendChild(novo);
    }
    el.appendChild(grupo);
  }
  if (soLer || buscando) return;
  const proximo = proximoMes(meses[meses.length - 1]);
  const criar = document.createElement("button");
  criar.className = "btn contorno pequeno criar-mes";
  criar.textContent = `Criar ${textoDoMes(proximo)}`;
  criar.onclick = async () => {
    criar.disabled = true;
    try {
      await setDoc(doc(db, "meses_cadastro", s.colecao), { meses: arrayUnion(proximo) }, { merge: true });
      mesesAbertos.add(`${s.id}|${proximo}`);
      aviso("Mês criado.");
      await recarregarSecao(s);
    } catch (e) { criar.disabled = false; aviso("Erro ao criar o mês: " + e.message); }
  };
  el.appendChild(criar);
}

// ---------------------------------------------------------------- chat admin (entre administradores)
// Mesmo formato do app (ChatAdminRepository.java): conversasAdmin/{uidMenor_uidMaior}
let ouvinteSeloAdmin = null;
let ouvinteConversasAdmin = null;
let ouvinteMensagensAdmin = null;
let adminsDoChat = {};
let conversasAdminAtuais = [];
let chatAdminId = null;
let chatAdminOutro = null;

const idConversaAdmin = (a, b) => (a < b ? `${a}_${b}` : `${b}_${a}`);
const campoNaoLidasAdmin = (id, uid) => (id.startsWith(uid + "_") ? "naoLidas1" : "naoLidas2");
const nomeDoAdmin = (uid) => adminsDoChat[uid]?.nome || "Administrador";
const minhasConversasAdmin = () => query(collection(db, "conversasAdmin"), where("participantes", "array-contains", adminAtual.uid));

function escutarSeloChatAdmin() {
  if (ouvinteSeloAdmin) return;
  ouvinteSeloAdmin = onSnapshot(minhasConversasAdmin(), (snap) => {
    const total = snap.docs.reduce((n, d) => n + (d.data()[campoNaoLidasAdmin(d.id, adminAtual.uid)] || 0), 0);
    $("seloChatAdmin").hidden = total === 0;
    $("seloChatAdmin").textContent = total > 99 ? "99+" : total;
  });
}

function renderizarChatAdmin() {
  const el = $("listaSecao");
  $("resumoSecao").textContent = "";
  el.innerHTML = `<div class="chat">
      <div class="chat-conversas">
        <select id="chatAdminNova"><option value="">Nova conversa…</option></select>
        <div id="chatLista"><p class="vazio">Carregando…</p></div>
      </div>
      <div class="chat-janela">
        <header class="chat-topo" id="chatTopo"><span>Escolha uma conversa</span></header>
        <div class="chat-mensagens" id="chatMensagens"></div>
        <form class="chat-envio" id="chatEnvio" hidden>
          <input id="chatTexto" placeholder="Digite uma mensagem…" autocomplete="off">
          <button class="btn primario" type="submit">Enviar</button>
        </form>
      </div>
    </div>`;
  $("chatAdminNova").onchange = (e) => { if (e.target.value) abrirConversaAdmin(e.target.value); e.target.value = ""; };
  $("chatEnvio").onsubmit = (e) => {
    e.preventDefault();
    const texto = $("chatTexto").value.trim();
    if (!texto) return;
    $("chatTexto").value = "";
    enviarMensagemAdmin(texto).catch((err) => aviso("Não foi possível enviar: " + err.message));
  };
  ouvintesSecao.push(onSnapshot(collection(db, "administradores"), (snap) => {
    adminsDoChat = Object.fromEntries(snap.docs.map((d) => [d.id, d.data()]));
    const outros = Object.keys(adminsDoChat).filter((uid) => uid !== adminAtual.uid)
      .sort((a, b) => nomeDoAdmin(a).localeCompare(nomeDoAdmin(b), "pt-BR"));
    $("chatAdminNova").innerHTML = `<option value="">Nova conversa…</option>`
      + outros.map((uid) => `<option value="${esc(uid)}">${esc(nomeDoAdmin(uid))}</option>`).join("");
    mostrarConversasAdmin(conversasAdminAtuais);
  }));
  ouvinteConversasAdmin = onSnapshot(minhasConversasAdmin(), (s) => mostrarConversasAdmin(s.docs));
}

async function mostrarConversasAdmin(docs) {
  conversasAdminAtuais = docs;
  const quando = (d) => d.data({ serverTimestamps: "estimate" }).ultimoTimestamp?.toMillis?.() || 0;
  const ordenadas = [...docs].sort((a, b) => quando(b) - quando(a));
  const linhas = await Promise.all(ordenadas.map(async (d) => {
    const c = d.data({ serverTimestamps: "estimate" });
    const outro = c.participantes.find((uid) => uid !== adminAtual.uid);
    const naoLidas = c[campoNaoLidasAdmin(d.id, adminAtual.uid)] || 0;
    const ultima = await decifrar(c.ultimaMensagem);
    return `<button type="button" class="conversa${d.id === chatAdminId ? " ativa" : ""}" data-uid="${esc(outro)}" data-id="${esc(d.id)}">
      <span class="sem-foto"></span>
      <span class="conversa-texto"><strong>${esc(nomeDoAdmin(outro))}</strong>
        <small>${esc((c.ultimoRemetenteId === adminAtual.uid ? "Você: " : "") + (ultima || ""))}</small></span>
      <span class="conversa-fim"><small>${esc(horaChat(c.ultimoTimestamp))}</small>
        ${naoLidas > 0 ? `<span class="selo">${naoLidas}</span>` : ""}</span></button>`;
  }));
  const el = $("chatLista");
  if (!el) return;
  el.innerHTML = linhas.join("") || `<p class="vazio">Nenhuma conversa ainda.</p>`;
  el.onclick = (e) => {
    const b = e.target.closest("[data-uid]");
    if (b) abrirConversaAdmin(b.dataset.uid);
  };
}

async function abrirConversaAdmin(outro) {
  ouvinteMensagensAdmin?.();
  const id = idConversaAdmin(adminAtual.uid, outro);
  chatAdminId = id;
  chatAdminOutro = outro;
  try {
    const conv = doc(db, "conversasAdmin", id);
    if (!(await getDoc(conv)).exists()) {
      await setDoc(conv, {
        participantes: [adminAtual.uid, outro], ultimaMensagem: "", ultimoTimestamp: serverTimestamp(),
        ultimoRemetenteId: "", naoLidas1: 0, naoLidas2: 0,
      });
    }
  } catch (e) { aviso("Erro ao abrir a conversa: " + e.message); return; }
  document.querySelectorAll(".conversa").forEach((b) => b.classList.toggle("ativa", b.dataset.id === id));
  $("chatTopo").innerHTML = `<span class="chat-nome"><strong>${esc(nomeDoAdmin(outro))}</strong><small>Administrador</small></span>
    <button type="button" class="btn pequeno perigo" id="chatApagar">Apagar conversa</button>`;
  $("chatApagar").onclick = () => apagarConversaAdmin(id, nomeDoAdmin(outro));
  $("chatEnvio").hidden = false;
  $("chatMensagens").innerHTML = "";
  ouvinteMensagensAdmin = onSnapshot(query(collection(db, "conversasAdmin", id, "mensagens"), orderBy("timestamp", "asc")),
    (s) => mostrarMensagensAdmin(id, s.docs));
}

async function mostrarMensagensAdmin(id, docs) {
  const eu = adminAtual.uid;
  const oculta = (m) => (m.remetenteId === eu ? m.deletadoParaRemetente : m.deletadoParaDestinatario) === true;
  const itens = await Promise.all(docs.filter((d) => !oculta(d.data())).map(async (d) => ({
    d, m: d.data({ serverTimestamps: "estimate" }), texto: await decifrar(d.data().conteudo),
  })));
  if (id !== chatAdminId) return;
  const el = $("chatMensagens");
  el.innerHTML = "";
  if (!itens.length) el.innerHTML = `<p class="vazio">Nenhuma mensagem ainda.</p>`;
  for (const { d, m, texto } of itens) {
    const minha = m.remetenteId === eu;
    const apagada = m.deletadoParaTodos === true;
    const b = document.createElement("div");
    b.className = "linha-mensagem" + (minha ? " minha" : "");
    b.innerHTML = `<div class="balao"><div class="${apagada ? "apagada" : ""}">${esc(texto)}</div>
        <small class="${minha && m.lida ? "lida" : ""}">${esc(horaChat(m.timestamp))}${minha ? (m.lida ? "  ✓✓" : "  ✓") : ""}</small>
        <span class="apagar">
          <button type="button" data-mim>Apagar só pra mim</button>
          ${minha && !apagada ? `<button type="button" data-todos>Apagar pra todos</button>` : ""}
        </span></div>`;
    const erro = (e) => aviso("Não foi possível apagar: " + e.message);
    b.querySelector("[data-mim]").onclick = () => updateDoc(d.ref, { [minha ? "deletadoParaRemetente" : "deletadoParaDestinatario"]: true }).catch(erro);
    b.querySelector("[data-todos]")?.addEventListener("click", async () => {
      updateDoc(d.ref, { deletadoParaTodos: true, conteudo: await cifrar(TEXTO_APAGADA) }).catch(erro);
    });
    el.appendChild(b);
  }
  el.scrollTop = el.scrollHeight;
  marcarConversaAdminLida(id, docs).catch(() => {});
}

// Conversa aberta: o que chegou já conta como lido
async function marcarConversaAdminLida(id, docs) {
  const eu = adminAtual.uid;
  const conv = doc(db, "conversasAdmin", id);
  const campo = campoNaoLidasAdmin(id, eu);
  if ((await getDoc(conv)).data()?.[campo] > 0) await updateDoc(conv, { [campo]: 0 });
  const novas = docs.filter((d) => d.data().destinatarioId === eu && d.data().lida !== true);
  if (!novas.length) return;
  const lote = writeBatch(db);
  novas.forEach((d) => lote.update(d.ref, { lida: true }));
  await lote.commit();
}

async function enviarMensagemAdmin(texto) {
  const conv = doc(db, "conversasAdmin", chatAdminId);
  const cifrado = await cifrar(texto);
  const lote = writeBatch(db);
  lote.set(doc(collection(conv, "mensagens")), {
    remetenteId: adminAtual.uid, destinatarioId: chatAdminOutro, conteudo: cifrado, tipo: "texto",
    timestamp: serverTimestamp(), lida: false, deletadoParaTodos: false, deletadoParaRemetente: false, deletadoParaDestinatario: false,
  });
  lote.update(conv, {
    ultimaMensagem: cifrado, ultimoTimestamp: serverTimestamp(), ultimoRemetenteId: adminAtual.uid,
    [campoNaoLidasAdmin(chatAdminId, chatAdminOutro)]: increment(1),
  });
  await lote.commit();
}

async function apagarConversaAdmin(id, nome) {
  if (!confirm(`Apagar toda a conversa com ${nome}? As mensagens somem para os dois.`)) return;
  try {
    ouvinteMensagensAdmin?.();
    ouvinteMensagensAdmin = chatAdminId = null;
    const conv = doc(db, "conversasAdmin", id);
    const snap = await getDocs(collection(conv, "mensagens"));
    // Limite do Firestore: 500 operações por lote
    for (let i = 0; i < snap.docs.length; i += 450) {
      const lote = writeBatch(db);
      snap.docs.slice(i, i + 450).forEach((d) => lote.delete(d.ref));
      await lote.commit();
    }
    await deleteDoc(conv);
    aviso("Conversa apagada.");
    $("chatTopo").innerHTML = "<span>Escolha uma conversa</span>";
    $("chatMensagens").innerHTML = "";
    $("chatEnvio").hidden = true;
  } catch (e) { aviso("Erro ao apagar: " + e.message); }
}

// ---------------------------------------------------------------- backup
function renderizarBackup() {
  const el = $("listaSecao");
  $("resumoSecao").textContent = "";
  el.innerHTML = `<article class="cartao backup-info">
      <h3>Seus dados sempre seguros</h3>
      <div class="texto-longo">Todo dia, à meia-noite, o banco de dados é salvo aqui automaticamente, e os últimos 15 backups ficam guardados (os mais antigos são apagados sozinhos).

Para levar os dados para outro aplicativo ou empresa, use o Pacote completo: ele traz o banco, planilhas para Excel, todos os arquivos enviados e um LEIA-ME explicando como implantar. Baixe e guarde em um local seu.</div>
      <div class="botoes">
        <button class="btn primario" data-gerar="gerarBackupAgora">Fazer backup agora</button>
        <button class="btn sucesso" data-gerar="gerarPacoteCompleto">Gerar Pacote completo</button>
      </div>
    </article>`;
  el.querySelectorAll("[data-gerar]").forEach((b) => {
    b.onclick = async () => {
      const texto = b.textContent;
      el.querySelectorAll("[data-gerar]").forEach((x) => { x.disabled = true; });
      b.textContent = "Gerando… pode levar alguns minutos";
      try {
        await httpsCallable(funcoes, b.dataset.gerar, { timeout: 540000 })();
        aviso("Pronto! O backup já está na lista.");
      } catch (e) { aviso("Erro ao gerar: " + e.message); }
      el.querySelectorAll("[data-gerar]").forEach((x) => { x.disabled = false; });
      b.textContent = texto;
    };
  });
  const lista = [...registros].sort((a, b) => (b.criadoEm?.toMillis?.() || 0) - (a.criadoEm?.toMillis?.() || 0));
  if (!lista.length) el.insertAdjacentHTML("beforeend", `<p class="vazio">Nenhum backup ainda. O primeiro sai à meia-noite.</p>`);
  for (const r of lista) {
    const c = document.createElement("article");
    c.className = "cartao";
    c.style.setProperty("--cor", r.tipo === "pacote" ? "#16A34A" : secaoAtual.cor);
    c.innerHTML = `<h3>${r.tipo === "pacote" ? "Pacote completo" : "Backup do banco"} — ${esc(r.titulo)}</h3>
      <div class="sub">${((r.tamanho || 0) / 1048576).toFixed(1)} MB · ${r.registros || 0} registros</div>
      <div class="botoes"><button class="btn pequeno primario" data-baixar>Baixar</button></div>`;
    c.querySelector("[data-baixar]").onclick = async () => {
      try { window.open(await getDownloadURL(refArquivo(storage, r.caminho)), "_blank", "noopener"); }
      catch (e) { aviso("Erro ao baixar: " + e.message); }
    };
    el.appendChild(c);
  }
}

function renderizarVerComoMorador() {
  const el = $("listaSecao");
  $("resumoSecao").textContent = "";
  el.innerHTML = QUADROS_MORADOR.map((q, i) =>
    `<button class="ladrilho" style="--cor:${q.cor}" data-quadro="${i}">${esc(q.titulo)}</button>`).join("");
  el.onclick = (e) => {
    const b = e.target.closest("[data-quadro]");
    if (!b) return;
    const q = QUADROS_MORADOR[+b.dataset.quadro];
    if (q.id) return abrirLista(q.id, q.titulo);
    subLista = q;
    $("tituloSecao").textContent = q.titulo;
    mostrarVoltarParaSecao();
    renderizar();
  };
}
function mostrarVoltarParaSecao() {
  $("btnVoltar").hidden = false;
  $("btnVoltar").textContent = "← " + secaoAtual.titulo;
}
// O admin não tem cadastro de morador: estas duas telas aparecem como o morador vê, sem enviar nada
function previaLeitura() {
  return `<article class="cartao">
      <div class="sub">Digite o número que aparece no hidrômetro e tire uma foto dele.</div>
      <label>Leitura:<input disabled></label>
      <button class="btn contorno" disabled>Tirar foto do hidrômetro</button>
      <button class="btn primario" disabled>Enviar</button>
      <div class="sub">Visualização: nada é enviado por aqui.</div>
    </article>`;
}
function previaPagamentos() {
  const hoje = new Date();
  const venc = dataBr(new Date(hoje.getFullYear(), hoje.getMonth(), 15));
  return `<article class="cartao">
      <div class="texto-longo">O boleto vence sempre no dia 15 do mês vigente.\nVencimento deste mês: ${venc}</div>
      <h4>Boletos</h4>
      <span class="sub">Nenhum boleto postado.</span>
      <div class="sub">Visualização: cada morador vê aqui os próprios boletos.</div>
    </article>`;
}
function renderizarListas() {
  const el = $("listaSecao");
  $("resumoSecao").textContent = "";
  el.innerHTML = LISTAS.map(([id, titulo]) => `<button class="ladrilho" data-lista="${id}">${esc(titulo)}</button>`).join("");
  el.onclick = (e) => {
    const b = e.target.closest("[data-lista]");
    if (b) abrirLista(b.dataset.lista, b.textContent);
  };
}
async function abrirLista(id, titulo) {
  $("listaSecao").onclick = null;
  subLista = SECOES.find((s) => s.id === id);
  $("tituloSecao").textContent = titulo;
  mostrarBusca(secaoAtual.especial === "listas");
  mostrarVoltarParaSecao();
  $("busca").value = "";
  $("listaSecao").innerHTML = `<p class="vazio">Carregando…</p>`;
  try {
    registros = await carregar(subLista);
    renderizar();
    observarEmTempoReal(subLista);
  } catch (e) {
    $("listaSecao").innerHTML = `<p class="vazio">Erro ao carregar: ${esc(e.message)}</p>`;
  }
}

// ---------------------------------------------------------------- boletos (PDF, vencimento dia 15)
const expandidos = new Set();
const mesesAbertos = new Set(); // continuam abertos quando a lista recarrega
// Meses criados em "Criar próximo mês", por morador (boletos_meses no Firestore)
let mesesCriados = {};

function loteTexto(lote) {
  const l = String(lote ?? "").trim();
  return /^\d{1,9}$/.test(l) ? l.padStart(2, "0") : l || "-";
}
function quadraTexto(quadra) { return String(quadra ?? "").trim().toUpperCase() || "-"; }
function numeroDoLote(r) {
  const d = String(r.lote ?? "").replace(/\D/g, "");
  return d && d.length <= 9 ? +d : Number.MAX_SAFE_INTEGER;
}
// Quadra em ordem alfabética e, dentro dela, lote em ordem numérica (sem quadra vai pro fim)
function ordenarPorQuadraELote(lista) {
  lista.sort((a, b) => {
    const qa = String(a.quadra ?? "").trim().toUpperCase(), qb = String(b.quadra ?? "").trim().toUpperCase();
    if (!qa !== !qb) return qa ? -1 : 1;
    return qa.localeCompare(qb) || numeroDoLote(a) - numeroDoLote(b)
      || String(a.lote ?? "").localeCompare(String(b.lote ?? "")) || (a.nome || "").localeCompare(b.nome || "", "pt-BR");
  });
}

async function carregarBoletos() {
  const [snap, meses] = await Promise.all([getDocs(collection(db, "boletos")), getDocs(collection(db, "boletos_meses"))]);
  mesesCriados = Object.fromEntries(meses.docs.map((d) => [d.id, d.data().meses || []]));
  const porMorador = {};
  for (const d of snap.docs) (porMorador[d.data().moradorUid] ||= []).push({ ...d.data(), _id: d.id });
  const quando = (b) => b.postadoEm?.toMillis?.() || 0;
  for (const lista of Object.values(porMorador)) lista.sort((a, b) => quando(b) - quando(a));
  return porMorador;
}
async function recarregarBoletos() {
  secaoAtual._boletos = await carregarBoletos();
  renderizar();
}
const postadoDe = (b) => b.postadoEm?.toDate?.() || new Date();
// Mês do boleto ("2026-11"); boletos antigos, sem esse campo, usam o mês da postagem
const chaveDoMes = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
const mesDoBoleto = (b) => b.mes || chaveDoMes(postadoDe(b));
function dataDoMes(mes, dia = 1) {
  const [ano, m] = mes.split("-").map(Number);
  return new Date(ano, m - 1, dia, 23, 59, 59);
}
const vencimentoDo = (b) => dataDoMes(mesDoBoleto(b), 15);
const proximoMes = (mes) => { const d = dataDoMes(mes); return chaveDoMes(new Date(d.getFullYear(), d.getMonth() + 1, 1)); };
// "2026-10" vira "Outubro de 2026"
function textoDoMes(mes) {
  const texto = dataDoMes(mes).toLocaleDateString("pt-BR", { month: "long", year: "numeric" });
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}
// Meses da lista de um morador, em ordem: o mês atual, os criados e os que têm boleto
function mesesDoMorador(uid, lista) {
  return [...new Set([chaveDoMes(new Date()), ...(mesesCriados[uid] || []), ...lista.map(mesDoBoleto)])].sort();
}
const dataBr = (d) => d.toLocaleDateString("pt-BR");
const horaBr = (d) => d.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });

function linhaBoleto(b) {
  const venc = dataBr(vencimentoDo(b));
  const liberada = new Date() > vencimentoDo(b) || b.naoPago;
  const postado = postadoDe(b);
  const c = document.createElement("div");
  c.className = "boleto" + (b.naoPago ? " nao-pago" : "");
  c.innerHTML = `<div class="anexo">
      <span class="tipo" style="background:#DC2626">PDF</span>
      <span class="nome" title="${esc(b.nomeArquivo || "boleto.pdf")}">${esc(b.nomeArquivo || "boleto.pdf")}
        <small>Postado em ${dataBr(postado)} às ${horaBr(postado)}</small></span>
      <span class="mini-acoes">
        <button type="button" class="btn pequeno" data-abrir>Abrir</button>
        <button type="button" class="btn pequeno" data-baixar>Baixar</button>
        <button type="button" class="btn pequeno perigo" data-excluir>Excluir</button>
      </span></div>
    <div class="status ${b.naoPago ? "vencido" : "em-dia"}">${b.naoPago
      ? `Boleto vencido (venceu em ${venc})` : `Vence em ${venc} (sempre dia 15 do mês do boleto)`}</div>
    <label class="marcar"><input type="checkbox" ${b.naoPago ? "checked" : ""} ${liberada ? "" : "disabled"}>
      ${liberada ? "Não pago após o dia 15" : `Não pago (liberado após ${venc})`}</label>`;
  c.querySelector("[data-abrir]").onclick = () => window.open(b.arquivo, "_blank", "noopener");
  c.querySelector("[data-baixar]").onclick = () => baixar(b.arquivo);
  c.querySelector("[data-excluir]").onclick = async () => {
    if (!confirm(`Excluir o boleto postado em ${dataBr(postado)} às ${horaBr(postado)}?`)) return;
    try { await deleteDoc(doc(db, "boletos", b._id)); aviso("Boleto excluído."); await recarregarBoletos(); }
    catch (e) { aviso("Erro no boleto: " + e.message); }
  };
  c.querySelector("input").onchange = async (e) => {
    try {
      await updateDoc(doc(db, "boletos", b._id), { naoPago: e.target.checked });
      aviso(e.target.checked ? "Boleto marcado como não pago." : "Marcação de não pago removida.");
    } catch (err) { aviso("Erro no boleto: " + err.message); }
    await recarregarBoletos();
  };
  return c;
}

function cartaoBoletos(r) {
  const c = document.createElement("article");
  c.className = "cartao";
  c.style.setProperty("--cor", secaoAtual.cor);
  const aberto = expandidos.has(r._id);
  c.innerHTML = `<button type="button" class="resumo-morador" data-alternar>
      <span><h3>Quadra ${esc(quadraTexto(r.quadra))} · Lote ${esc(loteTexto(r.lote))}</h3>
      <span class="sub">${esc(r.nome || "Nome não informado")}</span></span><span>${aberto ? "▲" : "▼"}</span></button>
    <div class="detalhes" ${aberto ? "" : "hidden"}>
      <h4>Boletos</h4><div class="grade-anexos" data-boletos></div>
      <button type="button" class="btn contorno pequeno" data-criar></button>
    </div>`;
  c.querySelector("[data-alternar]").onclick = () => {
    if (!expandidos.delete(r._id)) expandidos.add(r._id);
    renderizar();
  };
  const lista = secaoAtual._boletos?.[r._id] || [];
  const caixa = c.querySelector("[data-boletos]");
  // Cada mês fechado; aberto, mostra os boletos dele e "Adicionar boleto"
  const meses = mesesDoMorador(r._id, lista);
  for (const mes of meses) {
    const chave = `${r._id}|${mes}`;
    const grupo = document.createElement("details");
    grupo.className = "mes-boleto";
    grupo.open = mesesAbertos.has(chave);
    grupo.innerHTML = `<summary>${esc(textoDoMes(mes))}</summary>`;
    grupo.ontoggle = (e) => (e.target.open ? mesesAbertos.add(chave) : mesesAbertos.delete(chave));
    const doMes = lista.filter((b) => mesDoBoleto(b) === mes);
    doMes.forEach((b) => grupo.appendChild(linhaBoleto(b)));
    if (!doMes.length) grupo.insertAdjacentHTML("beforeend", `<span class="sub">Nenhum boleto postado.</span>`);
    grupo.insertAdjacentHTML("beforeend", `<label class="btn primario pequeno">Adicionar boleto (PDF)
      <input type="file" accept="application/pdf,.pdf" hidden data-pdf></label>`);
    grupo.querySelector("[data-pdf]").onchange = (e) => postarBoleto(r._id, mes, e.target.files[0]);
    caixa.appendChild(grupo);
  }
  const proximo = proximoMes(meses[meses.length - 1]);
  const criar = c.querySelector("[data-criar]");
  criar.textContent = `Criar ${textoDoMes(proximo)}`;
  criar.onclick = async () => {
    criar.disabled = true;
    try {
      await setDoc(doc(db, "boletos_meses", r._id), { meses: arrayUnion(proximo) }, { merge: true });
      mesesAbertos.add(`${r._id}|${proximo}`);
      aviso("Mês criado.");
      await recarregarBoletos();
    } catch (e) { criar.disabled = false; aviso("Erro ao criar o mês: " + e.message); }
  };
  return c;
}

async function postarBoleto(moradorUid, mes, arquivo) {
  if (!arquivo) return;
  if (arquivo.type !== "application/pdf" && !/\.pdf$/i.test(arquivo.name)) { aviso("Escolha um arquivo PDF."); return; }
  const nome = /\.pdf$/i.test(arquivo.name) ? arquivo.name : arquivo.name + ".pdf";
  aviso("Enviando boleto…");
  try {
    const pdf = new File([arquivo], nome, { type: "application/pdf" });
    const link = await enviarNaPasta("boletos", moradorUid, `${Date.now()}_${nome}`, pdf);
    await setDoc(doc(collection(db, "boletos")), {
      moradorUid, arquivo: link, nomeArquivo: nome, postadoEm: serverTimestamp(), naoPago: false, mes,
    });
    aviso("Boleto postado!");
    await recarregarBoletos();
  } catch (e) { aviso("Erro no boleto: " + e.message); }
}

// ---------------------------------------------------------------- chat (mesmo formato do app)
// Texto cifrado com AES-256-GCM: "ENC1:" + base64(iv de 12 bytes + cifrado + tag), igual ao ChatCrypto.java
const PREFIXO_CHAT = "ENC1:";
const TEXTO_APAGADA = "🚫 Mensagem apagada";
const chaveChat = crypto.subtle.importKey("raw",
  Uint8Array.from(atob("NIYJ8BQYYrEbXaBbFWKG/AcgGY2IhZfmY3wQHCi2A8s="), (c) => c.charCodeAt(0)),
  "AES-GCM", false, ["encrypt", "decrypt"]);

async function cifrar(texto) {
  if (!texto) return texto;
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const cifrado = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, await chaveChat, new TextEncoder().encode(texto)));
  const tudo = new Uint8Array(12 + cifrado.length);
  tudo.set(iv);
  tudo.set(cifrado, 12);
  return PREFIXO_CHAT + btoa(String.fromCharCode(...tudo));
}
async function decifrar(texto) {
  if (!texto || !texto.startsWith(PREFIXO_CHAT)) return texto;
  try {
    const tudo = Uint8Array.from(atob(texto.slice(PREFIXO_CHAT.length)), (c) => c.charCodeAt(0));
    const claro = await crypto.subtle.decrypt({ name: "AES-GCM", iv: tudo.slice(0, 12) }, await chaveChat, tudo.slice(12));
    return new TextDecoder().decode(claro);
  } catch { return "(mensagem não pôde ser lida)"; }
}

let ouvinteSelo = null;
let ouvinteConversas = null;
let ouvinteMensagens = null;
let chatMoradores = {};
let chatConversas = [];
let chatUid = null;

function escutarSeloChat() {
  if (ouvinteSelo) return;
  ouvinteSelo = onSnapshot(query(collection(db, "conversas"), where("naoLidasAdmin", ">", 0)), (snap) => {
    const total = snap.docs.reduce((n, d) => n + (d.data().naoLidasAdmin || 0), 0);
    $("seloChat").hidden = total === 0;
    $("seloChat").textContent = total > 99 ? "99+" : total;
  });
}
function pararChat() {
  pararTempoReal();
  ouvinteConversas?.();
  ouvinteMensagens?.();
  ouvinteConversasAdmin?.();
  ouvinteMensagensAdmin?.();
  ouvinteConversas = ouvinteMensagens = chatUid = null;
  ouvinteConversasAdmin = ouvinteMensagensAdmin = chatAdminId = null;
  $("listaSecao").onclick = null;
}
// "Q:A L:01" e o primeiro nome de quem usa o app: o inquilino, se houver, senão o morador
function rotuloMorador(m) {
  const temInquilino = m?.temInquilino === true && String(m.inquilinoNome ?? "").trim();
  const nome = String((temInquilino ? m.inquilinoNome : m?.nome) ?? "").trim();
  const q = String(m?.quadra ?? "").trim().toUpperCase() || "-";
  return [`Q:${q} L:${loteTexto(m?.lote)}`, nome ? nome.split(/\s+/)[0] : "Morador"];
}
const horaChat = (ts) => {
  const d = ts?.toDate?.();
  return d ? `${d.toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit" })} ${horaBr(d)}` : "";
};

async function renderizarChat() {
  const el = $("listaSecao");
  $("resumoSecao").textContent = "";
  el.innerHTML = `<div class="chat">
      <div class="chat-conversas">
        <select id="chatNova"><option value="">Nova conversa…</option></select>
        <div id="chatLista"><p class="vazio">Carregando…</p></div>
      </div>
      <div class="chat-janela">
        <header class="chat-topo" id="chatTopo"><span>Escolha uma conversa</span></header>
        <div class="chat-mensagens" id="chatMensagens"></div>
        <form class="chat-envio" id="chatEnvio" hidden>
          <label class="btn contorno pequeno">Foto<input type="file" accept="image/*" id="chatFoto" hidden></label>
          <input id="chatTexto" placeholder="Digite uma mensagem…" autocomplete="off">
          <button class="btn primario" type="submit">Enviar</button>
        </form>
      </div>
    </div>`;
  $("chatNova").onchange = (e) => { if (e.target.value) abrirConversa(e.target.value); e.target.value = ""; };
  $("chatEnvio").onsubmit = (e) => {
    e.preventDefault();
    const texto = $("chatTexto").value.trim();
    if (!texto) return;
    $("chatTexto").value = "";
    gravarMensagem(chatUid, "texto", texto, null, texto).catch((err) => aviso("Não foi possível enviar: " + err.message));
  };
  $("chatFoto").onchange = (e) => { enviarFotoChat(e.target.files[0]); e.target.value = ""; };

  ouvintesSecao.push(onSnapshot(collection(db, "moradores"), (snap) => {
    chatMoradores = {};
    for (const d of snap.docs) if (d.data().verTodos !== true) chatMoradores[d.id] = d.data();
    const lista = Object.entries(chatMoradores).map(([id, m]) => ({ ...m, _id: id }));
    ordenarPorQuadraELote(lista);
    $("chatNova").innerHTML = `<option value="">Nova conversa…</option>` + lista.map((m) => {
      const [ql, nome] = rotuloMorador(m);
      return `<option value="${esc(m._id)}">${esc(ql)} — ${esc(nome)}</option>`;
    }).join("");
    mostrarConversas(chatConversas);
  }));
  ouvinteConversas = onSnapshot(query(collection(db, "conversas"), orderBy("ultimoTimestamp", "desc")),
    (s) => mostrarConversas(s.docs));
}

async function mostrarConversas(docs) {
  chatConversas = docs;
  const linhas = await Promise.all(docs.map(async (d) => {
    const c = d.data({ serverTimestamps: "estimate" });
    const m = chatMoradores[d.id];
    const [ql, nome] = rotuloMorador(m);
    const ultima = await decifrar(c.ultimaMensagem);
    return `<button type="button" class="conversa${d.id === chatUid ? " ativa" : ""}" data-uid="${esc(d.id)}">
      ${ehLink(m?.foto) ? `<img alt="" src="${esc(m.foto)}">` : `<span class="sem-foto"></span>`}
      <span class="conversa-texto"><strong>${esc(ql)}</strong> ${esc(m ? nome : "Morador excluído")}
        <small>${esc((c.ultimoDeAdmin ? "Você: " : "") + (ultima || ""))}</small></span>
      <span class="conversa-fim"><small>${esc(horaChat(c.ultimoTimestamp))}</small>
        ${c.naoLidasAdmin > 0 ? `<span class="selo">${c.naoLidasAdmin}</span>` : ""}</span></button>`;
  }));
  const el = $("chatLista");
  if (!el) return;
  el.innerHTML = linhas.join("") || `<p class="vazio">Nenhuma conversa ainda.</p>`;
  el.onclick = (e) => {
    const b = e.target.closest("[data-uid]");
    if (b) abrirConversa(b.dataset.uid);
  };
}

function abrirConversa(uid) {
  ouvinteMensagens?.();
  chatUid = uid;
  document.querySelectorAll(".conversa").forEach((b) => b.classList.toggle("ativa", b.dataset.uid === uid));
  const m = chatMoradores[uid];
  const [ql, nome] = rotuloMorador(m);
  $("chatTopo").innerHTML = `${ehLink(m?.foto) ? `<img alt="" src="${esc(m.foto)}">` : ""}
    <span class="chat-nome"><strong>${esc(ql)}</strong><small>${esc(m ? nome : "Morador excluído")}</small></span>
    <button type="button" class="btn pequeno perigo" id="chatApagar">Apagar conversa</button>`;
  $("chatApagar").onclick = () => apagarConversa(uid, `${ql} ${nome}`);
  $("chatEnvio").hidden = false;
  $("chatMensagens").innerHTML = "";
  ouvinteMensagens = onSnapshot(query(collection(db, "conversas", uid, "mensagens"), orderBy("timestamp", "asc")),
    (s) => mostrarMensagens(uid, s.docs));
}

async function mostrarMensagens(uid, docs) {
  const visiveis = docs.filter((d) => d.data().deletadoParaAdmin !== true);
  const itens = await Promise.all(visiveis.map(async (d) => ({
    d, m: d.data({ serverTimestamps: "estimate" }), texto: await decifrar(d.data().conteudo),
  })));
  if (uid !== chatUid) return;
  const el = $("chatMensagens");
  el.innerHTML = "";
  if (!itens.length) el.innerHTML = `<p class="vazio">Nenhuma mensagem ainda.</p>`;
  for (const { d, m, texto } of itens) {
    const minha = m.deAdmin === true;
    const apagada = m.deletadoParaTodos === true;
    const imagem = m.tipo === "imagem" && m.imagemUrl && !apagada;
    const b = document.createElement("div");
    b.className = "linha-mensagem" + (minha ? " minha" : "");
    b.innerHTML = `<div class="balao">
        ${imagem ? `<img alt="Foto" src="${esc(m.imagemUrl)}">` : `<div class="${apagada ? "apagada" : ""}">${esc(texto)}</div>`}
        <small class="${minha && m.lida ? "lida" : ""}">${esc(horaChat(m.timestamp))}${minha ? (m.lida ? "  ✓✓" : "  ✓") : ""}</small>
        <span class="apagar">
          <button type="button" data-mim>Apagar só pra mim</button>
          ${minha && !apagada ? `<button type="button" data-todos>Apagar pra todos</button>` : ""}
        </span></div>`;
    b.querySelector("img")?.addEventListener("click", () => verImagem(m.imagemUrl));
    const erro = (e) => aviso("Não foi possível apagar: " + e.message);
    b.querySelector("[data-mim]").onclick = () => updateDoc(d.ref, { deletadoParaAdmin: true }).catch(erro);
    b.querySelector("[data-todos]")?.addEventListener("click", async () => {
      updateDoc(d.ref, { deletadoParaTodos: true, conteudo: await cifrar(TEXTO_APAGADA), tipo: "texto", imagemUrl: deleteField() }).catch(erro);
    });
    el.appendChild(b);
  }
  el.scrollTop = el.scrollHeight;
  marcarComoLida(uid, docs).catch(() => {});
}

// Conversa aberta: o que chegou já conta como lido
async function marcarComoLida(uid, docs) {
  const conv = doc(db, "conversas", uid);
  const atual = await getDoc(conv);
  if (atual.exists() && atual.data().naoLidasAdmin > 0) await updateDoc(conv, { naoLidasAdmin: 0 });
  const novas = docs.filter((d) => d.data().deAdmin === false && d.data().lida !== true);
  if (!novas.length) return;
  const lote = writeBatch(db);
  novas.forEach((d) => lote.update(d.ref, { lida: true }));
  await lote.commit();
}

async function gravarMensagem(uid, tipo, texto, imagemUrl, resumo) {
  const conv = doc(db, "conversas", uid);
  const msg = doc(collection(conv, "mensagens"));
  const m = {
    deAdmin: true, remetenteId: auth.currentUser.uid, conteudo: await cifrar(texto), tipo,
    timestamp: serverTimestamp(), lida: false, deletadoParaTodos: false, deletadoParaMorador: false, deletadoParaAdmin: false,
  };
  if (imagemUrl) m.imagemUrl = imagemUrl;
  const lote = writeBatch(db);
  lote.set(msg, m);
  lote.set(conv, {
    moradorUid: uid, ultimaMensagem: await cifrar(resumo), ultimoTimestamp: serverTimestamp(),
    ultimoDeAdmin: true, naoLidasMorador: increment(1),
  }, { merge: true });
  await lote.commit();
}

async function enviarFotoChat(foto) {
  if (!foto || !chatUid) return;
  aviso("Enviando foto…");
  try {
    const link = await enviarNaPasta("chat", chatUid, `${Date.now()}_${foto.name}`, foto);
    await gravarMensagem(chatUid, "imagem", "", link, "📷 Foto");
  } catch (e) { aviso("Não foi possível enviar: " + e.message); }
}

async function apagarConversa(uid, rotulo) {
  if (!confirm(`Apagar toda a conversa com ${rotulo}? As mensagens somem para todos.`)) return;
  try {
    ouvinteMensagens?.();
    ouvinteMensagens = chatUid = null;
    const conv = doc(db, "conversas", uid);
    const snap = await getDocs(collection(conv, "mensagens"));
    // Limite do Firestore: 500 operações por lote
    for (let i = 0; i < snap.docs.length; i += 450) {
      const lote = writeBatch(db);
      snap.docs.slice(i, i + 450).forEach((d) => lote.delete(d.ref));
      await lote.commit();
    }
    await deleteDoc(conv);
    aviso("Conversa apagada.");
    $("chatTopo").innerHTML = "<span>Escolha uma conversa</span>";
    $("chatMensagens").innerHTML = "";
    $("chatEnvio").hidden = true;
  } catch (e) { aviso("Erro ao apagar: " + e.message); }
}

function cartao(s, r, soLer) {
  const c = document.createElement("article");
  c.className = "cartao";
  c.style.setProperty("--cor", s.cor);
  const foto = s.foto && ehLink(r[s.foto]) ? `<img class="avatar" alt="" src="${esc(r[s.foto])}" data-foto>` : "";
  const det = (s.detalhes?.(r) || []).filter(([, v]) => v);
  c.innerHTML = `${foto}<h3>${esc(s.titulo_(r) || "(sem título)")}</h3>
    <div class="sub">${esc(s.sub?.(r) || "")}</div>
    ${det.length ? `<dl>${det.map(([k, v]) => `<dt>${esc(k)}</dt><dd>${esc(v)}</dd>`).join("")}</dl>` : ""}
    ${s.longo?.(r) ? `<div class="texto-longo">${esc(s.longo(r))}</div>` : ""}
    <div class="grade-anexos"></div>
    ${soLer ? "" : `<div class="botoes">
      <button class="btn pequeno contorno" data-editar>Editar</button>
      <button class="btn pequeno perigo" data-excluir>Excluir</button>
    </div>`}`;
  const anexos = listaDeTexto(r.anexos);
  montarAnexos(c.querySelector(".grade-anexos"), anexos);
  c.querySelector("[data-foto]")?.addEventListener("click", () => verImagem(r[s.foto]));
  if (soLer) return c;
  c.querySelector("[data-editar]").onclick = () => formulario(r);
  c.querySelector("[data-excluir]").onclick = () => excluir(s, r);
  return c;
}

// Leitura anterior de cada envio: o envio de antes, na mesma quadra e lote (a lista vem do mais novo)
function anotarAnteriores(lista) {
  const ultimaDoLote = {};
  for (const r of [...lista].reverse()) {
    const lote = `${String(r.quadra ?? "").toUpperCase()}|${r.lote ?? ""}`;
    r._anterior = ultimaDoLote[lote];
    ultimaDoLote[lote] = r.leitura;
  }
}
// "1250" e "1200" dão "50". Sem como calcular (sem anterior, texto ou gasto negativo): "—"
function gastoDeAgua(atual, anterior) {
  const numero = (t) => (t === undefined || t === null || String(t).trim() === "" ? NaN : Number(String(t).trim().replace(",", ".")));
  const a = numero(atual), b = numero(anterior);
  if (isNaN(a) || isNaN(b) || a < b) return "—";
  return String(Math.round((a - b) * 1000) / 1000).replace(".", ",");
}

function cartaoLeitura(r) {
  const c = document.createElement("article");
  c.className = "cartao";
  c.style.setProperty("--cor", secaoAtual.cor);
  const quando = r.criadoEm?.toDate?.().toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" }) || "";
  c.innerHTML = `<h3>Quadra ${esc(r.quadra || "—")} · Lote ${esc(r.lote || "—")}</h3>
    <div class="sub">${esc(r.nome || "")}${quando ? " — " + esc(quando) : ""}</div>
    <div class="leitura-anterior">Leitura anterior: ${esc(r._anterior ?? "—")}</div>
    <div class="leitura-linha">
      <div class="leitura-atual">Leitura atual: ${esc(r.leitura || "—")}</div>
      <div class="leitura-gasto"><small>Gastos do mês</small><strong>${esc(gastoDeAgua(r.leitura, r._anterior))}</strong></div>
    </div>
    ${ehLink(r.foto) ? `<img class="foto" alt="Foto do hidrômetro" src="${esc(r.foto)}">` : ""}
    <div class="botoes">
      ${ehLink(r.foto) ? `<button class="btn pequeno" data-baixar>Baixar foto</button>` : ""}
      <button class="btn pequeno perigo" data-excluir>Excluir</button>
    </div>`;
  c.querySelector(".foto")?.addEventListener("click", () => verImagem(r.foto));
  c.querySelector("[data-baixar]")?.addEventListener("click", () => baixar(r.foto));
  c.querySelector("[data-excluir]").onclick = () => excluir(secaoAtual, r,
    `Excluir a leitura da quadra ${r.quadra}, lote ${r.lote}?`);
  return c;
}

function renderizarAdmins(lista) {
  const s = secaoAtual;
  const el = $("listaSecao");
  const pedidos = s._pedidos || [];
  $("resumoSecao").textContent = `${registros.length} administrador(es)` + (pedidos.length ? ` · ${pedidos.length} pedido(s) aguardando` : "");
  el.innerHTML = "";
  for (const p of pedidos) {
    const c = document.createElement("article");
    c.className = "cartao";
    c.style.setProperty("--cor", "#F59E0B");
    c.innerHTML = `<span class="etiqueta">Pedido de cadastro</span><h3>${esc(p.nome || "")}</h3>
      <div class="sub">${esc(p.email || "")}${p.telefone ? " · " + esc(p.telefone) : ""}</div>
      <div class="botoes"><button class="btn pequeno sucesso" data-aprovar>Aprovar</button>
      <button class="btn pequeno perigo" data-recusar>Recusar</button></div>`;
    c.querySelector("[data-aprovar]").onclick = () => aprovarPedido(p);
    c.querySelector("[data-recusar]").onclick = () => recusarPedido(p);
    el.appendChild(c);
  }
  for (const a of lista) {
    const c = document.createElement("article");
    c.className = "cartao";
    c.style.setProperty("--cor", s.cor);
    const euMesmo = a._id === adminAtual?.uid;
    c.innerHTML = `<h3>${esc(a.nome || "")}${euMesmo ? ' <span class="etiqueta">você</span>' : ""}</h3>
      <div class="sub">${a.master ? "admin master" : esc(a.email || "")}${a.telefone ? " · " + esc(a.telefone) : ""}</div>
      ${euMesmo || a.master ? "" : `<div class="botoes"><button class="btn pequeno perigo" data-remover>Remover acesso</button></div>`}`;
    c.querySelector("[data-remover]")?.addEventListener("click", () => excluir(s, a,
      `Remover o acesso de administrador de ${a.nome || a.email}?`));
    el.appendChild(c);
  }
  if (!el.children.length) el.innerHTML = `<p class="vazio">Nenhum administrador encontrado.</p>`;
}

async function aprovarPedido(p) {
  const dados = { tipo: "admin" };
  for (const k of ["nome", "nomeLowercase", "primeiroNome", "primeiroNomeLowercase", "email", "telefone"]) dados[k] = p[k] ?? "";
  const lote = writeBatch(db);
  lote.set(doc(db, "administradores", p._id), dados);
  lote.delete(doc(db, "solicitacoes_admin", p._id));
  try { await lote.commit(); aviso("Administrador aprovado!"); abrirSecao("administradores"); }
  catch (e) { aviso("Erro ao aprovar: " + e.message); }
}
async function recusarPedido(p) {
  if (!confirm(`Recusar o pedido de ${p.nome || p.email}?`)) return;
  try { await updateDoc(doc(db, "solicitacoes_admin", p._id), { status: "recusado" }); aviso("Pedido recusado."); abrirSecao("administradores"); }
  catch (e) { aviso("Erro ao recusar: " + e.message); }
}

async function excluir(s, r, pergunta) {
  if (!confirm(pergunta || `Excluir "${s.titulo_?.(r) || "este registro"}"? Não dá para desfazer.`)) return;
  try {
    await deleteDoc(doc(db, s.colecao, r._id));
    aviso("Excluído.");
    registros = registros.filter((x) => x !== r);
    if (s.especial === "leituras") anotarAnteriores(registros);
    renderizar();
  } catch (e) { aviso("Erro ao excluir: " + e.message); }
}

// ---------------------------------------------------------------- formulário
let anexosEdicao = [];
let mesDoFormulario = null; // mês em que o registro novo está sendo adicionado
let fotosEdicao = {};

function campoHtml(c, r, editando) {
  const v = r?.[c.k] ?? "";
  const id = `campo_${c.k}`;
  const obrig = c.obrig ? " *" : "";
  const travado = editando && c.travaNaEdicao ? " disabled" : "";
  const cls = c.largo ? ' class="largo"' : "";
  switch (c.t) {
    case "textarea":
      return `<label${cls}>${esc(c.r)}${obrig}<textarea id="${id}" rows="4">${esc(v)}</textarea></label>`;
    case "datahora":
      return `<label${cls}>${esc(c.r)}${obrig}<input id="${id}" type="datetime-local" value="${esc(dataParaInput(v) || dataParaInput(agoraFormatado()))}"></label>`;
    case "hora":
      return `<label${cls}>${esc(c.r)}<input id="${id}" type="time" value="${esc(v)}"></label>`;
    case "dinheiro":
      return `<label${cls}>${esc(c.r)}${obrig}<input id="${id}" inputmode="decimal" placeholder="Ex.: 150,00" value="${esc(v === "" ? "" : String(v).replace(".", ","))}"></label>`;
    case "funcao": {
      const outra = v && !FUNCOES.includes(v);
      return `<label${cls}>${esc(c.r)}${obrig}<select id="${id}">
          <option value="">Selecione a função…</option>
          ${FUNCOES.map((f) => `<option${(outra ? f === "Outros" : f === v) ? " selected" : ""}>${esc(f)}</option>`).join("")}
        </select>
        <input id="${id}_outra" placeholder="Digite a nova função" value="${esc(outra ? v : "")}" ${outra ? "" : "hidden"}></label>`;
    }
    case "envolvidos": {
      const lista = listaDeTexto(v);
      return `<label${cls}>${esc(c.r)}<div class="campos">${[0, 1, 2, 3, 4].map((i) =>
        `<input id="${id}_${i}" placeholder="Envolvido ${i + 1}" value="${esc(lista[i] || "")}">`).join("")}</div></label>`;
    }
    case "foto":
      return `<div${cls}><label>${esc(c.r)}</label>
        <div class="anexo"><img class="miniatura" id="${id}_previa" alt="" ${ehLink(v) ? `src="${esc(v)}"` : 'hidden'}>
        <span class="nome" id="${id}_nome">${ehLink(v) ? "Foto atual" : "Sem foto"}</span>
        <span class="mini-acoes"><label class="btn pequeno">Escolher<input type="file" accept="image/*" id="${id}" hidden></label>
        <button type="button" class="btn pequeno perigo" id="${id}_tirar" ${v ? "" : "hidden"}>Tirar</button></span></div></div>`;
    default:
      return `<label${cls}>${esc(c.r)}${obrig}<input id="${id}" type="${c.t === "email" ? "email" : c.t === "tel" ? "tel" : "text"}" value="${esc(v)}"${travado}></label>`;
  }
}

function formulario(r, mes) {
  const s = secaoAtual;
  const editando = !!r;
  mesDoFormulario = mes;
  $("tituloDialogo").textContent = `${editando ? "Editar" : "Novo"} — ${s.titulo}`;
  $("camposDialogo").innerHTML = s.campos.map((c) => campoHtml(c, r, editando)).join("");
  $("erroDialogo").textContent = "";
  $("blocoAnexos").hidden = false;
  anexosEdicao = listaDeTexto(r?.anexos);
  fotosEdicao = {};
  atualizarAnexosEdicao();

  for (const c of s.campos) {
    if (c.t === "funcao") {
      const sel = $(`campo_${c.k}`), outra = $(`campo_${c.k}_outra`);
      sel.onchange = () => { outra.hidden = sel.value !== "Outros"; if (!outra.hidden) outra.focus(); };
    }
    if (c.t === "foto") {
      const input = $(`campo_${c.k}`);
      fotosEdicao[c.k] = r?.[c.k] || "";
      input.onchange = () => {
        const f = input.files[0];
        if (!f) return;
        fotosEdicao[c.k] = f;
        const previa = $(`campo_${c.k}_previa`);
        previa.src = URL.createObjectURL(f);
        previa.hidden = false;
        $(`campo_${c.k}_nome`).textContent = f.name + " (nova)";
        $(`campo_${c.k}_tirar`).hidden = false;
      };
      $(`campo_${c.k}_tirar`).onclick = () => {
        fotosEdicao[c.k] = "";
        $(`campo_${c.k}_previa`).hidden = true;
        $(`campo_${c.k}_nome`).textContent = "Sem foto";
        $(`campo_${c.k}_tirar`).hidden = true;
      };
    }
  }

  $("formRegistro").onsubmit = async (e) => {
    e.preventDefault();
    await salvar(s, r);
  };
  $("dialogo").showModal();
}

function atualizarAnexosEdicao() {
  montarAnexos($("listaAnexosEdicao"), anexosEdicao, (i) => {
    if (!confirm(`Remover "${nomeDoAnexo(anexosEdicao[i])}" deste cadastro?`)) return;
    anexosEdicao.splice(i, 1);
    atualizarAnexosEdicao();
  });
}
function adicionarArquivos(lista) {
  for (const f of lista) {
    if (f.size > LIMITE_ARQUIVO) { aviso(`"${f.name}" passa de 20 MB e não foi adicionado.`); continue; }
    anexosEdicao.push(f);
  }
  atualizarAnexosEdicao();
}
$("inputArquivos").onchange = (e) => { adicionarArquivos(e.target.files); e.target.value = ""; };
$("inputCamera").onchange = (e) => { adicionarArquivos(e.target.files); e.target.value = ""; };
const zona = $("zonaArquivos");
zona.ondragover = (e) => { e.preventDefault(); zona.classList.add("arrastando"); };
zona.ondragleave = () => zona.classList.remove("arrastando");
zona.ondrop = (e) => { e.preventDefault(); zona.classList.remove("arrastando"); adicionarArquivos(e.dataTransfer.files); };
$("btnCancelar").onclick = () => $("dialogo").close();
$("btnFecharDialogo").onclick = () => $("dialogo").close();

function lerCampos(s) {
  const v = {};
  for (const c of s.campos) {
    const el = $(`campo_${c.k}`);
    if (c.t === "datahora") v[c.k] = inputParaData(el.value);
    else if (c.t === "funcao") v[c.k] = el.value === "Outros" ? $(`campo_${c.k}_outra`).value.trim() : el.value;
    else if (c.t === "envolvidos") v[c.k] = JSON.stringify([0, 1, 2, 3, 4].map((i) => $(`campo_${c.k}_${i}`).value.trim()).filter(Boolean));
    else if (c.t === "foto") continue;
    else v[c.k] = el.value.trim();
    if (c.obrig && !v[c.k] && !(el.disabled)) throw new Error(`Preencha "${c.r}".`);
  }
  return v;
}

async function salvar(s, r) {
  const botao = $("btnSalvar");
  const erro = $("erroDialogo");
  erro.textContent = "";
  let v;
  try { v = lerCampos(s); } catch (e) { erro.textContent = e.message; return; }
  if (s.id === "despesas") {
    const n = paraNumero(v.valor);
    if (isNaN(n)) { erro.textContent = "Valor inválido."; return; }
    v.valor = n;
  }
  if (s.especial === "morador") {
    v.quadra = (v.quadra || r?.quadra || "").toUpperCase();
    v.lote = v.lote || r?.lote || "";
  }
  botao.disabled = true;
  try {
    for (const k of Object.keys(fotosEdicao)) {
      const f = fotosEdicao[k];
      v[k] = f instanceof File ? await enviarArquivo(s.modulo, f) : (f || "");
    }
    const links = await enviarTodos(s.modulo, anexosEdicao, (t) => (botao.textContent = t));
    v.anexos = s.anexos === "texto" ? JSON.stringify(links) : links;
    Object.assign(v, s.extra?.(r || {}) || {});
    if (!r && mesDoFormulario) v.mes = mesDoFormulario;

    if (s.especial === "morador" && !r) await criarMorador(v);
    else if (r) await updateDoc(doc(db, s.colecao, r._id), v);
    else {
      const novo = doc(collection(db, s.colecao));
      if (s.gravaId) v.id = novo.id;
      await setDoc(novo, v);
    }
    $("dialogo").close();
    aviso(r ? "Alterações salvas!" : "Salvo!");
    abrirSecao(s.id);
  } catch (e) {
    erro.textContent = "Erro ao salvar: " + (e.message || e);
  } finally {
    botao.disabled = false;
    botao.textContent = "Salvar";
  }
}

// Morador novo: conta de login (quadra+lote, senha = 6 primeiros dígitos do CPF), igual ao app
async function criarMorador(v) {
  const numeros = (v.cpf || "").replace(/\D/g, "");
  if (numeros.length < 6) throw new Error("Informe o CPF completo: a senha inicial são os 6 primeiros dígitos.");
  const senha = numeros.slice(0, 6);
  const usuario = (v.quadra + v.lote).toUpperCase();
  const loginEmail = emailDoMorador(usuario);
  let uid;
  try {
    uid = (await createUserWithEmailAndPassword(authCadastro, loginEmail, senha)).user.uid;
  } catch (e) {
    if (e.code === "auth/email-already-in-use") throw new Error(`Já existe um morador com o login ${usuario}.`);
    throw e;
  } finally {
    await signOut(authCadastro).catch(() => {});
  }
  await setDoc(doc(db, "moradores", uid), { ...v, cod: uid, usuario, loginEmail });
  alert(`Morador cadastrado!\n\nPasse os dados de acesso:\nUsuário: ${usuario}\nSenha: ${senha} (6 primeiros dígitos do CPF)`);
}

// Novo administrador (mesmos campos do app)
function formularioAdmin() {
  $("tituloDialogo").textContent = "Novo administrador";
  $("camposDialogo").innerHTML = `
    <label class="largo">Nome completo *<input id="adm_nome"></label>
    <label>E-mail *<input id="adm_email" type="email"></label>
    <label>Telefone<input id="adm_tel" type="tel"></label>
    <label class="largo">Senha (6 números) *<input id="adm_senha" inputmode="numeric" maxlength="6"></label>`;
  $("blocoAnexos").hidden = true;
  $("erroDialogo").textContent = "";
  $("formRegistro").onsubmit = async (e) => {
    e.preventDefault();
    const nome = $("adm_nome").value.trim(), email = $("adm_email").value.trim();
    const telefone = $("adm_tel").value.trim(), senha = $("adm_senha").value.trim();
    if (!nome || !email || !senha) { $("erroDialogo").textContent = "Preencha nome, e-mail e senha."; return; }
    if (!/^\d{6}$/.test(senha)) { $("erroDialogo").textContent = "A senha deve ter 6 dígitos numéricos."; return; }
    const primeiro = nome.split(" ")[0];
    $("btnSalvar").disabled = true;
    try {
      let uid;
      try { uid = (await createUserWithEmailAndPassword(authCadastro, email, senha)).user.uid; }
      finally { await signOut(authCadastro).catch(() => {}); }
      await setDoc(doc(db, "administradores", uid), {
        nome, nomeLowercase: nome.toLowerCase(), primeiroNome: primeiro, primeiroNomeLowercase: primeiro.toLowerCase(),
        email, tipo: "admin", telefone,
      });
      $("dialogo").close();
      aviso("Administrador cadastrado!");
      abrirSecao("administradores");
    } catch (err) {
      $("erroDialogo").textContent = err.code === "auth/email-already-in-use"
        ? "Já existe uma conta com esse e-mail." : "Erro: " + err.message;
    } finally { $("btnSalvar").disabled = false; }
  };
  $("dialogo").showModal();
}

// ---------------------------------------------------------------- exportar (CSV que abre no Excel)
function exportar() {
  const s = subLista || secaoAtual;
  const lista = filtrados();
  let colunas;
  if (s.especial === "leituras") {
    colunas = [["Quadra", (r) => r.quadra], ["Lote", (r) => r.lote], ["Morador", (r) => r.nome], ["Leitura anterior", (r) => r._anterior], ["Leitura atual", (r) => r.leitura],
      ["Gastos do mês", (r) => gastoDeAgua(r.leitura, r._anterior)],
      ["Data", (r) => r.criadoEm?.toDate?.().toLocaleString("pt-BR") || ""], ["Foto", (r) => r.foto]];
  } else if (s.especial === "boletos") {
    const boletos = (r) => s._boletos?.[r._id] || [];
    colunas = [["Quadra", (r) => r.quadra], ["Lote", (r) => r.lote], ["Morador", (r) => r.nome],
      ["Boletos", (r) => boletos(r).length], ["Não pagos", (r) => boletos(r).filter((b) => b.naoPago).length]];
  } else if (s.especial === "admins") {
    colunas = [["Nome", (r) => r.nome], ["E-mail", (r) => r.email], ["Telefone", (r) => r.telefone]];
  } else {
    colunas = s.campos.map((c) => [c.r, (r) => c.t === "envolvidos" ? listaDeTexto(r[c.k]).join(", ") : r[c.k]]);
    colunas.push(["Anexos", (r) => listaDeTexto(r.anexos).join(" | ")]);
  }
  const celula = (x) => `"${String(x ?? "").replace(/"/g, '""')}"`;
  const linhas = [colunas.map(([t]) => celula(t)).join(";"), ...lista.map((r) => colunas.map(([, f]) => celula(f(r))).join(";"))];
  const blob = new Blob(["﻿" + linhas.join("\r\n")], { type: "text/csv;charset=utf-8" });
  const a = Object.assign(document.createElement("a"), {
    href: URL.createObjectURL(blob),
    download: `${s.titulo.replace(/[^\p{L}\p{N}]+/gu, "_")}_${new Date().toISOString().slice(0, 10)}.csv`,
  });
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 5000);
}

// Configurações (engrenagem no canto superior esquerdo), igual no app: por enquanto a Política de Privacidade
$("btnConfig").onclick = (e) => { e.stopPropagation(); $("menuConfig").hidden = !$("menuConfig").hidden; };
document.addEventListener("click", () => { $("menuConfig").hidden = true; });
$("btnPrivacidade").onclick = async () => {
  $("menuConfig").hidden = true;
  if (!$("textoPrivacidade").innerHTML) {
    const html = await (await fetch("privacidade.html")).text();
    $("textoPrivacidade").innerHTML = new DOMParser().parseFromString(html, "text/html").querySelector(".container").innerHTML;
  }
  $("painelPrivacidade").hidden = false;
  $("painelPrivacidade").scrollIntoView({ behavior: "smooth", block: "start" });
};
$("btnFecharPrivacidade").onclick = () => { $("painelPrivacidade").hidden = true; };
