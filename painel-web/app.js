// Painel web do Condomínio Sociedade Morro Grande.
// Mesmo Firebase e mesmo formato de dados do app Android (Administradores/Moradores),
// então o que é salvo aqui aparece no app e vice-versa.
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut, createUserWithEmailAndPassword, connectAuthEmulator
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import {
  getFirestore, collection, doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, writeBatch, query, where, connectFirestoreEmulator
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";
import {
  getStorage, ref as refArquivo, uploadBytes, getDownloadURL, connectStorageEmulator
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-storage.js";

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
    id: "avisos", titulo: "Avisos", cor: "#EA580C", colecao: "avisos", modulo: "avisos", anexos: "texto",
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
    id: "assembleias", titulo: "Assembleias", cor: "#7C3AED", colecao: "assembleias", modulo: "assembleias", anexos: "texto",
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
    id: "despesas", titulo: "Despesas", cor: "#15803D", colecao: "despesas", modulo: "despesas", anexos: "lista",
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
    id: "ocorrencias", titulo: "Ocorrências", cor: "#DC2626", colecao: "ocorrencias", modulo: "ocorrencias", anexos: "texto",
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
    id: "manutencoes", titulo: "Manutenção", cor: "#78350F", colecao: "manutencoes", modulo: "manutencoes", anexos: "lista",
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
    id: "funcionarios", titulo: "Funcionários", cor: "#0F766E", colecao: "funcionarios", modulo: "funcionarios", anexos: "lista",
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
    id: "moradores", titulo: "Moradores", cor: "#0284C7", colecao: "moradores", modulo: "moradores", anexos: "lista",
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
  { id: "leituras", titulo: "Leitura d'Água", cor: "#0369A1", colecao: "leituras_agua", especial: "leituras", semNovo: true },
  { id: "administradores", titulo: "Administradores", cor: "#3730A3", colecao: "administradores", especial: "admins" },
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
  if (arquivo.size > LIMITE_ARQUIVO) throw new Error(`"${arquivo.name}" passa de 20 MB`);
  const nome = arquivo.name.replace(/[\\/]/g, "_");
  const r = refArquivo(storage, `${modulo}/${crypto.randomUUID()}/${nome}`);
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
    abrirSecao(secaoAtual.id);
  } catch (e) {
    mostrarLogin("Erro ao verificar o acesso: " + e.message);
  }
});
function mostrarLogin(erro = "") {
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
    await signInWithEmailAndPassword(auth, emailDoAdmin($("loginEmail").value.trim()), $("loginSenha").value);
  } catch (err) {
    $("btnEntrar").disabled = false;
    $("erroLogin").textContent = /invalid|wrong|not-found/.test(err.code || "")
      ? "E-mail ou senha incorretos." : "Não foi possível entrar: " + err.message;
  }
};
$("btnSair").onclick = () => { if (confirm("Deseja sair do painel?")) signOut(auth); };

// ---------------------------------------------------------------- menu
$("navSecoes").innerHTML = SECOES.map((s) =>
  `<button data-secao="${s.id}"><span class="ponto" style="background:${s.cor}"></span>${esc(s.titulo)}</button>`).join("");
$("navSecoes").onclick = (e) => {
  const b = e.target.closest("[data-secao]");
  if (b) { abrirSecao(b.dataset.secao); fecharMenu(); }
};
$("btnMenu").onclick = () => { $("menu").classList.add("aberto"); $("fundoMenu").classList.add("aberto"); };
$("fundoMenu").onclick = fecharMenu;
function fecharMenu() { $("menu").classList.remove("aberto"); $("fundoMenu").classList.remove("aberto"); }

$("busca").oninput = () => renderizar();
$("btnNovo").onclick = () => (secaoAtual.especial === "admins" ? formularioAdmin() : formulario(null));
$("btnExportar").onclick = () => exportar();

async function abrirSecao(id) {
  secaoAtual = SECOES.find((s) => s.id === id) || SECOES[0];
  document.querySelectorAll("#navSecoes button").forEach((b) => b.classList.toggle("ativo", b.dataset.secao === secaoAtual.id));
  $("tituloSecao").textContent = secaoAtual.titulo;
  $("btnNovo").hidden = !!secaoAtual.semNovo;
  $("btnNovo").textContent = secaoAtual.especial === "admins" ? "+ Novo administrador" : "+ Novo";
  $("busca").value = "";
  $("listaSecao").innerHTML = `<p class="vazio">Carregando…</p>`;
  $("resumoSecao").textContent = "";
  try {
    registros = await carregar(secaoAtual);
    renderizar();
  } catch (e) {
    $("listaSecao").innerHTML = `<p class="vazio">Erro ao carregar: ${esc(e.message)}</p>`;
  }
}

async function carregar(s) {
  const snap = await getDocs(collection(db, s.colecao));
  let lista = snap.docs.map((d) => ({ ...d.data(), _id: d.id }));
  // A conta de visualização dos admins (morador com verTodos) não é um morador de verdade
  if (s.colecao === "moradores") lista = lista.filter((r) => r.verTodos !== true);
  if (s.especial === "leituras") {
    lista.sort((a, b) => (b.criadoEm?.toMillis?.() || 0) - (a.criadoEm?.toMillis?.() || 0));
  } else if (s.especial === "admins") {
    const pedidos = await getDocs(query(collection(db, "solicitacoes_admin"), where("status", "==", "pendente")));
    s._pedidos = pedidos.docs.map((d) => ({ ...d.data(), _id: d.id }));
    lista.sort((a, b) => (a.nome || "").localeCompare(b.nome || "", "pt-BR"));
  } else if (s.campoData) {
    lista.sort((a, b) => dataEmMs(b[s.campoData]) - dataEmMs(a[s.campoData]));
  } else if (s.campoNome) {
    lista.sort((a, b) => (a[s.campoNome] || "").localeCompare(b[s.campoNome] || "", "pt-BR"));
  }
  return lista;
}

function filtrados() {
  const termo = $("busca").value.trim().toLowerCase();
  if (!termo) return registros;
  return registros.filter((r) => JSON.stringify(r).toLowerCase().includes(termo));
}

// ---------------------------------------------------------------- listas
function renderizar() {
  const s = secaoAtual;
  const lista = filtrados();
  const el = $("listaSecao");
  el.style.setProperty("--cor", s.cor);
  if (s.especial === "admins") return renderizarAdmins(lista);
  $("resumoSecao").textContent = `${lista.length} de ${registros.length} ${registros.length === 1 ? "registro" : "registros"}`;
  if (!lista.length) { el.innerHTML = `<p class="vazio">Nada por aqui ainda.</p>`; return; }
  el.innerHTML = "";
  for (const r of lista) el.appendChild(s.especial === "leituras" ? cartaoLeitura(r) : cartao(s, r));
}

function cartao(s, r) {
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
    <div class="botoes">
      <button class="btn pequeno contorno" data-editar>Editar</button>
      <button class="btn pequeno perigo" data-excluir>Excluir</button>
    </div>`;
  const anexos = listaDeTexto(r.anexos);
  montarAnexos(c.querySelector(".grade-anexos"), anexos);
  c.querySelector("[data-foto]")?.addEventListener("click", () => verImagem(r[s.foto]));
  c.querySelector("[data-editar]").onclick = () => formulario(r);
  c.querySelector("[data-excluir]").onclick = () => excluir(s, r);
  return c;
}

function cartaoLeitura(r) {
  const c = document.createElement("article");
  c.className = "cartao";
  c.style.setProperty("--cor", secaoAtual.cor);
  const quando = r.criadoEm?.toDate?.().toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" }) || "";
  c.innerHTML = `<h3>Quadra ${esc(r.quadra || "—")} · Lote ${esc(r.lote || "—")}</h3>
    <div class="sub">${esc(r.nome || "")}${quando ? " — " + esc(quando) : ""}</div>
    <div style="font-size:22px;font-weight:800">Leitura: ${esc(r.leitura || "—")}</div>
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
    renderizar();
  } catch (e) { aviso("Erro ao excluir: " + e.message); }
}

// ---------------------------------------------------------------- formulário
let anexosEdicao = [];
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

function formulario(r) {
  const s = secaoAtual;
  const editando = !!r;
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
  const s = secaoAtual;
  const lista = filtrados();
  let colunas;
  if (s.especial === "leituras") {
    colunas = [["Quadra", (r) => r.quadra], ["Lote", (r) => r.lote], ["Morador", (r) => r.nome], ["Leitura", (r) => r.leitura],
      ["Data", (r) => r.criadoEm?.toDate?.().toLocaleString("pt-BR") || ""], ["Foto", (r) => r.foto]];
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
