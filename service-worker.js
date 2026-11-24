/* Mock Test Studio PWA service worker. */
const CACHE_PREFIX = 'mock-test-studio-';
const CACHE_NAME = CACHE_PREFIX + 'v5';
const APP_SHELL = ['./','./index.html','./publisher.html','./manifest.json','./icons/favicon-32.png','./icons/icon-192.png','./icons/icon-512.png','./icons/icon-maskable-512.png','./icons/apple-touch-icon.png'];
self.addEventListener('install', event => event.waitUntil((async () => { const cache = await caches.open(CACHE_NAME); await cache.addAll(APP_SHELL); await self.skipWaiting(); })()));
self.addEventListener('activate', event => event.waitUntil((async () => { const keys = await caches.keys(); await Promise.all(keys.filter(key => key.startsWith(CACHE_PREFIX) && key !== CACHE_NAME).map(key => caches.delete(key))); await self.clients.claim(); })()));
self.addEventListener('fetch', event => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  if (request.mode === 'navigate') {
    event.respondWith((async () => {
      try { const response = await fetch(request, {cache:'no-store'}); if (response && response.ok) { const cache = await caches.open(CACHE_NAME); await cache.put(request,response.clone()); } return response; }
      catch { return (await caches.match(request)) || (await caches.match('./index.html')) || (await caches.match('./')) || new Response('The app is not available offline yet.',{status:503}); }
    })());
  }
});