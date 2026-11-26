const API = "http://localhost:8080/api";
const content = document.getElementById("pageContent");
const accessToken = sessionStorage.getItem("noteAraAccessToken");
let userSession = null;
const titles = { dashboard: "Visão geral", notebooks: "Notebooks", loans: "Empréstimos", history: "Histórico", users: "Usuários", reports: "Relatórios", location: "Localização", appearance: "Aparência", account: "Minha conta" };

async function request(path, options = {}) {
  const response = await fetch(API + path, { ...options, headers: { "Content-Type": "application/json", ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}), ...(options.headers || {}) } });
  if (response.status === 204) return null;
  const data = await response.json().catch(() => ({}));
  if (response.status === 401) {
    sessionStorage.removeItem("noteAraAccessToken");
    sessionStorage.removeItem("noteAraUser");
    window.location.replace("login.html");
  }
  if (!response.ok) throw new Error(data.detail || data.message || data.mensagem || "Falha ao acessar a API.");
  return data;
}
const esc = value => String(value ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const field = (item, camel, snake) => item?.[camel] ?? item?.[snake] ?? "";
const date = value => value ? String(value).slice(0, 10).split("-").reverse().join("/") : "—";
function notify(message) { const el = document.getElementById("toast"); el.textContent = message; el.classList.add("show"); clearTimeout(notify.timer); notify.timer = setTimeout(() => el.classList.remove("show"), 2800); }
function fail(error) { notify(error.message || "Ocorreu um erro."); }
document.querySelectorAll(".nav-link").forEach(b => b.onclick = () => { document.querySelectorAll(".nav-link").forEach(x => x.classList.toggle("active", x === b)); render(b.dataset.page); document.getElementById("sidebar").classList.remove("open"); });
document.getElementById("logoutBtn").onclick = async () => { try { await request("/auth/logout", { method: "POST" }); } catch {} sessionStorage.removeItem("noteAraAccessToken"); sessionStorage.removeItem("noteAraUser"); window.location.replace("login.html"); };
document.getElementById("menuToggle").onclick = () => document.getElementById("sidebar").classList.add("open");
document.getElementById("sidebarClose").onclick = () => document.getElementById("sidebar").classList.remove("open");

function head(title, description, action = "") { return `<div class="page-header"><div><span class="eyebrow">NOTEARA · CENTRAL DE OPERAÇÕES</span><h1>${title}</h1><p>${description}</p></div><div class="page-header-actions">${action}</div></div>`; }
function table(title, columns, rows) { return `<section class="table-card"><h3>${title}</h3><div class="table-container"><table><thead><tr>${columns.map(x => `<th>${x}</th>`).join("")}</tr></thead><tbody>${rows || `<tr><td class="empty" colspan="${columns.length}">Nenhum registro encontrado.</td></tr>`}</tbody></table></div></section>`; }
function badge(status) { return `<span class="status-badge ${status === "EMPRESTADO" ? "badge-amber" : "status-available"}">${status === "EMPRESTADO" ? "Emprestado" : "Disponível"}</span>`; }
async function render(page) {
  document.getElementById("breadcrumb").textContent = titles[page] || titles.dashboard;
  try {
    if (page === "notebooks") return await renderNotebooks();
    if (page === "users") return await renderUsers();
    if (page === "loans" || page === "history") return await renderLoans(page);
    if (page === "location") return renderLocation();
    if (page === "appearance") return renderAppearance();
    if (page === "account") return renderAccount();
    return await renderDashboard(page);
  } catch (error) { content.innerHTML = `<section class="table-card"><div class="empty">${esc(error.message)}<br><br>Confira se o back-end e o MySQL estão em execução.</div></section>`; }
}
async function renderDashboard(page) {
  const [stats, loans, notebooks] = await Promise.all([request("/dashboard"), request("/loans"), request("/notebooks")]);
  const active = loans.filter(l => !l.returnedAt);
  content.innerHTML = `${head(page === "reports" ? "Relatórios" : "Visão geral", "Acompanhe os equipamentos e empréstimos da instituição.", page === "reports" ? "" : `<button class="btn-primary" data-go="loans">＋ Novo empréstimo</button>`)}
    <div class="stats-grid"><article class="stat-card"><small>Notebooks cadastrados</small><h2>${stats.notebooks}</h2><p>Equipamentos no inventário</p></article><article class="stat-card"><small>Disponíveis</small><h2>${stats.available}</h2><p>Prontos para empréstimo</p></article><article class="stat-card"><small>Em empréstimo</small><h2>${stats.onLoan}</h2><p>Equipamentos em uso</p></article><article class="stat-card"><small>Devoluções atrasadas</small><h2>${stats.overdue}</h2><p>Precisam de acompanhamento</p></article></div>
    ${table("Empréstimos em andamento", ["Patrimônio", "Equipamento", "Responsável", "Devolução prevista", "Situação"], active.slice(0, 8).map(l => `<tr><td><b>${esc(l.assetTag)}</b></td><td>${esc(l.brand)} ${esc(l.model)}</td><td>${esc(l.userName)}</td><td>${date(l.dueDate)}</td><td>${badge("EMPRESTADO")}</td></tr>`).join(""))}
    ${table("Equipamentos disponíveis", ["Patrimônio", "Equipamento", "Localização", "Situação"], notebooks.filter(n => n.status === "DISPONIVEL").slice(0, 6).map(n => `<tr><td><b>${esc(field(n,"assetTag","asset_tag"))}</b></td><td>${esc(n.brand)} ${esc(n.model)}</td><td>${esc(n.location) || "—"}</td><td>${badge(n.status)}</td></tr>`).join(""))}`;
  content.querySelector("[data-go]")?.addEventListener("click", () => render("loans"));
}
async function renderNotebooks() {
  const items = await request("/notebooks");
  content.innerHTML = `${head("Notebooks", "Cadastre e acompanhe os equipamentos da instituição.", `<button class="btn-primary" id="add">＋ Cadastrar notebook</button>`)}${table("Inventário", ["Patrimônio","Equipamento","Nº de série","Localização","Situação","Ações"], items.map(n => `<tr><td><b>${esc(field(n,"assetTag","asset_tag"))}</b></td><td>${esc(n.brand)} ${esc(n.model)}</td><td>${esc(field(n,"serialNumber","serial_number")) || "—"}</td><td>${esc(n.location) || "—"}</td><td>${badge(n.status)}</td><td><button class="btn-secondary btn-small" data-edit="${n.id}">Editar</button> <button class="btn-secondary btn-small danger" data-del="${n.id}">Excluir</button></td></tr>`).join(""))}`;
  document.getElementById("add").onclick = () => notebookForm();
  content.querySelectorAll("[data-edit]").forEach(b => b.onclick = () => notebookForm(items.find(n => String(n.id) === b.dataset.edit)));
  content.querySelectorAll("[data-del]").forEach(b => b.onclick = async () => { if (!confirm("Excluir este notebook?")) return; try { await request(`/notebooks/${b.dataset.del}`, { method: "DELETE" }); notify("Notebook excluído."); renderNotebooks(); } catch (e) { fail(e); } });
}
function input(label, name, value = "", required = false, type = "text") { const minimum = type === "password" ? 'minlength="10"' : ""; return `<div class="field"><label for="${name}">${label}</label><input id="${name}" name="${name}" type="${type}" value="${esc(value)}" ${required ? "required" : ""} ${minimum}></div>`; }
function modal(title, body) { document.getElementById("modalRoot").innerHTML = `<div class="modal-backdrop"><section class="modal"><header class="modal-header"><h2>${title}</h2><button class="modal-close" type="button" data-close>×</button></header><div class="modal-content">${body}</div></section></div>`; document.querySelectorAll("[data-close]").forEach(b => b.onclick = closeModal); document.querySelector(".modal-backdrop").onclick = e => { if (e.target.classList.contains("modal-backdrop")) closeModal(); }; }
function closeModal() { document.getElementById("modalRoot").innerHTML = ""; }
function notebookForm(n = null) {
  modal(n ? "Editar notebook" : "Cadastrar notebook", `<form id="form" class="modal-form">${input("Patrimônio","assetTag",field(n,"assetTag","asset_tag"),true)}${input("Marca","brand",n?.brand || "",true)}${input("Modelo","model",n?.model || "",true)}${input("Número de série","serialNumber",field(n,"serialNumber","serial_number"))}${input("Localização","location",n?.location || "")}<div class="field"><label>Observações</label><textarea name="notes">${esc(n?.notes)}</textarea></div><div class="form-actions"><button type="button" class="btn-secondary" data-close>Cancelar</button><button class="btn-primary">Salvar</button></div></form>`);
  document.getElementById("form").onsubmit = async e => { e.preventDefault(); try { await request(n ? `/notebooks/${n.id}` : "/notebooks", { method: n ? "PUT" : "POST", body: JSON.stringify(Object.fromEntries(new FormData(e.currentTarget))) }); closeModal(); notify("Notebook salvo."); renderNotebooks(); } catch (err) { fail(err); } };
}
async function renderUsers() {
  const users = await request("/users");
  content.innerHTML = `${head("Usuários", "Gerencie as pessoas que podem retirar equipamentos.", `<button class="btn-primary" id="add">＋ Novo usuário</button>`)}${table("Usuários ativos", ["Nome","E-mail","Perfil","Ações"], users.filter(u => u.active !== false).map(u => `<tr><td><b>${esc(u.name)}</b></td><td>${esc(u.email)}</td><td><span class="badge badge-blue">${esc(u.role)}</span></td><td><button class="btn-secondary btn-small" data-edit="${u.id}">Editar</button> <button class="btn-secondary btn-small danger" data-del="${u.id}">Desativar</button></td></tr>`).join(""))}`;
  document.getElementById("add").onclick = () => userForm();
  content.querySelectorAll("[data-edit]").forEach(b => b.onclick = () => userForm(users.find(u => String(u.id) === b.dataset.edit)));
  content.querySelectorAll("[data-del]").forEach(b => b.onclick = async () => { if (!confirm("Desativar este usuário?")) return; try { await request(`/users/${b.dataset.del}`, { method: "DELETE" }); notify("Usuário desativado."); renderUsers(); } catch (e) { fail(e); } });
}
function userForm(u = null) {
  modal(u ? "Editar usuário" : "Cadastrar usuário", `<form id="form" class="modal-form">${input("Nome completo","name",u?.name || "",true)}${input("E-mail","email",u?.email || "",true,"email")}${input(u ? "Nova senha (opcional)" : "Senha","password","",!u,"password")}<div class="field"><label>Perfil</label><select name="role"><option ${u?.role === "Administrador" ? "selected" : ""}>Administrador</option><option ${!u || u.role === "Colaborador" ? "selected" : ""}>Colaborador</option></select></div><div class="form-actions"><button type="button" class="btn-secondary" data-close>Cancelar</button><button class="btn-primary">Salvar</button></div></form>`);
  document.getElementById("form").onsubmit = async e => { e.preventDefault(); const body = Object.fromEntries(new FormData(e.currentTarget)); if (u && !body.password) delete body.password; try { await request(u ? `/users/${u.id}` : "/users", { method: u ? "PUT" : "POST", body: JSON.stringify(body) }); closeModal(); notify("Usuário salvo."); renderUsers(); } catch (err) { fail(err); } };
}
async function renderLoans(page) {
  const [loans, notebooks, users] = await Promise.all([request("/loans"), request("/notebooks"), request("/users")]);
  const history = page === "history"; const selected = loans.filter(l => history ? !!l.returnedAt : !l.returnedAt);
  content.innerHTML = `${head(history ? "Histórico de empréstimos" : "Empréstimos", history ? "Consulte as devoluções já concluídas." : "Acompanhe retiradas e registre devoluções.", history ? "" : `<button class="btn-primary" id="add">＋ Registrar empréstimo</button>`)}${table(history ? "Devoluções concluídas" : "Em andamento", ["Notebook","Responsável","Retirada","Devolução prevista","Situação","Ação"], selected.map(l => `<tr><td><b>${esc(l.assetTag)}</b><br>${esc(l.brand)} ${esc(l.model)}</td><td>${esc(l.userName)}<br>${esc(l.userEmail)}</td><td>${date(l.checkoutAt)}</td><td>${date(l.dueDate)}</td><td>${l.returnedAt ? date(l.returnedAt) : `<span class="badge badge-amber">Em andamento</span>`}</td><td>${history ? "—" : `<button class="btn-secondary btn-small" data-return="${l.id}">Registrar devolução</button>`}</td></tr>`).join(""))}`;
  if (!history) {
    document.getElementById("add").onclick = () => loanForm(notebooks.filter(n => n.status === "DISPONIVEL"), users.filter(u => u.active !== false));
    content.querySelectorAll("[data-return]").forEach(b => b.onclick = async () => { if (!confirm("Confirmar a devolução do notebook?")) return; try { await request(`/loans/${b.dataset.return}/return`, { method: "POST" }); notify("Devolução registrada."); renderLoans("loans"); } catch (e) { fail(e); } });
  }
}
function loanForm(notebooks, users) {
  if (!notebooks.length || !users.length) return notify("Cadastre notebook disponível e usuário antes do empréstimo.");
  const day = new Date(); day.setDate(day.getDate() + 7);
  modal("Registrar empréstimo", `<form id="form" class="modal-form"><div class="field"><label>Notebook</label><select name="notebookId">${notebooks.map(n => `<option value="${n.id}">${esc(field(n,"assetTag","asset_tag"))} · ${esc(n.brand)} ${esc(n.model)}</option>`).join("")}</select></div><div class="field"><label>Responsável</label><select name="userId">${users.map(u => `<option value="${u.id}">${esc(u.name)} · ${esc(u.email)}</option>`).join("")}</select></div>${input("Data prevista para devolução","dueDate",day.toISOString().slice(0,10),true,"date")}<div class="field"><label>Observações</label><textarea name="notes"></textarea></div><div class="form-actions"><button type="button" class="btn-secondary" data-close>Cancelar</button><button class="btn-primary">Confirmar empréstimo</button></div></form>`);
  document.getElementById("form").onsubmit = async e => { e.preventDefault(); try { await request("/loans", { method: "POST", body: JSON.stringify(Object.fromEntries(new FormData(e.currentTarget))) }); closeModal(); notify("Empréstimo registrado."); renderLoans("loans"); } catch (err) { fail(err); } };
}

function renderLocation() { const unit = localStorage.getItem("noteAraUnit") || "Unidade SESI SENAI"; content.innerHTML = `${head("Localização","Defina o nome da unidade exibido no navegador.")}<section class="form-card"><form id="setting">${input("Nome da unidade","unit",unit,true)}<div class="form-actions"><button class="btn-primary">Salvar</button></div></form></section>`; document.getElementById("setting").onsubmit = e => { e.preventDefault(); localStorage.setItem("noteAraUnit", document.getElementById("unit").value); notify("Unidade salva neste navegador."); }; }
function renderAppearance() { content.innerHTML = `${head("Aparência","Ajuste o tamanho da interface neste navegador.")}<section class="form-card"><label for="size">Tamanho da interface</label><input id="size" type="range" min="80" max="125" step="5" value="${localStorage.getItem("noteAraZoom") || 100}"></section>`; document.getElementById("size").oninput = e => { document.body.style.zoom = `${e.target.value}%`; localStorage.setItem("noteAraZoom", e.target.value); }; }
function renderAccount() { content.innerHTML = `${head("Minha conta","Informações do usuário conectado.")}<section class="form-card"><p><b>Nome:</b> ${esc(userSession.name)}</p><p><b>E-mail:</b> ${esc(userSession.email)}</p><p><b>Perfil:</b> ${esc(userSession.role)}</p></section>`; }

document.getElementById("notificationBell").onclick = async () => {
  const panel = document.getElementById("notificationPanel"); panel.style.display = panel.style.display === "block" ? "none" : "block";
  if (panel.style.display !== "block") return;
  try { const loans = await request("/loans"); const overdue = loans.filter(l => !l.returnedAt && String(l.dueDate).slice(0,10) < new Date().toISOString().slice(0,10)); document.getElementById("notificationCount").textContent = overdue.length; panel.innerHTML = overdue.length ? overdue.map(l => `<div class="notification-item"><b>${esc(l.assetTag)}</b> · ${esc(l.userName)}<br>Prevista: ${date(l.dueDate)}</div>`).join("") : `<div class="empty-notifications">Nenhum aviso pendente.</div>`; } catch (e) { panel.innerHTML = `<div class="empty-notifications">${esc(e.message)}</div>`; }
};
let zoom = Number(localStorage.getItem("noteAraZoom") || 100);
document.body.style.zoom = `${zoom}%`; document.getElementById("zoomValue").textContent = `${zoom}%`;
document.getElementById("increaseZoom").onclick = () => setZoom(Math.min(125, zoom + 5));
document.getElementById("decreaseZoom").onclick = () => setZoom(Math.max(80, zoom - 5));
document.getElementById("resetZoom").onclick = () => setZoom(100);
function setZoom(value) { zoom = value; document.body.style.zoom = `${zoom}%`; document.getElementById("zoomValue").textContent = `${zoom}%`; localStorage.setItem("noteAraZoom", zoom); }
window.apiBackend = { verificarConexaoBackend: () => request("/health") };
async function updateSystemStatus() {
  const status = document.querySelector(".system-status");
  try {
    const result = await request("/health");
    status.textContent = result.banco === "conectado" ? "● API e MySQL conectados" : "● MySQL indisponível";
    status.style.color = result.banco === "conectado" ? "#16a878" : "#d9435d";
  } catch {
    status.textContent = "● API ou MySQL indisponível";
    status.style.color = "#d9435d";
  }
}
updateSystemStatus();
setInterval(updateSystemStatus, 30000);

async function initializeAdmin() {
  if (!accessToken) {
    window.location.replace("login.html");
    return;
  }
  try {
    userSession = await request("/auth/me");
    if (userSession.role !== "Administrador") {
      try { await request("/auth/logout", { method: "POST" }); } catch {}
      sessionStorage.removeItem("noteAraAccessToken");
      window.location.replace("login.html");
      return;
    }
    document.getElementById("app").classList.remove("hidden");
    document.getElementById("profileName").textContent = userSession.name;
    document.getElementById("profileRole").textContent = userSession.role;
    document.getElementById("profileAvatar").textContent = userSession.name.charAt(0).toUpperCase();
    render("dashboard");
  } catch {
    sessionStorage.removeItem("noteAraAccessToken");
    window.location.replace("login.html");
  }
}

initializeAdmin();
