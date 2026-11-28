import { gzipSync } from "node:zlib";

// YlowstoneTV — High-Speed Cached M3U Playlist Proxy (sin1 + Pre-Compressed Warm Cache + HEAD/Range)
const CACHE_TTL_MS = 15 * 60 * 1000; // 15 menit auto-revalidate via GitHub ETag
const warmCache = new Map();

async function loadPlaylist(repo, branch, token, file, forceRefresh) {
  const cacheKey = `${repo}:${branch}:${file}`;
  const now = Date.now();
  const cached = warmCache.get(cacheKey);

  if (cached && !forceRefresh && now - cached.fetchedAt < CACHE_TTL_MS) {
    return { entry: cached, hit: "MEMORY" };
  }

  const apiUrl = `https://api.github.com/repos/${repo}/contents/${encodeURIComponent(file)}?ref=${branch}`;
  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: "application/vnd.github.raw+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "YlowstoneTV-Playlist-Proxy",
  };
  if (cached?.etag && !forceRefresh) {
    headers["If-None-Match"] = cached.etag;
  }

  const ghRes = await fetch(apiUrl, { headers });

  if (ghRes.status === 304 && cached) {
    cached.fetchedAt = now;
    return { entry: cached, hit: "REVALIDATED" };
  }

  if (!ghRes.ok) {
    return { errorStatus: ghRes.status };
  }

  const rawBuf = Buffer.from(await ghRes.arrayBuffer());
  const gzipBuf = gzipSync(rawBuf, { level: 6 });
  const etag = ghRes.headers.get("etag") || `W/"${rawBuf.length}-${now}"`;

  const entry = { rawBuf, gzipBuf, etag, fetchedAt: now };
  warmCache.set(cacheKey, entry);
  return { entry, hit: "MISS" };
}

export default async function handler(req, res) {
  const { file, key, refresh } = req.query;

  if (!process.env.PLAYLIST_SECRET || key !== process.env.PLAYLIST_SECRET.trim()) {
    return res.status(403).send("Forbidden");
  }

  if (!file || !/^[A-Za-z0-9._-]+\.(m3u|m3u8)$/i.test(file)) {
    return res.status(400).send("Invalid file name");
  }

  const repo = (process.env.GH_PLAYLIST_REPO || "").trim();
  const token = (process.env.GH_PLAYLIST_TOKEN || "").trim();
  const branch = (process.env.GH_PLAYLIST_BRANCH || "main").trim();
  const forceRefresh = refresh === "1" || refresh === "true";

  try {
    const result = await loadPlaylist(repo, branch, token, file, forceRefresh);
    if (result.errorStatus) {
      return res
        .status(result.errorStatus)
        .send(`GitHub error ${result.errorStatus} (repo: ${repo}, file: ${file})`);
    }

    const { rawBuf, gzipBuf, etag } = result.entry;

    res.setHeader("Content-Type", "audio/x-mpegurl; charset=utf-8");
    res.setHeader("ETag", etag);
    res.setHeader("Accept-Ranges", "bytes");
    res.setHeader("Vary", "Accept-Encoding");
    res.setHeader("X-Playlist-Cache", result.hit);
    res.setHeader(
      "Cache-Control",
      forceRefresh
        ? "no-store"
        : "public, s-maxage=900, stale-while-revalidate=86400"
    );

    if (req.headers["if-none-match"] && req.headers["if-none-match"] === etag) {
      return res.status(304).end();
    }

    // Respons instan untuk HEAD (Health Inspector) tanpa kirim body belasan MB
    if (req.method === "HEAD") {
      res.setHeader("Content-Length", String(rawBuf.length));
      return res.status(200).end();
    }

    // Dukungan Range request sejati (mis. bytes=0-8192 saat cek status cepat)
    const rangeHeader = String(req.headers.range || "");
    const rangeMatch = /^bytes=(\d*)-(\d*)$/i.exec(rangeHeader);
    if (rangeMatch && rawBuf.length > 0) {
      const start = rangeMatch[1] ? parseInt(rangeMatch[1], 10) : 0;
      const end = rangeMatch[2]
        ? Math.min(parseInt(rangeMatch[2], 10), rawBuf.length - 1)
        : rawBuf.length - 1;
      if (start <= end && start < rawBuf.length) {
        const chunk = rawBuf.subarray(start, end + 1);
        res.setHeader("Content-Range", `bytes ${start}-${end}/${rawBuf.length}`);
        res.setHeader("Content-Length", String(chunk.length));
        return res.status(206).send(chunk);
      }
    }

    // Kirim pre-compressed Gzip buffer langsung dari memori (tanpa gzipSync ulang!)
    const acceptEnc = String(req.headers["accept-encoding"] || "");
    if (acceptEnc.includes("gzip")) {
      res.setHeader("Content-Encoding", "gzip");
      res.setHeader("Content-Length", String(gzipBuf.length));
      return res.status(200).send(gzipBuf);
    }

    res.setHeader("Content-Length", String(rawBuf.length));
    return res.status(200).send(rawBuf);
  } catch (err) {
    return res.status(500).send("Gagal mengambil playlist");
  }
}
