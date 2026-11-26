import { gzipSync } from "node:zlib";

export default async function handler(req, res) {
  const { file, key } = req.query;

  if (!process.env.PLAYLIST_SECRET || key !== process.env.PLAYLIST_SECRET.trim()) {
    return res.status(403).send("Forbidden");
  }

  if (!file || !/^[A-Za-z0-9._-]+\.(m3u|m3u8)$/i.test(file)) {
    return res.status(400).send("Invalid file name");
  }

  const repo = (process.env.GH_PLAYLIST_REPO || "").trim();
  const token = (process.env.GH_PLAYLIST_TOKEN || "").trim();
  const branch = (process.env.GH_PLAYLIST_BRANCH || "main").trim();

  const apiUrl = `https://api.github.com/repos/${repo}/contents/${encodeURIComponent(file)}?ref=${branch}`;

  try {
    const ghRes = await fetch(apiUrl, {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: "application/vnd.github.raw+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "YlowstoneTV-Playlist-Proxy",
      },
    });

    if (!ghRes.ok) {
      return res.status(ghRes.status).send(`GitHub error ${ghRes.status} (repo: ${repo}, file: ${file})`);
    }

    const content = await ghRes.text();
    res.setHeader("Content-Type", "audio/x-mpegurl; charset=utf-8");
    res.setHeader("Cache-Control", "no-store");

    const acceptEnc = String(req.headers["accept-encoding"] || "");
    if (acceptEnc.includes("gzip")) {
      const zipped = gzipSync(Buffer.from(content, "utf-8"));
      res.setHeader("Content-Encoding", "gzip");
      return res.status(200).send(zipped);
    }

    return res.status(200).send(content);
  } catch (err) {
    return res.status(500).send("Gagal mengambil playlist");
  }
}
