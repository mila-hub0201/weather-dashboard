"use strict";

// キャッシュを更新したいときはこのバージョンを上げる
const CACHE_NAME = "weather-dashboard-v2";

const APP_SHELL = [
  "./",
  "./index.html",
  "./manifest.webmanifest",
  "./icons/icon-512.png"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      // アイコン等が未配置でもインストール自体は成功させる
      .then((cache) => Promise.allSettled(APP_SHELL.map((url) => cache.add(url))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  const url = new URL(request.url);

  // 外部API (Open-Meteo・気象庁・国土地理院・Weathernews) はキャッシュせず素通しする。
  // 常に最新の観測・予報を使い、各カード側のエラー表示に任せる。
  if (request.method !== "GET" || url.origin !== self.location.origin) return;

  // 自サイトの資産はネットワーク優先 (更新をすぐ反映)、オフライン時はキャッシュで表示
  event.respondWith(
    fetch(request)
      .then((response) => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
        }
        return response;
      })
      .catch(() =>
        caches.match(request, { ignoreSearch: true }).then((hit) => {
          if (hit) return hit;
          if (request.mode === "navigate") return caches.match("./index.html");
          return Response.error();
        })
      )
  );
});
