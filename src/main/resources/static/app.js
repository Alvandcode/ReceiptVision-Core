const $ = (id) => document.getElementById(id);
const authCard = $('authCard'), appMain = $('appMain');
const usernameEl = $('username'), passwordEl = $('password');
const authMsg = $('authMsg'), whoami = $('whoami'), logoutBtn = $('logoutBtn');
const drop = $('drop'), fileInput = $('file'), preview = $('preview');
const uploadBtn = $('uploadBtn'), clearBtn = $('clearBtn');
const uploadMsg = $('uploadMsg'), ocrOut = $('ocrOut');
const listEl = $('list'), listMsg = $('listMsg');
const dialog = $('detailDialog'), dTitle = $('dTitle'), dBody = $('dBody');
let selectedFile = null;
let currentDetailId = null;
let page = 0;
const size = 10;
let lastPage = false;

const TOKEN_KEY = 'rv_token';
const USER_KEY = 'rv_user';
const getToken = () => localStorage.getItem(TOKEN_KEY) || '';
const isLoggedIn = () => !!getToken();

function setMsg(el, text, kind) {
  el.textContent = text || '';
  el.className = 'msg' + (kind ? ' ' + kind : '');
}

function authHeaders(extra) {
  return Object.assign({ 'Authorization': 'Bearer ' + getToken() }, extra || {});
}

function showAuth(show) {
  authCard.hidden = !show;
  appMain.hidden = show;
  logoutBtn.hidden = show;
  whoami.hidden = show;
  if (!show) {
    whoami.textContent = '👤 ' + (localStorage.getItem(USER_KEY) || '');
  }
}

async function api(path, opts) {
  const res = await fetch(path, opts);
  if (res.status === 401) {
    logout();
    throw new Error('نشست منقضی شد، دوباره وارد شوید (401)');
  }
  return res;
}

async function doAuth(mode) {
  const username = usernameEl.value.trim();
  const password = passwordEl.value;
  if (username.length < 3 || password.length < 8) {
    setMsg(authMsg, 'نام کاربری ≥۳ و رمز ≥۸ حرف لازم است', 'err');
    return;
  }
  setMsg(authMsg, 'در حال ارسال…', '');
  try {
    const res = await fetch('/api/auth/' + mode, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.message || ('خطای ' + res.status));
    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(USER_KEY, data.username);
    passwordEl.value = '';
    setMsg(authMsg, '', '');
    enterApp();
  } catch (err) {
    setMsg(authMsg, err.message, 'err');
  }
}

function logout() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  listEl.innerHTML = '';
  page = 0; lastPage = false;
  showAuth(true);
}

function enterApp() {
  showAuth(false);
  page = 0; lastPage = false; listEl.innerHTML = '';
  loadList();
}

$('loginBtn').addEventListener('click', () => doAuth('login'));
$('registerBtn').addEventListener('click', () => doAuth('register'));
logoutBtn.addEventListener('click', logout);

function setFile(f) {
  selectedFile = f;
  const ok = !!f;
  uploadBtn.disabled = !ok;
  clearBtn.disabled = !ok;
  if (!f) {
    preview.hidden = true;
    preview.src = '';
    return;
  }
  if (!f.type.startsWith('image/')) {
    setMsg(uploadMsg, 'فقط فایل تصویری مجاز است', 'err');
    selectedFile = null;
    uploadBtn.disabled = true;
    return;
  }
  if (f.size > 10 * 1024 * 1024) {
    setMsg(uploadMsg, 'حجم فایل بیشتر از ۱۰ مگ است', 'err');
    selectedFile = null;
    uploadBtn.disabled = true;
    return;
  }
  setMsg(uploadMsg, f.name + ' (' + Math.round(f.size / 1024) + ' KB)', 'ok');
  const url = URL.createObjectURL(f);
  preview.src = url;
  preview.hidden = false;
}

drop.addEventListener('click', () => fileInput.click());
drop.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') fileInput.click(); });
fileInput.addEventListener('change', () => setFile(fileInput.files[0] || null));
['dragover', 'dragenter'].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.add('over'); }));
['dragleave', 'drop'].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.remove('over'); }));
drop.addEventListener('drop', (e) => setFile(e.dataTransfer.files[0] || null));
clearBtn.addEventListener('click', () => { fileInput.value = ''; setFile(null); setMsg(uploadMsg, ''); ocrOut.hidden = true; });

uploadBtn.addEventListener('click', async () => {
  if (!selectedFile) return;
  uploadBtn.disabled = true;
  setMsg(uploadMsg, 'در حال استخراج متن…', '');
  ocrOut.hidden = true;
  try {
    const fd = new FormData();
    fd.append('file', selectedFile);
    const res = await api('/api/receipts', { method: 'POST', headers: authHeaders(), body: fd });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.message || ('خطای ' + res.status));
    setMsg(uploadMsg, 'فقط در حساب شما ذخیره شد (id=' + data.id + ')', 'ok');
    ocrOut.textContent = data.ocrText || '(متنی استخراج نشد)';
    ocrOut.hidden = false;
    page = 0; lastPage = false; listEl.innerHTML = '';
    await loadList();
  } catch (err) {
    setMsg(uploadMsg, err.message, 'err');
  } finally {
    uploadBtn.disabled = !selectedFile;
  }
});

async function loadList() {
  if (lastPage || !isLoggedIn()) return;
  setMsg(listMsg, 'در حال بارگذاری رسیدهای شما…', '');
  try {
    const res = await api('/api/receipts?page=' + page + '&size=' + size + '&sort=id,desc', { headers: authHeaders() });
    if (!res.ok) throw new Error('خطای ' + res.status);
    const data = await res.json();
    const items = data.content || [];
    if (items.length === 0 && page === 0) setMsg(listMsg, 'هنوز رسیدی ندارید', '');
    else setMsg(listMsg, 'تعداد رسیدهای شما: ' + (data.totalElements ?? items.length), 'ok');
    for (const r of items) {
      const li = document.createElement('li');
      const left = document.createElement('div');
      left.innerHTML = '<div><b>#' + r.id + '</b> ' + escapeHtml(r.fileName || '') + '</div>'
        + '<div class="meta">' + escapeHtml(r.createdAt || '') + ' • ' + (r.size || 0) + ' byte</div>'
        + '<div class="meta">' + escapeHtml((r.ocrText || '').slice(0, 80)) + '</div>';
      const actions = document.createElement('div');
      actions.className = 'actions';
      const view = document.createElement('button');
      view.textContent = 'نمایش';
      view.onclick = () => openDetail(r);
      const del = document.createElement('button');
      del.textContent = 'حذف';
      del.onclick = () => deleteReceipt(r.id);
      actions.append(view, del);
      li.append(left, actions);
      listEl.appendChild(li);
    }
    lastPage = data.last ?? items.length < size;
    page += 1;
  } catch (err) {
    setMsg(listMsg, err.message, 'err');
  }
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function openDetail(r) {
  currentDetailId = r.id;
  dTitle.textContent = 'رسید #' + r.id + ' — ' + (r.fileName || '');
  dBody.textContent = r.ocrText || '(خالی)';
  dialog.showModal();
}

async function deleteReceipt(id) {
  if (!confirm('رسید #' + id + ' فقط از حساب شما حذف شود؟')) return;
  const res = await api('/api/receipts/' + id, { method: 'DELETE', headers: authHeaders() });
  if (res.ok || res.status === 204) {
    page = 0; lastPage = false; listEl.innerHTML = '';
    await loadList();
  } else {
    alert('حذف ناموفق: ' + res.status);
  }
}

$('refreshBtn').addEventListener('click', () => { page = 0; lastPage = false; listEl.innerHTML = ''; loadList(); });
$('moreBtn').addEventListener('click', loadList);
$('dClose').addEventListener('click', () => dialog.close());
$('dDelete').addEventListener('click', async () => {
  if (currentDetailId == null) return;
  await deleteReceipt(currentDetailId);
  dialog.close();
});

async function checkHealth() {
  const el = $('health');
  try {
    const res = await fetch('/actuator/health');
    const ok = res.ok;
    el.textContent = ok ? '● سرویس سالم' : '● اختلال';
    el.className = 'health ' + (ok ? 'ok' : 'down');
  } catch {
    el.textContent = '● قطع';
    el.className = 'health down';
  }
}

// Init: force login gate — nothing private is fetched before JWT exists.
if (isLoggedIn()) enterApp();
else showAuth(true);
checkHealth();
setInterval(checkHealth, 30000);
