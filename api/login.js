const FIXED_USERNAME = 'YlowstoneTV';

module.exports = (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') return res.status(200).end();
  if (req.method === 'GET') {
    return res.status(200).json({ ok: true, service: 'YlowstoneTV Auth API' });
  }
  if (req.method !== 'POST') {
    return res.status(405).json({ ok: false, error: 'Method not allowed' });
  }

  let body = req.body;
  if (typeof body === 'string') {
    try { body = JSON.parse(body); } catch { body = {}; }
  }

  const inputUser = String(body?.username || '').trim();
  const inputPin = String(body?.pin || '').trim();

  if (!inputUser || !inputPin) {
    return res.status(400).json({ ok: false, error: 'Username dan PIN wajib diisi' });
  }

  const expectedPin = String(process.env.ADMIN_PIN || '').trim();
  if (!expectedPin) {
    return res.status(500).json({ ok: false, error: 'ADMIN_PIN belum diatur di Vercel' });
  }

  const validUser = inputUser.toLowerCase() === FIXED_USERNAME.toLowerCase();
  const validPin = inputPin === expectedPin;

  if (!validUser || !validPin) {
    return res.status(401).json({ ok: false, error: 'Username atau PIN salah' });
  }

  let playlists = [];
  if (process.env.PLAYLISTS_JSON) {
    try {
      const parsed = JSON.parse(process.env.PLAYLISTS_JSON);
      if (Array.isArray(parsed)) playlists = parsed;
    } catch {
      return res.status(500).json({ ok: false, error: 'Format PLAYLISTS_JSON tidak valid' });
    }
  }

  return res.status(200).json({
    ok: true,
    username: FIXED_USERNAME,
    role: 'admin',
    playlists
  });
};
