// YlowstoneTV — Vercel Serverless Direct APK Release Proxy
// Connects directly to GitHub Releases for liveforever24/ylowstonetv
// Uses GitHub 302 pre-signed asset redirect to bypass serverless body size limits.

const REPO_OWNER = "liveforever24";
const REPO_NAME = "ylowstonetv";

module.exports = async function handler(req, res) {
  const token = process.env.GITHUB_TOKEN || "";
  const apiHeaders = {
    "User-Agent": "YlowstoneTV-Web-Release-Proxy",
    "Accept": "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28"
  };

  if (token) {
    apiHeaders["Authorization"] = `Bearer ${token}`;
  }

  try {
    // 1. Fetch latest published release; fallback to recent releases list
    let release = null;
    const latestResp = await fetch(
      `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases/latest`,
      { headers: apiHeaders }
    );

    if (latestResp.ok) {
      release = await latestResp.json();
    } else {
      const listResp = await fetch(
        `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/releases?per_page=5`,
        { headers: apiHeaders }
      );
      if (listResp.ok) {
        const releases = await listResp.json();
        if (Array.isArray(releases) && releases.length > 0) {
          release = releases.find((r) => !r.draft && Array.isArray(r.assets) && r.assets.some((a) => /\.apk$/i.test(a.name))) || releases[0];
        }
      } else {
        const status = listResp.status;
        return res.status(status === 404 ? 404 : 502).json({
          error: "Gagal membaca rilis GitHub YlowstoneTV.",
          hint: token
            ? "Pastikan GITHUB_TOKEN memiliki izin Contents: Read-only pada repo liveforever24/ylowstonetv dan sudah ada minimal 1 Release dengan file .apk."
            : "Repo bersifat private. Tambahkan Environment Variable GITHUB_TOKEN di dashboard Vercel lalu redeploy."
        });
      }
    }

    if (!release || !Array.isArray(release.assets)) {
      return res.status(404).json({ error: "Belum ada rilis YlowstoneTV yang tersedia." });
    }

    const apkAsset = release.assets.find((a) => /\.apk$/i.test(a.name));
    if (!apkAsset) {
      return res.status(404).json({
        error: "Berkas .apk tidak ditemukan pada rilis terbaru.",
        tag: release.tag_name
      });
    }

    // Optional metadata mode: /api/download-apk?info=1
    if (req.query && req.query.info === "1") {
      res.setHeader("Cache-Control", "s-maxage=300, stale-while-revalidate=600");
      return res.status(200).json({
        tag: release.tag_name,
        name: apkAsset.name,
        size: apkAsset.size,
        published_at: release.published_at
      });
    }

    // 2. Request GitHub API asset endpoint with redirect: manual to get short-lived signed URL
    const assetHeaders = {
      "User-Agent": "YlowstoneTV-Web-Release-Proxy",
      "Accept": "application/octet-stream"
    };
    if (token) {
      assetHeaders["Authorization"] = `Bearer ${token}`;
    }

    const assetResp = await fetch(apkAsset.url, {
      headers: assetHeaders,
      redirect: "manual"
    });

    const signedUrl = assetResp.headers.get("location");
    if (signedUrl) {
      res.setHeader("Cache-Control", "no-store");
      res.writeHead(302, { Location: signedUrl });
      return res.end();
    }

    // Fallback for public repositories
    if (apkAsset.browser_download_url) {
      res.setHeader("Cache-Control", "no-store");
      res.writeHead(302, { Location: apkAsset.browser_download_url });
      return res.end();
    }

    return res.status(502).json({ error: "Gagal mendapatkan tautan unduh APK." });
  } catch (err) {
    return res.status(500).json({
      error: "Terjadi kesalahan saat menghubungkan ke GitHub Releases.",
      detail: err && err.message ? err.message : "Unknown error"
    });
  }
};
