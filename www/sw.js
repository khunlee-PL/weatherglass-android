/* Weatherglass service worker — offline install for the single-file app.
 *
 * The app HTML already bakes its atlas inline, so this SW's real job is to make the
 * install reliable and guarantee the shell is served with the wire pulled. It caches
 * only the local shell + manifest + icons; it NEVER caches or intercepts the live
 * weather/warning endpoints (those stay user-initiated, per Constitution Art. I §1).
 * Bump CACHE on every release so an updated shell replaces the old one.
 */
const CACHE = "weatherglass-shell-v1";
const SHELL = [
  "./index.html",
  "./manifest.webmanifest",
  "./icon/icon-192.png",
  "./icon/icon-512.png"
];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  // Only serve the local app shell from cache. Anything cross-origin (a live weather
  // or warning fetch the reader initiated) is left to the network, untouched.
  if (e.request.method !== "GET" || url.origin !== self.location.origin) return;
  e.respondWith(
    caches.match(e.request).then((hit) => hit || fetch(e.request))
  );
});
