// Área do Morador na web (morador.html): o painel do app Moradores para quem usa iPhone.
// Mesmo Firebase, mesmo login (quadra+lote e os 6 primeiros dígitos do CPF) e mesmo formato de
// dados do app Android, então o que o morador faz aqui aparece no app e no painel na hora.
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth, onAuthStateChanged, signInWithEmailAndPassword, signOut, connectAuthEmulator
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import {
  getFirestore, collection, doc, getDoc, addDoc, updateDoc, writeBatch, query, where, orderBy, onSnapshot,
  serverTimestamp, increment, deleteField, connectFirestoreEmulator
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

// Nome próprio: a sessão do morador fica separada da do painel administrativo no mesmo navegador
// (sem isso, abrir um derrubava o login do outro)
const app = initializeApp(config, "area-morador");
const auth = getAuth(app);
const db = getFirestore(app);
const storage = getStorage(app);

// Só para testes no computador: http://localhost:5000/morador.html?emulador usa os emuladores locais
if (location.hostname === "localhost" && new URLSearchParams(location.search).has("emulador")) {
  connectAuthEmulator(auth, "http://localhost:9099", { disableWarnings: true });
  connectFirestoreEmulator(db, "localhost", 8085);
  connectStorageEmulator(storage, "localhost", 9199);
}

const $ = (id) => document.getElementById(id);
const DOMINIO_MORADOR = "@moradores.morrogrande.app";
// Contas master (ContaHelper.java): a senha delas tem letras, as dos moradores são só números
const USUARIOS_MASTER = ["condominiomorrogrande", "condominiosociedademorrogrande"];
const DIA_VENCIMENTO_BOLETO = 15;

// ---------------------------------------------------------------- assinatura (Assinatura.java)
// Cobrança desligada enquanto o app Android não é finalizado: ligar junto com o app e functions/index.js
const COBRANCA_ATIVA = false;
const MES_INICIO_COBRANCA = "2027-01";
const ULTIMO_DIA_SEM_TRAVAR = 5;

function assinaturaBloqueada(pagoAteMes) {
  // Horário de Brasília, igual ao servidor
  const partes = Object.fromEntries(new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Sao_Paulo", year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(new Date()).map((p) => [p.type, p.value]));
  const mesAtual = `${partes.year}-${partes.month}`;
  const cobrado = COBRANCA_ATIVA && mesAtual >= MES_INICIO_COBRANCA;
  const pago = !cobrado || (pagoAteMes && pagoAteMes >= mesAtual);
  return !pago && +partes.day > ULTIMO_DIA_SEM_TRAVAR;
}

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
function limparUsuario(usuario) {
  return usuario.normalize("NFD").replace(/[^A-Za-z0-9]/g, "").toLowerCase();
}
function dinheiro(v) {
  const n = typeof v === "number" ? v : paraNumero(v);
  return isNaN(n) ? (v ?? "") : n.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}
function paraNumero(v) {
  let s = String(v ?? "").trim().replace(/[^\d,.-]/g, "");
  if (s.includes(",")) s = s.replace(/\./g, "").replace(",", ".");
  return parseFloat(s);
}
function dataEmMs(s) {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})(?:\s+(\d{2}):(\d{2}))?/.exec(s || "");
  return m ? new Date(+m[3], +m[2] - 1, +m[1], +(m[4] || 0), +(m[5] || 0)).getTime() : 0;
}
const chaveDoMes = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
function dataDoMes(mes, dia = 1) {
  const [ano, m] = mes.split("-").map(Number);
  return new Date(ano, m - 1, dia, 23, 59, 59);
}
// "2026-10" vira "Outubro de 2026"
function textoDoMes(mes) {
  const texto = dataDoMes(mes).toLocaleDateString("pt-BR", { month: "long", year: "numeric" });
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}
const dataBr = (d) => d.toLocaleDateString("pt-BR");
const horaBr = (d) => d.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
const loteTexto = (lote) => {
  const l = String(lote ?? "").trim();
  return /^\d{1,9}$/.test(l) ? l.padStart(2, "0") : l || "-";
};
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
function verImagem(src) {
  const v = document.createElement("div");
  v.className = "visualizador";
  v.innerHTML = `<img alt="" src="${esc(src)}">`;
  v.onclick = () => v.remove();
  document.body.appendChild(v);
}
// PDF dentro da página, só para ver (igual ao VisualizarPdfActivity do app): as páginas viram imagens
// pelo pdf.js, sem o leitor do navegador e o botão de baixar dele
let pdfjs = null;
async function carregarPdfJs() {
  if (pdfjs) return pdfjs;
  const base = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/";
  await new Promise((ok, falha) => {
    const s = Object.assign(document.createElement("script"), { src: base + "pdf.min.js", onload: ok, onerror: falha });
    document.head.appendChild(s);
  });
  pdfjs = window.pdfjsLib;
  pdfjs.GlobalWorkerOptions.workerSrc = base + "pdf.worker.min.js";
  return pdfjs;
}
async function verPdf(link) {
  const v = document.createElement("div");
  v.className = "visualizador visualizador-pdf";
  v.innerHTML = `<div class="pdf-topo"><span>${esc(nomeDoAnexo(link))}</span>
      <button type="button" class="btn contorno pequeno">Fechar</button></div>
    <div class="pdf-paginas"><p class="vazio">Carregando…</p></div>`;
  v.querySelector("button").onclick = () => v.remove();
  v.oncontextmenu = (e) => e.preventDefault();
  document.body.appendChild(v);
  const paginas = v.querySelector(".pdf-paginas");
  try {
    const lib = await carregarPdfJs();
    const dados = await (await fetch(link)).arrayBuffer();
    const pdf = await lib.getDocument({ data: dados }).promise;
    paginas.innerHTML = "";
    const largura = Math.min(paginas.clientWidth || 800, 900);
    for (let n = 1; n <= pdf.numPages; n++) {
      const pagina = await pdf.getPage(n);
      const escala = (largura / pagina.getViewport({ scale: 1 }).width) * (window.devicePixelRatio || 1);
      const vp = pagina.getViewport({ scale: escala });
      const canvas = Object.assign(document.createElement("canvas"), { width: vp.width, height: vp.height });
      await pagina.render({ canvasContext: canvas.getContext("2d"), viewport: vp }).promise;
      paginas.appendChild(canvas);
    }
  } catch (e) {
    paginas.innerHTML = `<p class="vazio">Não foi possível abrir o PDF: ${esc(e.message || String(e))}</p>`;
  }
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
// Só os anexos que já estão na nuvem; os que ficaram só no celular de origem não abrem aqui.
// Prestação de contas, avisos e assembleias: o morador só abre e visualiza (sem baixar)
function htmlAnexos(itens) {
  return itens.filter(ehLink).map((link, i) => {
    const nome = nomeDoAnexo(link);
    const tipo = tipoDoAnexo(nome);
    const icone = tipo.img
      ? `<img class="miniatura" data-ver="${i}" alt="" src="${esc(link)}">`
      : `<span class="tipo" data-abrir="${i}" style="background:${tipo.cor}">${tipo.rot}</span>`;
    return `<div class="anexo">${icone}<span class="nome" title="${esc(nome)}">${esc(nome)}</span>
      <span class="mini-acoes"><button type="button" class="btn pequeno" data-abrir="${i}">Abrir</button></span></div>`;
  }).join("");
}
function ligarAnexos(container, itens) {
  const links = itens.filter(ehLink);
  container.onclick = (e) => {
    const b = e.target.closest("[data-abrir],[data-ver]");
    if (!b) return;
    const link = links[+(b.dataset.abrir ?? b.dataset.ver)];
    const tipo = tipoDoAnexo(nomeDoAnexo(link));
    if (b.dataset.ver !== undefined || tipo.img) verImagem(link);
    else if (tipo.rot === "PDF") verPdf(link);
    else aviso("Este arquivo não pode ser visualizado aqui. Peça à administração a versão em PDF.");
  };
}

// Foto do celular reduzida para no máximo 1600px em JPEG: envia rápido e cabe no limite de 10 MB
async function reduzirFoto(arquivo, nome) {
  try {
    const url = URL.createObjectURL(arquivo);
    const img = await new Promise((ok, falha) => {
      const i = new Image();
      i.onload = () => ok(i);
      i.onerror = falha;
      i.src = url;
    });
    const escala = Math.min(1, 1600 / Math.max(img.naturalWidth, img.naturalHeight));
    const canvas = Object.assign(document.createElement("canvas"), {
      width: Math.round(img.naturalWidth * escala), height: Math.round(img.naturalHeight * escala),
    });
    canvas.getContext("2d").drawImage(img, 0, 0, canvas.width, canvas.height);
    URL.revokeObjectURL(url);
    const blob = await new Promise((ok) => canvas.toBlob(ok, "image/jpeg", 0.85));
    if (blob) return new File([blob], nome, { type: "image/jpeg" });
  } catch { /* formato que o navegador não abre: vai o original */ }
  return arquivo;
}
async function enviarNaPasta(modulo, id, nomeArquivo, arquivo) {
  if (arquivo.size > 10 * 1024 * 1024) throw new Error("a foto passa de 10 MB");
  const nome = nomeArquivo.replace(/[\\/]/g, "_");
  const r = refArquivo(storage, `${modulo}/${id}/${nome}`);
  await uploadBytes(r, arquivo, { contentType: arquivo.type || "image/jpeg" });
  return getDownloadURL(r);
}

// ---------------------------------------------------------------- estado e telas
let morador = null; // cadastro em moradores/{uid}
let uid = null;
let ouvintesGerais = []; // cadastro, assinatura, selo do chat, boletos: enquanto estiver logado
let ouvintesTela = []; // só da tela aberta
let telaAtual = "inicio";
let assinaturaTravada = false;
let temBoletoVencido = false;

const TELAS = ["telaLogin", "telaFoto", "telaBloqueio", "telaMaster", "telaPainel"];
function mostrar(tela) {
  for (const t of TELAS) $(t).hidden = t !== tela;
}
function pararTela() {
  ouvintesTela.forEach((parar) => parar());
  ouvintesTela = [];
}
function pararTudo() {
  pararTela();
  ouvintesGerais.forEach((parar) => parar());
  ouvintesGerais = [];
}
const nomeResponsavel = (m) => {
  const temInquilino = m?.temInquilino === true && String(m.inquilinoNome ?? "").trim();
  return String((temInquilino ? m.inquilinoNome : m?.nome) ?? "").trim();
};

// ---------------------------------------------------------------- login
onAuthStateChanged(auth, async (user) => {
  pararTudo();
  if (!user) {
    morador = uid = null;
    return mostrarLogin();
  }
  try {
    const d = await getDoc(doc(db, "moradores", user.uid));
    if (!d.exists()) {
      await signOut(auth);
      return mostrarLogin("Esta conta não é de morador.");
    }
    uid = user.uid;
    morador = d.data();
    if (morador.verTodos === true) return mostrar("telaMaster");
    iniciarPainel();
  } catch (e) {
    mostrarLogin("Erro ao verificar o acesso: " + e.message);
  }
});
// O código do erro vai junto para dar para saber o que aconteceu num aparelho que não está aqui
function mensagemDeErroLogin(err, incorretos) {
  const codigo = err.code || "";
  if (/invalid-credential|invalid-login|wrong-password|user-not-found|invalid-email/.test(codigo)) return incorretos;
  if (codigo.includes("too-many-requests")) return "Muitas tentativas seguidas. Espere alguns minutos e tente de novo.";
  if (codigo.includes("network-request-failed")) return "Sem conexão com o servidor. Confira a internet e tente de novo.";
  return `Erro ao fazer login (${codigo || err.message}).`;
}
function mostrarLogin(erro = "") {
  mostrar("telaLogin");
  $("erroLogin").textContent = erro;
  $("btnEntrar").disabled = false;
}
// A senha do morador é só número (teclado numérico no iPhone); a das contas master tem letras
$("loginUsuario").oninput = (e) => {
  $("loginSenha").inputMode = USUARIOS_MASTER.includes(limparUsuario(e.target.value)) ? "text" : "numeric";
};
$("formLogin").onsubmit = async (e) => {
  e.preventDefault();
  $("btnEntrar").disabled = true;
  $("erroLogin").textContent = "";
  const usuario = $("loginUsuario").value.trim();
  // E-mail é login de administrador: aqui ele virava um usuário de morador que não existe
  if (usuario.includes("@")) {
    $("btnEntrar").disabled = false;
    $("erroLogin").textContent = "Este é o login de administrador. Toque em \"Sou administrador\" abaixo.";
    return;
  }
  try {
    // Senha sem espaços nas pontas, igual ao app (o iPhone às vezes deixa um espaço no fim)
    await signInWithEmailAndPassword(auth, limparUsuario(usuario) + DOMINIO_MORADOR, $("loginSenha").value.trim());
  } catch (err) {
    $("btnEntrar").disabled = false;
    $("erroLogin").textContent = mensagemDeErroLogin(err, "Usuário ou senha incorretos!");
  }
};
document.querySelectorAll("[data-sair]").forEach((b) => {
  b.onclick = () => { if (confirm("Deseja sair da sua conta?")) signOut(auth); };
});

// ---------------------------------------------------------------- painel
function iniciarPainel() {
  // Cadastro em tempo real: nome, foto e inquilino trocados pelo admin aparecem na hora
  ouvintesGerais.push(onSnapshot(doc(db, "moradores", uid), (d) => {
    if (!d.exists()) { signOut(auth); return; }
    morador = d.data();
    atualizarTela();
  }));
  // Assinatura paga ou vencida reflete com a tela aberta, como no app
  ouvintesGerais.push(onSnapshot(doc(db, "config", "assinatura"), (d) => {
    assinaturaTravada = assinaturaBloqueada(d.data()?.pagoAteMes);
    atualizarTela();
  }, () => {}));
  ouvintesGerais.push(onSnapshot(doc(db, "conversas", uid), (d) => {
    const n = d.data()?.naoLidasMorador || 0;
    $("seloChat").hidden = n === 0;
    $("seloChat").textContent = n > 99 ? "99+" : n;
  }, () => {}));
  // Alerta no quadro "Pagamentos" quando algum boleto foi marcado como não pago
  ouvintesGerais.push(onSnapshot(query(collection(db, "boletos"), where("moradorUid", "==", uid)), (s) => {
    temBoletoVencido = s.docs.some((d) => d.data().naoPago === true);
    if (telaAtual === "inicio") renderizarInicio();
  }, () => {}));
}

let fotoPendente = null;
function atualizarTela() {
  if (!morador) return;
  if (assinaturaTravada) { pararTela(); return mostrar("telaBloqueio"); }
  // Primeiro acesso: sem foto não entra (FotoMoradorActivity)
  if (!ehLink(morador.foto)) {
    pararTela();
    const primeiro = nomeResponsavel(morador).split(/\s+/)[0];
    $("textoFoto").textContent = `${primeiro ? `Olá, ${primeiro}! ` : ""}Antes de entrar, coloque uma foto sua. `
      + "Ela ajuda a portaria e a administração a identificar os moradores.";
    return mostrar("telaFoto");
  }
  const primeiro = nomeResponsavel(morador).split(/\s+/)[0];
  $("olaMorador").textContent = primeiro ? `Olá, ${primeiro}!` : "Área do Morador";
  $("loteMorador").textContent = `Quadra ${String(morador.quadra ?? "-").toUpperCase()} · Lote ${loteTexto(morador.lote)}`;
  const entrando = $("telaPainel").hidden;
  mostrar("telaPainel");
  if (entrando) abrir(telaDoEndereco(), false);
}

// ---------------------------------------------------------------- foto do primeiro acesso
function escolherFoto(arquivo) {
  if (!arquivo) return;
  fotoPendente = arquivo;
  $("previaFoto").src = URL.createObjectURL(arquivo);
  $("previaFoto").hidden = false;
  $("btnSalvarFoto").disabled = false;
}
$("fotoCamera").onchange = (e) => { escolherFoto(e.target.files[0]); e.target.value = ""; };
$("fotoGaleria").onchange = (e) => { escolherFoto(e.target.files[0]); e.target.value = ""; };
$("btnSalvarFoto").onclick = async () => {
  if (!fotoPendente) return;
  const b = $("btnSalvarFoto");
  b.disabled = true;
  b.textContent = "Enviando…";
  try {
    const link = await enviarNaPasta("moradores", uid, "foto.jpg", await reduzirFoto(fotoPendente, "foto.jpg"));
    await updateDoc(doc(db, "moradores", uid), { foto: link });
    fotoPendente = null;
    aviso("Foto salva!");
  } catch (e) {
    b.disabled = false;
    aviso("Erro ao salvar a foto: " + e.message);
  }
  b.textContent = "Salvar foto e entrar";
};

// ---------------------------------------------------------------- navegação
// Cada tela fica no endereço (#avisos, #chat…): o voltar do iPhone volta para o início
const QUADROS = [
  { id: "despesas", titulo: "Prestação de Contas", cor: "#2E7D32" },
  { id: "avisos", titulo: "Avisos", cor: "#EF6C00" },
  { id: "leitura", titulo: "Leitura d'Água", cor: "#0284C7" },
  { id: "pagamentos", titulo: "Pagamentos", cor: "#059669" },
  { id: "assembleias", titulo: "Assembleias", cor: "#8E24AA" },
];
const TITULOS = { ...Object.fromEntries(QUADROS.map((q) => [q.id, q.titulo])), chat: "Chat com a Administração" };
const telaDoEndereco = () => (TITULOS[location.hash.slice(1)] ? location.hash.slice(1) : "inicio");

function abrir(tela, guardarNoHistorico = true) {
  if (guardarNoHistorico && tela !== telaAtual) history.pushState(null, "", tela === "inicio" ? location.pathname + location.search : "#" + tela);
  pararTela();
  telaAtual = tela;
  $("cabecalhoSecao").hidden = tela === "inicio";
  $("tituloSecao").textContent = TITULOS[tela] || "";
  const el = $("conteudo");
  el.onclick = null;
  el.innerHTML = "";
  window.scrollTo(0, 0);
  if (tela === "inicio") return renderizarInicio();
  if (tela === "leitura") return renderizarLeitura();
  if (tela === "pagamentos") return renderizarPagamentos();
  if (tela === "chat") return renderizarChat();
  renderizarPorMes(tela);
}
window.onpopstate = () => { if (!$("telaPainel").hidden) abrir(telaDoEndereco(), false); };
$("btnVoltar").onclick = () => (history.length > 1 && location.hash ? history.back() : abrir("inicio"));
$("btnChat").onclick = () => abrir("chat");

function renderizarInicio() {
  const el = $("conteudo");
  el.innerHTML = `<div class="quadros">${QUADROS.map((q) =>
    `<button class="ladrilho" style="--cor:${q.cor}" data-quadro="${q.id}">${esc(q.titulo)}${
      q.id === "pagamentos" && temBoletoVencido ? `<span class="alerta" title="Boleto vencido">!</span>` : ""}</button>`).join("")}</div>`;
  el.onclick = (e) => {
    const b = e.target.closest("[data-quadro]");
    if (b) abrir(b.dataset.quadro);
  };
}

// ---------------------------------------------------------------- avisos, assembleias e prestação de contas
// Só leitura, mês a mês, do mais novo para o mais antigo
const LISTAS = {
  despesas: {
    colecao: "despesas", campoData: "dataHora", cor: "#2E7D32",
    titulo: (r) => r.nome, sub: (r) => r.dataHora, longo: (r) => r.descricao,
    detalhes: (r) => [["Valor", dinheiro(r.valor)]],
    total: (lista) => lista.reduce((n, r) => n + (paraNumero(r.valor) || 0), 0),
  },
  avisos: {
    colecao: "avisos", campoData: "datahora", cor: "#EF6C00",
    titulo: (r) => r.assunto, sub: (r) => r.datahora, longo: (r) => r.descricao,
  },
  assembleias: {
    colecao: "assembleias", campoData: "datahora", cor: "#8E24AA",
    titulo: (r) => r.assunto, sub: (r) => [r.datahora, r.local].filter(Boolean).join(" · "), longo: (r) => r.descricao,
  },
};
function mesDoRegistro(l, r) {
  if (r.mes) return r.mes;
  const ms = dataEmMs(r[l.campoData]);
  return chaveDoMes(ms ? new Date(ms) : new Date());
}
const mesesAbertos = new Set();
function renderizarPorMes(tela) {
  const l = LISTAS[tela];
  const el = $("conteudo");
  el.innerHTML = `<p class="vazio">Carregando…</p>`;
  ouvintesTela.push(onSnapshot(collection(db, l.colecao), (snap) => {
    if (telaAtual !== tela) return;
    const lista = snap.docs.map((d) => ({ ...d.data(), _id: d.id }))
      .sort((a, b) => dataEmMs(b[l.campoData]) - dataEmMs(a[l.campoData]));
    const meses = [...new Set(lista.map((r) => mesDoRegistro(l, r)))].sort().reverse();
    el.innerHTML = meses.length ? "" : `<p class="vazio">Nada por aqui ainda.</p>`;
    meses.forEach((mes, i) => {
      const chave = `${tela}|${mes}`;
      const doMes = lista.filter((r) => mesDoRegistro(l, r) === mes);
      const grupo = document.createElement("details");
      grupo.className = "mes-morador";
      // O mês mais recente já vem aberto
      grupo.open = mesesAbertos.has(chave) || (i === 0 && !mesesAbertos.has(chave + "|fechado"));
      grupo.ontoggle = () => {
        if (grupo.open) { mesesAbertos.add(chave); mesesAbertos.delete(chave + "|fechado"); }
        else { mesesAbertos.delete(chave); mesesAbertos.add(chave + "|fechado"); }
      };
      grupo.innerHTML = `<summary>${esc(textoDoMes(mes))} (${doMes.length})</summary>
        ${l.total ? `<div class="total">Total do mês: ${esc(dinheiro(l.total(doMes)))}</div>` : ""}<div class="itens"></div>`;
      for (const r of doMes) grupo.querySelector(".itens").appendChild(cartao(l, r));
      el.appendChild(grupo);
    });
  }, (e) => { el.innerHTML = `<p class="vazio">Erro ao carregar: ${esc(e.message)}</p>`; }));
}
function cartao(l, r) {
  const c = document.createElement("article");
  c.className = "cartao";
  c.style.setProperty("--cor", l.cor);
  const det = (l.detalhes?.(r) || []).filter(([, v]) => v);
  const anexos = [...listaDeTexto(r.anexos), ...listaDeTexto(r.documento)];
  c.innerHTML = `<h3>${esc(l.titulo(r) || "(sem título)")}</h3>
    <div class="sub">${esc(l.sub?.(r) || "")}</div>
    ${det.length ? `<dl>${det.map(([k, v]) => `<dt>${esc(k)}</dt><dd>${esc(v)}</dd>`).join("")}</dl>` : ""}
    ${l.longo?.(r) ? `<div class="texto-longo">${esc(l.longo(r))}</div>` : ""}
    <div class="grade-anexos">${htmlAnexos(anexos)}</div>`;
  ligarAnexos(c.querySelector(".grade-anexos"), anexos);
  return c;
}

// ---------------------------------------------------------------- leitura d'água (LeituraAguaActivity)
let fotoHidrometro = null;
function renderizarLeitura() {
  const el = $("conteudo");
  fotoHidrometro = null;
  el.innerHTML = `<article class="cartao form-leitura" style="--cor:#0284C7">
      <div class="sub">Digite o número que aparece no hidrômetro e tire uma foto dele.</div>
      <label>Leitura<input id="campoLeitura" inputmode="decimal" maxlength="12" autocomplete="off"></label>
      <img id="previaHidrometro" class="previa" alt="Foto do hidrômetro" hidden>
      <div class="sub" id="dicaFoto" hidden>Confira se os números aparecem na foto.</div>
      <label class="btn contorno" id="rotuloFotoHidrometro">Tirar foto do hidrômetro
        <input type="file" id="fotoHidrometro" accept="image/*" capture="environment" hidden></label>
      <button type="button" class="btn primario" id="btnEnviarLeitura">Enviar</button>
    </article>
    <h3>Suas leituras</h3>
    <div id="minhasLeituras" class="lista"><p class="vazio">Carregando…</p></div>`;
  $("fotoHidrometro").onchange = (e) => {
    const f = e.target.files[0];
    if (!f) return;
    fotoHidrometro = f;
    $("previaHidrometro").src = URL.createObjectURL(f);
    $("previaHidrometro").hidden = $("dicaFoto").hidden = false;
    $("rotuloFotoHidrometro").firstChild.textContent = "Tirar outra foto";
  };
  $("btnEnviarLeitura").onclick = enviarLeitura;
  ouvintesTela.push(onSnapshot(query(collection(db, "leituras_agua"), where("moradorUid", "==", uid)), (snap) => {
    const quando = (r) => r.criadoEm?.toMillis?.() || 0;
    const lista = snap.docs.map((d) => d.data({ serverTimestamps: "estimate" })).sort((a, b) => quando(b) - quando(a));
    // Leitura anterior de cada envio: o envio de antes (a lista vem do mais novo)
    lista.forEach((r, i) => { r._anterior = lista[i + 1]?.leitura; });
    const caixa = $("minhasLeituras");
    if (!caixa) return;
    caixa.innerHTML = lista.length ? "" : `<p class="vazio">Você ainda não enviou leituras.</p>`;
    for (const r of lista) caixa.appendChild(cartaoLeitura(r));
  }, (e) => { $("minhasLeituras").innerHTML = `<p class="vazio">Erro ao carregar: ${esc(e.message)}</p>`; }));
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
  c.style.setProperty("--cor", "#0284C7");
  const quando = r.criadoEm?.toDate?.().toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" }) || "";
  c.innerHTML = `<div class="sub">${esc(quando)}</div>
    <div class="leitura-anterior">Leitura anterior: ${esc(r._anterior ?? "—")}</div>
    <div class="leitura-linha">
      <div class="leitura-atual">Leitura atual: ${esc(r.leitura || "—")}</div>
      <div class="leitura-gasto"><small>Gastos do mês</small><strong>${esc(gastoDeAgua(r.leitura, r._anterior))}</strong></div>
    </div>
    ${ehLink(r.foto) ? `<img class="foto" alt="Foto do hidrômetro" src="${esc(r.foto)}">` : ""}`;
  c.querySelector(".foto")?.addEventListener("click", () => verImagem(r.foto));
  return c;
}
async function enviarLeitura() {
  const leitura = $("campoLeitura").value.trim();
  if (!leitura) { aviso("Digite a leitura."); $("campoLeitura").focus(); return; }
  if (!fotoHidrometro) { aviso("Tire a foto do hidrômetro antes de enviar."); return; }
  const b = $("btnEnviarLeitura");
  b.disabled = true;
  b.textContent = "Enviando…";
  try {
    // Quadra/lote vêm do cadastro do morador (não do que ele digita)
    const cadastro = (await getDoc(doc(db, "moradores", uid))).data() || {};
    const nome = `leitura_${Date.now()}.jpg`;
    const link = await enviarNaPasta("leituras", uid, nome, await reduzirFoto(fotoHidrometro, nome));
    await addDoc(collection(db, "leituras_agua"), {
      moradorUid: uid, nome: cadastro.nome ?? "", quadra: cadastro.quadra ?? "", lote: cadastro.lote ?? "",
      leitura, foto: link, criadoEm: serverTimestamp(),
    });
    alert("Leitura enviada!\nA administração já recebeu a sua leitura d'água.");
    renderizarLeitura();
  } catch (e) {
    b.disabled = false;
    b.textContent = "Enviar";
    aviso("Erro ao enviar a leitura: " + e.message);
  }
}

// ---------------------------------------------------------------- pagamentos (PagamentosActivity)
function renderizarPagamentos() {
  const el = $("conteudo");
  const hoje = new Date();
  const venc = dataBr(new Date(hoje.getFullYear(), hoje.getMonth(), DIA_VENCIMENTO_BOLETO));
  el.innerHTML = `<article class="cartao" style="--cor:#059669">
      <div class="texto-longo">O boleto vence sempre no dia 15 do mês vigente.\nVencimento deste mês: ${venc}</div>
      <div class="alerta-vencido" id="alertaVencido" hidden>Você tem boleto vencido. Procure a administração.</div>
      <h4>Boletos</h4>
      <div class="grade-anexos" id="listaBoletos"><span class="sub">Carregando…</span></div>
    </article>`;
  ouvintesTela.push(onSnapshot(query(collection(db, "boletos"), where("moradorUid", "==", uid)), (snap) => {
    const postadoDe = (b) => b.postadoEm?.toDate?.() || new Date();
    const lista = snap.docs.map((d) => d.data({ serverTimestamps: "estimate" })).sort((a, b) => postadoDe(b) - postadoDe(a));
    $("alertaVencido").hidden = !lista.some((b) => b.naoPago === true);
    const caixa = $("listaBoletos");
    caixa.innerHTML = lista.length ? "" : `<span class="sub">Nenhum boleto postado.</span>`;
    for (const b of lista) {
      const postado = postadoDe(b);
      // Vencimento: dia 15 do mês do boleto; boletos antigos, sem "mes", usam o mês da postagem
      const vencimento = dataBr(dataDoMes(b.mes || chaveDoMes(postado), DIA_VENCIMENTO_BOLETO));
      const linha = document.createElement("div");
      linha.className = "boleto" + (b.naoPago ? " nao-pago" : "");
      linha.innerHTML = `<div class="anexo">
          <span class="tipo" style="background:#DC2626">PDF</span>
          <span class="nome">${esc(b.nomeArquivo || "boleto.pdf")}<small>Postado em ${dataBr(postado)} às ${horaBr(postado)}</small></span>
          <span class="mini-acoes"><button type="button" class="btn pequeno" data-abrir>Abrir</button>
            <button type="button" class="btn pequeno" data-baixar>Baixar</button></span></div>
        <div class="status ${b.naoPago ? "vencido" : "em-dia"}">${b.naoPago
          ? `Boleto vencido (venceu em ${vencimento})` : `Vence em ${vencimento} (sempre dia 15 do mês do boleto)`}</div>`;
      linha.querySelector("[data-abrir]").onclick = () => window.open(b.arquivo, "_blank", "noopener");
      linha.querySelector("[data-baixar]").onclick = () => baixar(b.arquivo);
      caixa.appendChild(linha);
    }
  }, (e) => { $("listaBoletos").innerHTML = `<span class="sub">Erro ao carregar boletos: ${esc(e.message)}</span>`; }));
}

// ---------------------------------------------------------------- chat com a Administração (ChatRepository.java)
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
const horaChat = (ts) => {
  const d = ts?.toDate?.();
  return d ? `${d.toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit" })} ${horaBr(d)}` : "";
};

function renderizarChat() {
  const el = $("conteudo");
  el.innerHTML = `<div class="chat-morador">
      <header class="chat-topo"><img alt="" src="icone.png">
        <span class="chat-nome"><strong>Administração</strong><small>Condomínio Sociedade Morro Grande</small></span></header>
      <div class="chat-mensagens" id="chatMensagens"><p class="vazio">Carregando…</p></div>
      <form class="chat-envio" id="chatEnvio">
        <label class="btn contorno pequeno">Foto<input type="file" accept="image/*" id="chatFoto" hidden></label>
        <input type="text" id="chatTexto" placeholder="Digite uma mensagem…" autocomplete="off" enterkeyhint="send">
        <button class="btn primario" type="submit">Enviar</button>
      </form>
    </div>`;
  $("chatEnvio").onsubmit = (e) => {
    e.preventDefault();
    const texto = $("chatTexto").value.trim();
    if (!texto) return;
    $("chatTexto").value = "";
    gravarMensagem("texto", texto, null, texto).catch((err) => aviso("Não foi possível enviar: " + err.message));
  };
  $("chatFoto").onchange = (e) => { enviarFotoChat(e.target.files[0]); e.target.value = ""; };
  ouvintesTela.push(onSnapshot(query(collection(db, "conversas", uid, "mensagens"), orderBy("timestamp", "asc")),
    (s) => mostrarMensagens(s.docs),
    (e) => { $("chatMensagens").innerHTML = `<p class="vazio">Erro ao carregar: ${esc(e.message)}</p>`; }));
}

async function mostrarMensagens(docs) {
  const visiveis = docs.filter((d) => d.data().deletadoParaMorador !== true);
  const itens = await Promise.all(visiveis.map(async (d) => ({
    d, m: d.data({ serverTimestamps: "estimate" }), texto: await decifrar(d.data().conteudo),
  })));
  const el = $("chatMensagens");
  if (telaAtual !== "chat" || !el) return;
  el.innerHTML = "";
  if (!itens.length) el.innerHTML = `<p class="vazio">Mande uma mensagem para a administração.</p>`;
  for (const { d, m, texto } of itens) {
    const minha = m.deAdmin === false;
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
    const balao = b.querySelector(".balao");
    balao.onclick = (e) => {
      if (e.target.closest("button")) return;
      if (e.target.tagName === "IMG") return verImagem(m.imagemUrl);
      balao.classList.toggle("mostrar");
    };
    const erro = (e) => aviso("Não foi possível apagar: " + e.message);
    b.querySelector("[data-mim]").onclick = () => updateDoc(d.ref, { deletadoParaMorador: true }).catch(erro);
    b.querySelector("[data-todos]")?.addEventListener("click", async () => {
      updateDoc(d.ref, { deletadoParaTodos: true, conteudo: await cifrar(TEXTO_APAGADA), tipo: "texto", imagemUrl: deleteField() }).catch(erro);
    });
    el.appendChild(b);
  }
  el.scrollTop = el.scrollHeight;
  marcarComoLida(docs).catch(() => {});
}

// Conversa aberta: zera o contador do morador e marca como lidas as mensagens da Administração
async function marcarComoLida(docs) {
  const conv = doc(db, "conversas", uid);
  const atual = await getDoc(conv);
  if (atual.exists() && atual.data().naoLidasMorador > 0) await updateDoc(conv, { naoLidasMorador: 0 });
  const novas = docs.filter((d) => d.data().deAdmin === true && d.data().lida !== true);
  if (!novas.length) return;
  const lote = writeBatch(db);
  novas.forEach((d) => lote.update(d.ref, { lida: true }));
  await lote.commit();
}

async function gravarMensagem(tipo, texto, imagemUrl, resumo) {
  const conv = doc(db, "conversas", uid);
  const msg = doc(collection(conv, "mensagens"));
  const m = {
    deAdmin: false, remetenteId: uid, conteudo: await cifrar(texto), tipo,
    timestamp: serverTimestamp(), lida: false, deletadoParaTodos: false, deletadoParaMorador: false, deletadoParaAdmin: false,
  };
  if (imagemUrl) m.imagemUrl = imagemUrl;
  const lote = writeBatch(db);
  lote.set(msg, m);
  lote.set(conv, {
    moradorUid: uid, ultimaMensagem: await cifrar(resumo), ultimoTimestamp: serverTimestamp(),
    ultimoDeAdmin: false, naoLidasAdmin: increment(1),
  }, { merge: true });
  await lote.commit();
}

async function enviarFotoChat(foto) {
  if (!foto) return;
  aviso("Enviando foto…");
  try {
    const nome = `${Date.now()}_foto.jpg`;
    const link = await enviarNaPasta("chat", uid, nome, await reduzirFoto(foto, nome));
    await gravarMensagem("imagem", "", link, "📷 Foto");
  } catch (e) { aviso("Não foi possível enviar: " + e.message); }
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
