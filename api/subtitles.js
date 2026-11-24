const https = require('https');
const http = require('http');
const zlib = require('zlib');

function fetchBuffer(targetUrl, redirects = 0) {
  return new Promise((resolve, reject) => {
    if (redirects > 5) return reject(new Error('Too many redirects'));
    const client = targetUrl.startsWith('https') ? https : http;
    client.get(targetUrl, { headers: { 'User-Agent': 'YlowstoneTV/2.2.5' } }, (res) => {
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
  let t = (raw || '').trim();
  t = t.replace(/^[A-Z]{2,4}\s*[★♦✪•|:-]\s*/i, '');
  const yearMatch = t.match(/[\[(](19\d{2}|20\d{2})[\])]/);
  const year = yearMatch ? yearMatch[1] : null;
  t = t.replace(/[\[(](19\d{2}|20\d{2}|4K|UHD|FHD|HD|SD|CAM|TS|HEVC|H\.?26[45])[\])]/gi, ' ');
  t = t.replace(/\b(4K|UHD|FHD|HD|1080p|720p|2160p)\b/gi, ' ');
  t = t.replace(/\s+/g, ' ').trim();
  return { title: t, year };
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
      return res.status(200).send(text);
    }

    if (!title) {
      return res.status(400).json({ ok: false, error: 'Parameter title wajib diisi' });
    }
    const parsed = cleanTitle(title);
    const mediaType = type === 'tv' ? 'tv' : 'movie';

    const querySubdl = async (withEpisode) => {
      const u = new URL('https://api.subdl.com/api/v1/subtitles');
      u.searchParams.set('api_key', apiKey);
      u.searchParams.set('film_name', parsed.title);
      u.searchParams.set('type', mediaType);
      u.searchParams.set('languages', 'ID');
      if (parsed.year && mediaType === 'movie') u.searchParams.set('year', parsed.year);
      if (mediaType === 'tv' && season) u.searchParams.set('season_number', String(season));
      if (mediaType === 'tv' && withEpisode && episode) u.searchParams.set('episode_number', String(episode));
      const raw = await fetchBuffer(u.toString());
      return JSON.parse(raw.toString('utf8'));
    };

    let data = await querySubdl(true);
    let list = Array.isArray(data.subtitles) ? data.subtitles : [];
    if (list.length === 0 && mediaType === 'tv' && episode) {
      data = await querySubdl(false);
      list = Array.isArray(data.subtitles) ? data.subtitles : [];
    }

    const results = list
      .filter((s) => s && s.url)
      .sort((a, b) => {
        const score = (x) => {
          const r = (x.release_name || '').toLowerCase();
          let sc = 0;
          if (!x.full_season && episode) sc += 20;
          if (/web-?dl|nf|netflix|amzn|dsnp/.test(r)) sc += 15;
          if (/bluray|brrip/.test(r)) sc += 10;
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
        zipUrl: s.url
      }));

    return res.status(200).json({ ok: true, query: parsed.title, results });
  } catch (err) {
    return res.status(500).json({ ok: false, error: err.message || 'Gagal memproses subtitle' });
  }
};
