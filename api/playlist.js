export default async function handler(req, res) {
  const { file, key } = req.query;

  if (!process.env.PLAYLIST_SECRET || key !== process.env.PLAYLIST_SECRET) {
    return res.status(403).send("Forbidden");
  }

  if (!file || !/^[A-Za-z0-9._-]+\.(m3u|m3u8)$/i.test(file)) {
    return res.status(400).send("Invalid file name");
  }

  const repo = process.env.GH_PLAYLIST_REPO;
  const token = process.env.GH_PLAYLIST_TOKEN;
  const branch = process.env.GH_PLAYLIST_BRANCH || "main";
  const rawUrl = `https://raw.githubusercontent.com/${repo}/${branch}/${encodeURIComponent(file)}`;

  try {
    const ghRes = await fetch(rawUrl, {
      headers: {
        Authorization: `Bearer ${token}`,
        "User-Agent": "YlowstoneTV-Playlist-Proxy",
      },
    });

    if (!ghRes.ok) {
      return res.status(ghRes.status).send("Playlist tidak ditemukan di repo");
    }

    const content = await ghRes.text();
    res.setHeader("Content-Type", "audio/x-mpegurl; charset=utf-8");
    res.setHeader("Cache-Control", "no-store");
    return res.status(200).send(content);
  } catch (err) {
    return res.status(500).send("Gagal mengambil playlist");
  }
}
