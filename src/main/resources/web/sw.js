const CACHE_NAME = 'expensebook-cache-v9';
const STATIC_ASSETS = [
  '/',
  'index.html',
  'style.css?v=9',
  'app.js?v=9',
  '/logo.png',
  '/brand-wordmark-dark.png',
  '/brand-wordmark-light.png',
  '/manifest.json'
];

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.map((key) => caches.delete(key))
      );
    })
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // Never cache API requests
  if (url.pathname.startsWith('/api/') || event.request.method !== 'GET') {
    return;
  }

  // Network-first for everything to ensure fresh code
  event.respondWith(
    fetch(event.request)
      .then((networkResponse) => {
        if (networkResponse && networkResponse.status === 200) {
          const clone = networkResponse.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
        }
        return networkResponse;
      })
      .catch(() => caches.match(event.request))
  );
});
