// YlowstoneTV v2.5.4 — Serverless Translation Proxy (ID & EN, ES Module)
// Endpoint: POST/GET https://ylowstonetv.vercel.app/api/translate

const memoryCache = new Map();
const MAX_CACHE = 400;

function setCache(key, value) {
  if (memoryCache.size >= MAX_CACHE) {
    const firstKey = memoryCache.keys().next().value;
    memoryCache.delete(firstKey);
  }
  memoryCache.set(key, value);
}

async function translatePrimary(text, target) {
  const url =
    "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" +
    encodeURIComponent(target) +
    "&dt=t&q=" +
    encodeURIComponent(text);
  const resp = await fetch(url, {
    headers: { "User-Agent": "YlowstoneTV/2.5.4" },
  });
  if (!resp.ok) throw new Error("primary_http_" + resp.status);
  const data = await resp.json();
  if (!Array.isArray(data) || !Array.isArray(data[0])) {
    throw new Error("primary_bad_payload");
  }
  const translated = data[0]
    .map((part) => (Array.isArray(part) && typeof part[0] === "string" ? part[0] : ""))
    .join("")
    .trim();
  if (!translated) throw new Error("primary_empty");
  return translated;
}

async function translateFallback(text, target) {
  const source = target === "id" ? "en" : "id";
  const url =
    "https://api.mymemory.translated.net/get?q=" +
    encodeURIComponent(text.slice(0, 480)) +
    "&langpair=" +
    encodeURIComponent(source + "|" + target);
  const resp = await fetch(url, {
    headers: { "User-Agent": "YlowstoneTV/2.5.4" },
  });
  if (!resp.ok) throw new Error("fallback_http_" + resp.status);
  const data = await resp.json();
  const translated =
    data && data.responseData && typeof data.responseData.translatedText === "string"
      ? data.responseData.translatedText.trim()
      : "";
  if (!translated) throw new Error("fallback_empty");
  return translated;
}

export default async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    return res.status(204).end();
  }

  try {
    const body =
      req.method === "POST"
        ? typeof req.body === "string"
          ? JSON.parse(req.body || "{}")
          : req.body || {}
        : req.query || {};

    const rawText = typeof body.text === "string" ? body.text.trim() : "";
    const rawTarget = typeof body.target === "string" ? body.target.trim().toLowerCase() : "id";
    const target = rawTarget === "en" ? "en" : "id";

    if (!rawText) {
      return res.status(200).json({ translated: "", target });
    }

    const clipped = rawText.slice(0, 1200);
    const cacheKey = target + "::" + clipped;
    if (memoryCache.has(cacheKey)) {
      res.setHeader("Cache-Control", "public, s-maxage=86400, stale-while-revalidate=604800");
      return res.status(200).json({ translated: memoryCache.get(cacheKey), target, cached: true });
    }

    let translated = clipped;
    try {
      translated = await translatePrimary(clipped, target);
    } catch (_) {
      try {
        translated = await translateFallback(clipped, target);
      } catch (__) {
        translated = clipped;
      }
    }

    setCache(cacheKey, translated);
    res.setHeader("Cache-Control", "public, s-maxage=86400, stale-while-revalidate=604800");
    return res.status(200).json({ translated, target, cached: false });
  } catch (err) {
    return res.status(200).json({ translated: "", error: "translate_failed" });
  }
}
