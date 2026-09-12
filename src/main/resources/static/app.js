const $ = (id) => document.getElementById(id);
const authCard = $('authCard'), appMain = $('appMain');
const usernameEl = $('username'), passwordEl = $('password');
const authMsg = $('authMsg'), whoami = $('whoami'), logoutBtn = $('logoutBtn');
const drop = $('drop'), fileInput = $('file'), cameraInput = $('camera'), preview = $('preview');
const uploadBtn = $('uploadBtn'), clearBtn = $('clearBtn'), cameraBtn = $('cameraBtn');
const installBtn = $('installBtn');
const uploadMsg = $('uploadMsg'), ocrOut = $('ocrOut');
const listEl = $('list'), listMsg = $('listMsg');
const dialog = $('detailDialog'), dTitle = $('dTitle'), dBody = $('dBody');
let selectedFile = null;
let selectedFileUrl = null;
let currentDetailId = null;
let page = 0;
const size = 10;
let lastPage = false;
let authBusy = false;

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
    await logout(true);
    throw new Error('نشست منقضی شد، دوباره وارد شوید (401)');
  }
  if (res.status === 429) {
    throw new Error('درخواست زیاد است، یک دقیقه صبر کنید (429)');
  }
  return res;
}

function validPassword(pw) {
  return pw.length >= 8 && pw.length <= 100 && /[A-Za-z]/.test(pw) && /\d/.test(pw);
}

async function doAuth(mode) {
  if (authBusy) return;
  const username = usernameEl.value.trim().toLowerCase();
  const password = passwordEl.value;
  if (username.length < 3 || !validPassword(password)) {
    setMsg(authMsg, 'نام کاربری ≥۳ (انگلیسی) و رمز ≥۸ حرف شامل حرف و عدد لازم است', 'err');
    return;
  }
  authBusy = true;
  $('loginBtn').disabled = true;
  $('registerBtn').disabled = true;
  setMsg(authMsg, 'در حال ارسال…', '');
  try {
    const res = await fetch('/api/auth/' + mode, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
      if (res.status === 409) throw new Error('این نام کاربری قبلا گرفته شده (409)');
      throw new Error(data.message || ('خطای ' + res.status));
    }
    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(USER_KEY, data.username);
    passwordEl.value = '';
    setMsg(authMsg, '', '');
    enterApp();
  } catch (err) {
    setMsg(authMsg, err.message, 'err');
  } finally {
    authBusy = false;
    $('loginBtn').disabled = false;
    $('registerBtn').disabled = false;
  }
}

async function logout(silent) {
  const token = getToken();
  if (token) {
    try {
      await fetch('/api/auth/logout', {
        method: 'POST',
        headers: { 'Authorization': 'Bearer ' + token },
      }).catch(() => {});
    } catch { /* best-effort server revoke */ }
  }
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
  // Clear private list + revoke preview URL + clear offline caches of shell
  // (receipt data itself is never cached, see sw.js).
  if (selectedFileUrl) {
    try { URL.revokeObjectURL(selectedFileUrl); } catch { /* ignore */ }
    selectedFileUrl = null;
  }
  selectedFile = null;
  if ('caches' in window) {
    try {
      const keys = await caches.keys();
      await Promise.all(keys.filter((k) => k.startsWith('rv-shell-')).map((k) => caches.delete(k)));
    } catch { /* ignore */ }
  }
  listEl.innerHTML = '';
  page = 0; lastPage = false;
  if (!silent) showAuth(true);
  else showAuth(true);
}

function enterApp() {
  showAuth(false);
  page = 0; lastPage = false; listEl.innerHTML = '';
  loadList();
}

$('loginBtn').addEventListener('click', () => doAuth('login'));
$('registerBtn').addEventListener('click', () => doAuth('register'));
logoutBtn.addEventListener('click', () => logout(false));

function setFile(f) {
  if (selectedFileUrl) {
    try { URL.revokeObjectURL(selectedFileUrl); } catch { /* ignore */ }
    selectedFileUrl = null;
  }
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
  selectedFileUrl = url;
  preview.src = url;
  preview.hidden = false;
}

drop.addEventListener('click', () => fileInput.click());
drop.addEventListener('keydown', (e) => { if (e.key === 'Enter' || e.key === ' ') fileInput.click(); });
fileInput.addEventListener('change', () => setFile(fileInput.files[0] || null));
cameraInput.addEventListener('change', () => setFile(cameraInput.files[0] || null));
if (cameraBtn) cameraBtn.addEventListener('click', (e) => { e.stopPropagation(); cameraInput.click(); });
['dragover', 'dragenter'].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.add('over'); }));
['dragleave', 'drop'].forEach((ev) => drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.remove('over'); }));
drop.addEventListener('drop', (e) => setFile(e.dataTransfer.files[0] || null));
clearBtn.addEventListener('click', () => { fileInput.value = ''; cameraInput.value = ''; setFile(null); setMsg(uploadMsg, ''); ocrOut.hidden = true; });

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

// PWA install prompt (Android/Chrome). iPhone: Share -> Add to Home Screen.
let deferredPrompt = null;
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  deferredPrompt = e;
  if (installBtn) installBtn.hidden = false;
});
if (installBtn) installBtn.addEventListener('click', async () => {
  if (!deferredPrompt) return;
  deferredPrompt.prompt();
  await deferredPrompt.userChoice.catch(() => {});
  deferredPrompt = null;
  installBtn.hidden = true;
});
