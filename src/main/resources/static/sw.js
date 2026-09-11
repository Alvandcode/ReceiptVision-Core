/* ReceiptVision service worker: offline shell for the UI only.
 * API calls (/api/*, /actuator/*) are NEVER cached: receipts are private
 * and must always come fresh from the server with the user's JWT. */
const CACHE = 'rv-shell-v1';
const SHELL = [
  '/',
  '/index.html',
  '/styles.css',
  '/app.js',
  '/manifest.webmanifest',
  '/icon-192.png',
  '/icon-512.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  // Private or dynamic: always network, never cache.
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/actuator/')) {
    return;
  }
  if (event.request.method !== 'GET') {
    return;
  }
  event.respondWith(
    caches.match(event.request).then(
      (hit) => hit || fetch(event.request).then((res) => {
        const copy = res.clone();
        caches.open(CACHE).then((c) => c.put(event.request, copy));
        return res;
      }).catch(() => caches.match('/index.html'))
    )
  );
});
