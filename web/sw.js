self.addEventListener('install', (e) => {
  self.skipWaiting();
});

self.addEventListener('activate', (e) => {
  return self.clients.claim();
});

self.addEventListener('fetch', (e) => {
  // APK fayllarını Service Worker-dən kənar tut (Yükləmə xətasını düzəltmək üçün)
  if (e.request.url.endsWith('.apk')) {
    return;
  }
  e.respondWith(fetch(e.request));
});