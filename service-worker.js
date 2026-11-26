/* Mock Test Studio PWA service worker. */
const CACHE_PREFIX = 'mock-test-studio-';
const CACHE_NAME = CACHE_PREFIX + 'v12';
const APP_SHELL = ['./','./index.html','./manifest.json','./icons/favicon-32.png','./icons/icon-192.png','./icons/icon-512.png','./icons/icon-maskable-512.png','./icons/apple-touch-icon.png'];
self.addEventListener('install', event => event.waitUntil((async () => { const cache = await caches.open(CACHE_NAME); await cache.addAll(APP_SHELL); await self.skipWaiting(); })()));
self.addEventListener('activate', event => event.waitUntil((async () => { const keys = await caches.keys(); await Promise.all(keys.filter(key => key.startsWith(CACHE_PREFIX) && key !== CACHE_NAME).map(key => caches.delete(key))); await self.clients.claim(); })()));
self.addEventListener('fetch', event => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method === 'POST' && url.origin === self.location.origin && url.pathname === new URL('./share-target', self.registration.scope).pathname) {
    event.respondWith((async () => {
      try {
        const form = await request.formData();
        const sharedFile = form.get('files');
        if (!sharedFile || typeof sharedFile.arrayBuffer !== 'function') throw new Error('No JSON file was attached.');
        const filename = String(sharedFile.name || 'Gemini-questions.json');
        if (!filename.toLowerCase().endsWith('.json')) throw new Error('Please share a .json file.');
        const cache = await caches.open(CACHE_NAME);
        const key = new Request(new URL('__share_target_file__', self.registration.scope).toString());
        const headers = new Headers({'Content-Type': sharedFile.type || 'application/json'});
        headers.set('x-share-filename', filename);
        await cache.put(key, new Response(await sharedFile.arrayBuffer(), {headers}));
        return Response.redirect(new URL('?shared=1', self.registration.scope).toString(), 303);
      } catch (error) {
        return new Response('Could not receive the shared file. Please use a .json file and try again.', {status:400,headers:{'Content-Type':'text/plain; charset=utf-8'}});
      }
    })());
    return;
  }
  if (request.method !== 'GET' || url.origin !== self.location.origin) return;
  if (request.mode === 'navigate') {
    event.respondWith((async () => {
      try {
        const response = await fetch(request, {cache:'no-store'});
        if (response && response.ok) { const cache = await caches.open(CACHE_NAME); await cache.put(request,response.clone()); }
        return response;
      } catch {
        return (await caches.match(request)) || (await caches.match('./index.html')) || (await caches.match('./')) || new Response('The app is not available offline yet.',{status:503});
      }
    })());
  }
});
