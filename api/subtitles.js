const https = require('https');
const http = require('http');
const zlib = require('zlib');

function fetchBuffer(targetUrl, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 5) return reject(new Error('Too many redirects'));
    const client = targetUrl.startsWith('https') ? https : http;
    client.get(targetUrl, { headers: { 'User-Agent': 'YlowstoneTV/2.5.4' } }, (res) => {
      if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location) {
        const nextUrl = new URL(res.headers.location, targetUrl).toString();
        res.resume();
        return resolve(fetchBuffer(nextUrl, redirects + 1));
      }
      if (res.statusCode !== 200) {
        res.resume();
        return reject(new Error(`HTTP ${res.statusCode}`));
      }
      const chunks = [];
      res.on('data', (c) => chunks.push(c));
      res.on('end', () => resolve(Buffer.concat(chunks)));
    }).on('error', reject);
  });
}

function cleanTitle(raw) {
  let t = String(raw || '').trim();

  // 1. Lepas prefix negara/kategori/ikon bertumpuk khas M3U (mis. [EN], |US|, EN ❖, US | 4K |, IND -)
  for (let i = 0; i < 4; i++) {
    const prev = t;
    t = t.replace(/^\s*(?:[\[(|]\s*[A-Z0-9+/\- ]{1,14}\s*[\])|]|[A-Z]{2,5}\s*[★♦❖✪•|:/\-])\s*/i, '');
    if (t === prev) break;
  }

  // 2. Deteksi pola episode di judul (S01E30, S01 E30, 1x30, Season 1 Episode 30) lalu pangkas ekornya
  let detectedSeason = null;
  let detectedEpisode = null;
  const seMatch =
    t.match(/\b[Ss](\d{1,2})\s*[.\-_ ]*\s*[Ee](\d{1,3})\b/) ||
    t.match(/\b(\d{1,2})x(\d{1,3})\b/i) ||
    t.match(/\bSeason\s*(\d{1,2})\s*(?:Episode|Ep\.?)\s*(\d{1,3})\b/i);
  if (seMatch) {
    detectedSeason = parseInt(seMatch[1], 10);
    detectedEpisode = parseInt(seMatch[2], 10);
    const cutIdx = t.indexOf(seMatch[0]);
    if (cutIdx >= 2) {
      t = t.slice(0, cutIdx);
    }
  } else {
    t = t.replace(/\s*[-–:|]\s*(?:Season|S)\s*\d{1,2}.*$/i, '');
  }

  // 3. Ambil tahun rilis jika ada
  const yearMatch = t.match(/[\[(](19\d{2}|20\d{2})[\])]/);
  const year = yearMatch ? yearMatch[1] : null;

  // 4. Hapus bracket/kurung metadata & tag kualitas/bahasa M3U
  t = t.replace(/[\[(][^\]\)]{1,28}[\])]/g, ' ');
  t = t.replace(
    /\b(4K|UHD|FHD|HD|SD|1080p|720p|2160p|480p|WEB[\s.-]?DL|WEBRip|BluRay|BDRip|BRRip|HDR10?|DV|HEVC|x26[45]|H\.?26[45]|AAC|AC3|DDP5\.?1|MULTI[\s.-]?SUB|SUB[\s.-]?INDO|INDO[\s.-]?SUB|DUB(?:BED)?|CAM|HDTS|TS|EXTENDED|REMASTERED|UNRATED|DIRECTORS?[\s.-]?CUT)\b/gi,
    ' '
  );

  // 5. Normalkan titik/underscore jika judul pakai pemisah titik (The.Dark.Knight)
  if (!t.includes(' ') && /[._]/.test(t)) {
    t = t.replace(/[._]+/g, ' ');
  } else {
    t = t.replace(/_+/g, ' ');
  }

  // 6. Hapus tahun telanjang di akhir & tanda baca sisa
  t = t.replace(/\b(19\d{2}|20\d{2})\s*$/, '');
  t = t.replace(/[\s\-–:|/]+$/g, '').replace(/^[\s\-–:|/]+/g, '');
  t = t.replace(/\s+/g, ' ').trim();

  return { title: t, year, detectedSeason, detectedEpisode };
}

function extractZipEntries(buf) {
  const entries = [];
  let eocdOffset = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65557); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) {
      eocdOffset = i;
      break;
    }
  }
  if (eocdOffset === -1) return entries;

  const totalEntries = buf.readUInt16LE(eocdOffset + 10);
  let cdOffset = buf.readUInt32LE(eocdOffset + 16);

  for (let i = 0; i < totalEntries && cdOffset + 46 <= buf.length; i++) {
    if (buf.readUInt32LE(cdOffset) !== 0x02014b50) break;
    const method = buf.readUInt16LE(cdOffset + 10);
    const compSize = buf.readUInt32LE(cdOffset + 20);
    const nameLen = buf.readUInt16LE(cdOffset + 28);
    const extraLen = buf.readUInt16LE(cdOffset + 30);
    const commentLen = buf.readUInt16LE(cdOffset + 32);
    const localOffset = buf.readUInt32LE(cdOffset + 42);
    const fileName = buf.slice(cdOffset + 46, cdOffset + 46 + nameLen).toString('utf8');

    if (!fileName.endsWith('/') && !fileName.includes('__MACOSX') && !fileName.split('/').pop().startsWith('._')) {
      if (localOffset + 30 <= buf.length && buf.readUInt32LE(localOffset) === 0x04034b50) {
        const lNameLen = buf.readUInt16LE(localOffset + 26);
        const lExtraLen = buf.readUInt16LE(localOffset + 28);
        const dataStart = localOffset + 30 + lNameLen + lExtraLen;
        const compData = buf.slice(dataStart, dataStart + compSize);
        try {
          const rawData = method === 0 ? compData : method === 8 ? zlib.inflateRawSync(compData) : null;
          if (rawData) entries.push({ fileName, data: rawData });
        } catch (_) {}
      }
    }
    cdOffset += 46 + nameLen + extraLen + commentLen;
  }
  return entries;
}

function pickBestSubtitleEntry(entries, season, episode) {
  const subs = entries.filter((e) => /\.(srt|vtt)$/i.test(e.fileName));
  if (subs.length === 0) return null;
  if (subs.length === 1 || !episode) return subs[0];

  const s = Number(season || 1);
  const e = Number(episode);
  const ePad3 = String(e).padStart(3, '0');

  const rSxEy = new RegExp(`s0?${s}[^a-z0-9]*e0?${e}(?![0-9])`, 'i');
  const rX = new RegExp(`(^|[^0-9])0?${s}x0?${e}(?![0-9])`, 'i');
  const rEp = new RegExp(`(^|[^a-z0-9])(?:ep?|episode)[\\s._-]*0?${e}(?![0-9])`, 'i');
  const rNum = new RegExp(`(^|[^0-9])${ePad3}(?![0-9])`);

  let best = subs[0];
  let bestScore = -1;
  for (const item of subs) {
    const base = item.fileName.split('/').pop();
    let score = 0;
    if (rSxEy.test(base)) score += 100;
    if (rX.test(base)) score += 90;
    if (rEp.test(base)) score += 70;
    if (rNum.test(base)) score += 50;
    if (/\.srt$/i.test(base)) score += 5;
    if (score > bestScore) {
      bestScore = score;
      best = item;
    }
  }
  return best;
}

module.exports = async function handler(req, res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  const apiKey = process.env.SUBDL_API_KEY;
  if (!apiKey) {
    return res.status(500).json({ ok: false, error: 'SUBDL_API_KEY belum diset di Vercel' });
  }

  try {
    const { zip, title, type, season, episode } = req.query || {};

    if (zip) {
      let dlUrl = zip.startsWith('http') ? zip : `https://dl.subdl.com${zip.startsWith('/') ? '' : '/'}${zip}`;
      if (!dlUrl.includes('api_key=')) {
        dlUrl += `${dlUrl.includes('?') ? '&' : '?'}api_key=${encodeURIComponent(apiKey)}`;
      }
      const zipBuf = await fetchBuffer(dlUrl);
      const entries = extractZipEntries(zipBuf);
      const chosen = pickBestSubtitleEntry(entries, season, episode);
      if (!chosen) {
        return res.status(404).json({ ok: false, error: 'File .srt tidak ditemukan di dalam arsip' });
      }
      let text = chosen.data.toString('utf8');
      if (text.includes('\uFFFD')) text = chosen.data.toString('latin1');
      text = text.replace(/^\uFEFF/, '').replace(/^WEBVTT[^\n]*\n+/i, '');
      res.setHeader('Content-Type', 'text/plain; charset=utf-8');
      res.setHeader('Cache-Control', 'public, s-maxage=86400, stale-while-revalidate=604800');
      return res.status(200).send(text);
    }

    if (!title) {
      return res.status(400).json({ ok: false, error: 'Parameter title wajib diisi' });
    }

    const parsed = cleanTitle(title);
    const effSeason = season || parsed.detectedSeason || null;
    const effEpisode = episode || parsed.detectedEpisode || null;
    const primaryType = (type === 'tv' || (effSeason && effEpisode)) ? 'tv' : 'movie';

    const querySubdl = async (filmName, mType, useYear, withEpisode) => {
      if (!filmName || filmName.length < 2) return [];
      const u = new URL('https://api.subdl.com/api/v1/subtitles');
      u.searchParams.set('api_key', apiKey);
      u.searchParams.set('film_name', filmName);
      u.searchParams.set('type', mType);
      u.searchParams.set('languages', 'ID');
      if (useYear && parsed.year && mType === 'movie') u.searchParams.set('year', parsed.year);
      if (mType === 'tv' && effSeason) u.searchParams.set('season_number', String(effSeason));
      if (mType === 'tv' && withEpisode && effEpisode) u.searchParams.set('episode_number', String(effEpisode));
      try {
        const raw = await fetchBuffer(u.toString());
        const json = JSON.parse(raw.toString('utf8'));
        return Array.isArray(json.subtitles) ? json.subtitles : [];
      } catch (_) {
        return [];
      }
    };

    // Multi-stage smart search untuk M3U & Xtream
    let list = await querySubdl(parsed.title, primaryType, true, true);

    if (list.length === 0 && primaryType === 'tv' && effEpisode) {
      list = await querySubdl(parsed.title, 'tv', false, false);
    }
    if (list.length === 0 && parsed.year) {
      list = await querySubdl(parsed.title, primaryType, false, true);
    }
    if (list.length === 0 && /[:\-–]/.test(parsed.title)) {
      const shortTitle = parsed.title.split(/[:\-–]/)[0].trim();
      if (shortTitle.length >= 2) {
        list = await querySubdl(shortTitle, primaryType, false, false);
      }
    }
    if (list.length === 0) {
      const altType = primaryType === 'tv' ? 'movie' : 'tv';
      list = await querySubdl(parsed.title, altType, false, false);
    }

    const results = list
      .filter((s) => s && s.url)
      .sort((a, b) => {
        const score = (x) => {
          const r = (x.release_name || '').toLowerCase();
          let sc = 0;
          if (!x.full_season && effEpisode) sc += 20;
          if (/web-?dl|webrip/.test(r)) sc += 15;
          if (/bluray|brrip|bdrip/.test(r)) sc += 10;
          return sc;
        };
        return score(b) - score(a);
      })
      .slice(0, 12)
      .map((s, idx) => ({
        id: `${idx}_${s.sd_id || idx}`,
        releaseName: (s.release_name || s.name || 'Subtitle Indonesia').trim(),
        author: (s.author || '').trim(),
        fullSeason: Boolean(s.full_season),
        zipUrl: String(s.url).replace(/[?&]api_key=[^&]+/gi, '')
      }));

    res.setHeader('Cache-Control', 'public, s-maxage=3600, stale-while-revalidate=86400');
    return res.status(200).json({ ok: true, query: parsed.title, results });
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message || 'Gagal memproses subtitle' });
  }
};
