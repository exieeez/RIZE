const {
  sendJson,
  parseBody,
  searchTracks,
  searchTracksByArtist,
  getRecommendations,
  getChartTracks,
  fetchStreamUrl,
  streamCache,
  STREAM_CACHE_TTL,
  httpGet,
  httpPostJSON,
  appRoot,
  SEARCH_LIMIT,
  SEARCH_MAX_LIMIT,
  findYtDlp,
  scImportPlaylist,
  YT_UA,
} = require('./utils.js');

const { spawn } = require('child_process');
const path = require('path');
const os = require('os');
const fs = require('fs');
const https = require('https');
const http = require('http');
const crypto = require('crypto');
const { URL } = require('url');
const { getSystemAudioOutputInfo } = require('./audio-output.js');

// Persistent keep-alive agents for stream proxying across range requests
const proxyHttpsAgent = new https.Agent({
  keepAlive: true,
  maxSockets: 64,
  maxFreeSockets: 16,
  timeout: 60000,
  keepAliveMsecs: 10000,
});
const proxyHttpAgent = new http.Agent({
  keepAlive: true,
  maxSockets: 64,
  maxFreeSockets: 16,
  timeout: 60000,
  keepAliveMsecs: 10000,
});

const lyricsMemCache = new Map();
const LYRICS_CACHE_TTL = 24 * 60 * 60 * 1000;

async function translateTexts(texts, targetLang = 'ru') {
  if (!Array.isArray(texts) || texts.length === 0) return [];
  const chunks = [];
  let curChunk = [];
  let curLen = 0;
  for (const text of texts) {
    const s = String(text || '').trim();
    if (curChunk.length > 0 && curLen + s.length + 1 > 1200) {
      chunks.push(curChunk);
      curChunk = [];
      curLen = 0;
    }
    curChunk.push(s);
    curLen += s.length + 1;
  }
  if (curChunk.length > 0) chunks.push(curChunk);

  const results = [];
  for (const chunk of chunks) {
    const query = chunk.join('\n');
    const encoded = encodeURIComponent(query);
    const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=${encodeURIComponent(targetLang)}&dt=t&q=${encoded}`;
    let success = false;
    try {
      const resp = await fetch(url, { headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)' }, signal: AbortSignal.timeout(6000) });
      if (resp.ok) {
        const data = await resp.json();
        if (Array.isArray(data) && Array.isArray(data[0])) {
          const translatedJoined = data[0].map(s => s[0] || '').join('');
          const splitLines = translatedJoined.replace(/\r/g, '').trimEnd().split('\n');
          for (let i = 0; i < chunk.length; i++) {
            results.push(splitLines[i] ? splitLines[i].trim() : chunk[i]);
          }
          success = true;
        }
      }
    } catch (e) {
      console.warn('[translate] Google GTX error, attempting fallback:', e.message);
    }

    if (!success) {
      results.push(...chunk);
    }
  }
  return results;
}

async function handleMusicRoutes(req, res, u) {
  // --- SPOTIFY PROFILE ENDPOINT ---
  if (u.pathname === '/api/spotify/profile' || u.pathname === '/api/spotify-profile') {
    const rawInput = u.searchParams.get('url') || u.searchParams.get('user') || u.searchParams.get('q') || '';
    if (!rawInput.trim()) {
      sendJson(res, 400, { success: false, error: 'Не указан пользователь или ссылка на профиль Spotify' });
      return true;
    }

    try {
      const profile = await fetchSpotifyUserProfile(rawInput.trim());
      sendJson(res, 200, { success: true, profile, user: profile });
    } catch (e) {
      console.warn('[spotify-profile] Error:', e.message);
      sendJson(res, 404, { success: false, error: e.message || 'Профиль Spotify не найден' });
    }
    return true;
  }

  // --- SPOTIFY DIRECT & COMPAT IMPORT ENDPOINT ---
  if (u.pathname === '/api/spotify-import' || u.pathname === '/api/spotify/import') {
    const rawUrl = u.searchParams.get('url')?.trim();
    if (!rawUrl) {
      sendJson(res, 400, { success: false, code: 'spotify_error', error: 'Не указан URL для импорта' });
      return true;
    }
    try {
      const { name, cover, items } = await extractTracksFromUrl(rawUrl);
      sendJson(res, 200, {
        success: true,
        name: name || 'Spotify Playlist',
        cover: cover || '',
        tracks: items || [],
        total: (items || []).length,
      });
    } catch (e) {
      console.warn('[spotify-import] Error:', e.message);
      sendJson(res, 400, { success: false, code: 'spotify_error', error: e.message || 'Ошибка импорта Spotify' });
    }
    return true;
  }

  // --- SPOTIFY OAUTH & LIBRARY ENDPOINTS ---
  if (u.pathname === '/api/spotify-status') {
    sendJson(res, 200, { connected: Boolean(spotifyAuth.token) });
    return true;
  }

  if (u.pathname === '/api/spotify-logout') {
    spotifyAuth.token = '';
    sendJson(res, 200, { success: true });
    return true;
  }

  if (u.pathname === '/api/spotify-auth-start') {
    const clientId = u.searchParams.get('clientId') || '';
    if (!/^[a-f0-9]{32}$/i.test(clientId)) {
      sendJson(res, 400, { success: false, error: 'Invalid Client ID' });
      return true;
    }
    const verifier = crypto.randomBytes(48).toString('base64url');
    const challenge = crypto.createHash('sha256').update(verifier).digest('base64url');
    const state = crypto.randomBytes(16).toString('hex');
    spotifyAuth = { verifier, state, clientId, token: '' };
    const port = Number(u.port) || 38472;
    const redirectUri = getSpotifyRedirectUri(port);
    const authUrl = `https://accounts.spotify.com/authorize?response_type=code&client_id=${encodeURIComponent(clientId)}&scope=${encodeURIComponent(SPOTIFY_SCOPES)}&redirect_uri=${encodeURIComponent(redirectUri)}&state=${encodeURIComponent(state)}&code_challenge_method=S256&code_challenge=${encodeURIComponent(challenge)}`;
    sendJson(res, 200, { success: true, url: authUrl, redirectUri });
    return true;
  }

  if (u.pathname === '/api/spotify-callback') {
    const code = u.searchParams.get('code');
    const state = u.searchParams.get('state');
    const errParam = u.searchParams.get('error');
    if (errParam || !state || state !== spotifyAuth.state || !code) {
      res.writeHead(400, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end('<!DOCTYPE html><html><body><p>Login failed, you can close this tab.</p></body></html>');
      return true;
    }
    const port = Number(u.port) || 38472;
    const redirectUri = getSpotifyRedirectUri(port);
    try {
      const tokenResult = await exchangeSpotifyCode(code, spotifyAuth.verifier, spotifyAuth.clientId, redirectUri);
      if (tokenResult.status >= 200 && tokenResult.status < 300 && tokenResult.data && tokenResult.data.access_token) {
        spotifyAuth.token = tokenResult.data.access_token;
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end('<!DOCTYPE html><html><body style="font-family:sans-serif;background:#121212;color:#fff;text-align:center;padding-top:40px;"><h2>Riff is connected to Spotify!</h2><p>You can close this tab and return to the app.</p></body></html>');
      } else {
        res.writeHead(400, { 'Content-Type': 'text/html; charset=utf-8' });
        res.end('<!DOCTYPE html><html><body><p>Login failed, you can close this tab.</p></body></html>');
      }
    } catch (err) {
      res.writeHead(500, { 'Content-Type': 'text/html; charset=utf-8' });
      res.end('<!DOCTYPE html><html><body><p>Login failed, you can close this tab.</p></body></html>');
    }
    return true;
  }

  if (u.pathname === '/api/spotify-library') {
    await handleSpotifyLibrary(req, res, u.searchParams);
    return true;
  }

  // --- SEARCH ---
  if (u.pathname === '/api/search') {
    const q = u.searchParams.get('q')?.trim();
    if (!q) {
      sendJson(res, 400, { error: 'Empty query' });
      return true;
    }
    const limit = Math.min(Number(u.searchParams.get('limit')) || SEARCH_LIMIT, SEARCH_MAX_LIMIT);
    const rawTracks = await searchTracks(q, limit, true);
    const tracks = (rawTracks || []).map(t => ({
      ...t,
      platform: t.platform || 'youtube',
      duration: t.duration || 0,
      url: t.url || `/api/audio?id=${encodeURIComponent(t.id)}`,
    }));
    sendJson(res, 200, { success: true, tracks });
    return true;
  }

async function getDeezerArtistData(name) {
  try {
    const searchRes = await fetch(`https://api.deezer.com/search/artist?q=${encodeURIComponent(name)}&limit=5`, {
      headers: { 'User-Agent': 'Votify/1.1' },
      signal: AbortSignal.timeout(6000),
    });
    if (!searchRes.ok) return null;
    const searchJson = await searchRes.json();
    if (!searchJson || !Array.isArray(searchJson.data) || !searchJson.data.length) return null;

    const best = searchJson.data.find(a => a.name.toLowerCase() === name.toLowerCase()) || searchJson.data[0];
    const artistId = best.id;

    const [artistRes, topRes, albumsRes, relatedRes] = await Promise.allSettled([
      fetch(`https://api.deezer.com/artist/${artistId}`, { headers: { 'User-Agent': 'Votify/1.1' }, signal: AbortSignal.timeout(6000) }).then(r => r.json()),
      fetch(`https://api.deezer.com/artist/${artistId}/top?limit=40`, { headers: { 'User-Agent': 'Votify/1.1' }, signal: AbortSignal.timeout(6000) }).then(r => r.json()),
      fetch(`https://api.deezer.com/artist/${artistId}/albums?limit=60`, { headers: { 'User-Agent': 'Votify/1.1' }, signal: AbortSignal.timeout(6000) }).then(r => r.json()),
      fetch(`https://api.deezer.com/artist/${artistId}/related?limit=16`, { headers: { 'User-Agent': 'Votify/1.1' }, signal: AbortSignal.timeout(6000) }).then(r => r.json()),
    ]);

    const artistDetails = artistRes.status === 'fulfilled' && artistRes.value && !artistRes.value.error ? artistRes.value : best;
    const topData = topRes.status === 'fulfilled' && topRes.value && Array.isArray(topRes.value.data) ? topRes.value.data : [];
    const albumsData = albumsRes.status === 'fulfilled' && albumsRes.value && Array.isArray(albumsRes.value.data) ? albumsRes.value.data : [];
    const relatedData = relatedRes.status === 'fulfilled' && relatedRes.value && Array.isArray(relatedRes.value.data) ? relatedRes.value.data : [];

    const tracks = topData.map(t => ({
      id: `dz_${t.id}`,
      title: t.title,
      artist: t.artist?.name || best.name,
      duration: t.duration || 0,
      cover: t.album?.cover_xl || t.album?.cover_big || t.album?.cover_medium || '',
      rank: t.rank || 0,
      plays: t.rank || 0,
      album: t.album?.title || '',
      preview: t.preview || '',
    }));

    const studioAlbums = albumsData
      .filter(a => (a.record_type || 'album').toLowerCase() === 'album')
      .map(a => ({
        id: a.id,
        title: a.title,
        cover: a.cover_xl || a.cover_big || a.cover_medium || '',
        releaseDate: a.release_date || '',
        recordType: 'album',
        trackCount: a.nb_tracks || 0,
      }));

    const singles = albumsData
      .filter(a => (a.record_type || 'album').toLowerCase() !== 'album')
      .map(a => ({
        id: a.id,
        title: a.title,
        cover: a.cover_xl || a.cover_big || a.cover_medium || '',
        releaseDate: a.release_date || '',
        recordType: a.record_type || 'single',
        trackCount: a.nb_tracks || 0,
      }));

    const related = relatedData.map(a => ({
      id: a.id,
      name: a.name,
      picture: a.picture_xl || a.picture_big || a.picture_medium || '',
      nb_fan: a.nb_fan || 0,
    }));

    return {
      artist: artistDetails.name || best.name,
      artistDetails: {
        id: artistDetails.id,
        name: artistDetails.name || best.name,
        picture: artistDetails.picture_xl || artistDetails.picture_big || artistDetails.picture_medium || '',
        nb_fan: artistDetails.nb_fan || 0,
        nb_album: artistDetails.nb_album || 0,
      },
      tracks,
      albums: studioAlbums.length ? studioAlbums : albumsData.map(a => ({
        id: a.id,
        title: a.title,
        cover: a.cover_xl || a.cover_big || a.cover_medium || '',
        releaseDate: a.release_date || '',
        recordType: a.record_type || 'album',
        trackCount: a.nb_tracks || 0,
      })),
      singles,
      relatedArtists: related,
    };
  } catch (err) {
    console.warn('[deezer] Error fetching artist:', err.message);
    return null;
  }
}

  // --- ARTIST ---
  if (u.pathname === '/api/artist') {
    const name = u.searchParams.get('name')?.trim();
    if (!name) {
      sendJson(res, 400, { error: 'Artist name required' });
      return true;
    }
    const limit = Math.min(Math.max(Number(u.searchParams.get('limit')) || 50, 1), 100);

    const deezerData = await getDeezerArtistData(name);
    if (deezerData && deezerData.tracks && deezerData.tracks.length > 0) {
      sendJson(res, 200, {
        success: true,
        artist: deezerData.artist,
        artistDetails: deezerData.artistDetails,
        tracks: deezerData.tracks,
        albums: deezerData.albums || [],
        singles: deezerData.singles || [],
        relatedArtists: deezerData.relatedArtists || [],
        artistData: {
          name: deezerData.artist,
          avatar: deezerData.artistDetails?.picture || (deezerData.tracks[0] && deezerData.tracks[0].cover) || '',
          isVerified: true,
          tracks: deezerData.tracks,
          albums: deezerData.albums || [],
          singles: deezerData.singles || [],
          related: deezerData.relatedArtists || []
        }
      });
      return true;
    }

    const tracks = await searchTracksByArtist(name, limit);
    sendJson(res, 200, {
      success: true,
      artist: name,
      tracks: tracks || [],
      artistData: {
        name,
        avatar: (tracks[0] && (tracks[0].cover || tracks[0].thumbnail)) || '',
        isVerified: false,
        tracks: tracks || [],
        albums: [],
        singles: [],
        related: []
      }
    });
    return true;
  }

  // --- DEEZER ALBUM TRACKS ---
  if (u.pathname === '/api/deezer/album-tracks') {
    const id = u.searchParams.get('id');
    if (!id) {
      sendJson(res, 400, { error: 'Album ID required' });
      return true;
    }
    try {
      const resp = await fetch(`https://api.deezer.com/album/${id}/tracks?limit=100`, {
        headers: { 'User-Agent': 'Votify/1.1' },
        signal: AbortSignal.timeout(6000),
      });
      const data = await resp.json();
      sendJson(res, 200, { tracks: (data && data.data) || [] });
    } catch (e) {
      sendJson(res, 500, { error: e.message });
    }
    return true;
  }

  // --- RECOMMENDATIONS ---
  if (u.pathname === '/api/recommendations') {
    const limit = u.searchParams.get('limit');
    const t = await getRecommendations(limit);
    if (!t.length) {
      sendJson(res, 500, { error: 'No recommendations' });
      return true;
    }
    sendJson(res, 200, { tracks: t });
    return true;
  }

  // --- CHARTS (живой чарт Apple Music: что слушают прямо сейчас / hot picks) ---
  if (u.pathname === '/api/charts' || u.pathname === '/api/hot') {
    const region = (u.searchParams.get('region') || 'ru').toLowerCase();
    const limit = Math.min(Math.max(Number(u.searchParams.get('limit')) || 30, 1), 50);
    try {
      const chart = await getChartTracks(region, limit);
      if (chart.length) {
        sendJson(res, 200, { success: true, tracks: chart });
        return true;
      }
    } catch (e) {
      console.error('[charts] не удалось получить чарт:', e.message);
    }
    // Чарт недоступен — отдаём обычные рекомендации, чтобы экран не пустовал.
    sendJson(res, 200, { success: true, tracks: await getRecommendations(limit) });
    return true;
  }

  // --- CUSTOM WAVE (seeds from playlists + recent) ---
  if (u.pathname === '/api/custom-wave') {
    const seedsParam = u.searchParams.get('seeds') || '';
    const trackSeedsParam = u.searchParams.get('trackSeeds') || '';
    const excludeParam = u.searchParams.get('exclude') || '';
    const limit = Math.min(Number(u.searchParams.get('limit')) || 20, 40);
    const seeds = seedsParam.split('|').filter(Boolean).slice(0, 8);
    const trackSeeds = trackSeedsParam.split('|').filter(Boolean).slice(0, 6);
    const excludeIds = new Set(excludeParam.split(',').filter(Boolean));
    if (!seeds.length && !trackSeeds.length) {
      sendJson(res, 400, { error: 'No seeds' });
      return true;
    }
    const totalSeeds = seeds.length + trackSeeds.length || 1;
    const perSeedLimit = Math.ceil(limit / totalSeeds) + 2;
    const results = await Promise.allSettled([
      // Plain artist seeds: bias toward that artist's actual songs
      ...seeds.map(seed => searchTracksByArtist(seed, perSeedLimit)),
      // Exact "artist title" seeds: keeps the wave anchored to songs you actually have
      ...trackSeeds.map(seed => searchTracks(seed, perSeedLimit, false)),
    ]);
    const allTracks = results.flatMap(r => (r.status === 'fulfilled' ? r.value : []));
    const normTrackKey = t => {
      const a = String(t.artist || '').toLowerCase().replace(/[^\p{L}\p{N}]/gu, '');
      const tit = String(t.title || '').toLowerCase().replace(/[^\p{L}\p{N}]/gu, '');
      return `${a}::${tit}`;
    };
    const seenIds = new Set();
    const seenNames = new Set();
    const unique = allTracks.filter(t => {
      if (!t || !t.id) return false;
      if (excludeIds.has(t.id)) return false;
      if (seenIds.has(t.id)) return false;
      const key = normTrackKey(t);
      if (key.length > 5 && seenNames.has(key)) return false;
      seenIds.add(t.id);
      if (key.length > 5) seenNames.add(key);
      return true;
    });
    // Ранжируем по похожести: совпадение артиста весит больше всего, потом —
    // совпадение слов в названии. Раньше список просто перемешивался, поэтому
    // в волну попадало что угодно, только не похожее на то, что вы слушаете.
    const norm = s =>
      String(s || '')
        .toLowerCase()
        .replace(/[^\p{L}\p{N}& ]/gu, ' ')
        .replace(/\s+/g, ' ')
        .trim();
    const words = s => norm(s).split(' ').filter(w => w.length >= 3);
    const seedArtists = [...seeds.map(norm), ...trackSeeds.map(s => norm(s.split(' ').slice(0, 2).join(' ')))].filter(
      Boolean,
    );
    const seedWords = new Set(trackSeeds.flatMap(words));
    const scored = unique.map(t => {
      const artist = norm(t.artist);
      let score = 0;
      if (seedArtists.some(a => artist === a)) score += 6;
      else if (seedArtists.some(a => a && (artist.includes(a) || a.includes(artist)))) score += 4;
      if (words(t.title).some(w => seedWords.has(w))) score += 0.75;
      return { t, score: score + Math.random() };
    });
    scored.sort((a, b) => b.score - a.score);
    sendJson(res, 200, { tracks: scored.map(s => s.t).slice(0, limit) });
    return true;
  }

  // --- STREAM PROXY (NEW) ---
  if (u.pathname === '/api/stream') {
    const id = u.searchParams.get('id')?.trim();
    if (!id) {
      sendJson(res, 400, { error: 'No id' });
      return true;
    }
    const isDownload = u.searchParams.get('download') === '1';
    const upstreamTimeoutMs = isDownload ? 300000 : 30000;
    // Демо-треки офлайн-каталога отдаются локально, без YouTube
    const demo = require('./demo.js');
    if (demo.isDemoId(id)) {
      if (demo.serveDemoAudio(id, res)) return true;
    }
    try {
      const streamUrl = await fetchStreamUrl(id);
      if (!streamUrl) {
        sendJson(res, 502, { error: 'No stream available' });
        return true;
      }
      proxyStream(streamUrl, req, res, 0, upstreamTimeoutMs);
      return true;
    } catch (e) {
      console.error('Stream setup error:', e.message);
      sendJson(res, 502, { error: 'Stream setup failed' });
      return true;
    }
  }

  // Helper function for redirects & stream proxying
  function proxyStream(url, req, res, depth = 0, upstreamTimeoutMs = 30000) {
    if (depth > 5) {
      if (!res.headersSent) sendJson(res, 502, { error: 'Too many redirects' });
      return;
    }
    try {
      const remote = new URL(url);
      const transport = remote.protocol === 'https:' ? https : http;
      const agent = remote.protocol === 'https:' ? proxyHttpsAgent : proxyHttpAgent;

      const headers = {
        'User-Agent': YT_UA,
        Accept: '*/*',
        'Accept-Encoding': 'identity',
        'Accept-Language': 'en-US,en;q=0.9',
        Referer: 'https://www.youtube.com/',
        Origin: 'https://www.youtube.com',
        'Sec-Fetch-Dest': 'audio',
        'Sec-Fetch-Mode': 'cors',
        'Sec-Fetch-Site': 'cross-site',
      };
      if (req.headers.range) headers.Range = req.headers.range;

      const upstream = transport.request(remote, { method: 'GET', headers, agent }, upRes => {
        const sc = upRes.statusCode || 500;
        if ([301, 302, 303, 307, 308].includes(sc) && upRes.headers.location) {
          upRes.resume();
          proxyStream(upRes.headers.location, req, res, depth + 1, upstreamTimeoutMs);
          return;
        }
        const rh = {
          'Content-Type': upRes.headers['content-type'] || 'audio/webm',
          'Cache-Control': 'no-store',
          'Accept-Ranges': 'bytes',
        };
        if (upRes.headers['content-length']) rh['Content-Length'] = upRes.headers['content-length'];
        if (upRes.headers['content-range']) rh['Content-Range'] = upRes.headers['content-range'];
        res.writeHead(sc, rh);
        upRes.pipe(res);
      });

      const onClose = () => {
        if (!res.writableEnded) upstream.destroy();
      };
      res.once('close', onClose);

      upstream.on('error', e => {
        res.removeListener('close', onClose);
        console.error('Stream proxy error:', e.message);
        if (!res.headersSent) sendJson(res, 502, { error: 'Stream proxy failed' });
      });
      upstream.setTimeout(upstreamTimeoutMs, () => {
        upstream.destroy(new Error('Stream upstream timeout'));
      });
      upstream.end();
    } catch (e) {
      console.error('Stream redirect error:', e.message);
      if (!res.headersSent) sendJson(res, 502, { error: 'Stream redirect failed' });
    }
  }

  // --- AUDIO STREAM (legacy, returns URL) ---
  if (u.pathname === '/api/audio') {
    const id = u.searchParams.get('id')?.trim();
    if (!id) {
      sendJson(res, 400, { error: 'No id' });
      return true;
    }
    // Демо-треки офлайн-каталога отдаются локально
    const demoLib = require('./demo.js');
    if (demoLib.isDemoId(id)) {
      sendJson(res, 200, { url: '/demo/audio/' + id + '.wav' });
      return true;
    }
    // SoundCloud tracks - use fetchStreamUrl
    if (id.startsWith('sc_')) {
      try {
        const streamUrl = await fetchStreamUrl(id);
        if (!streamUrl) {
          sendJson(res, 502, { error: 'No stream' });
          return true;
        }
        sendJson(res, 200, { url: streamUrl });
        return true;
      } catch (e) {
        sendJson(res, 502, { error: e.message });
        return true;
      }
    }
    try {
      const streamUrl = await fetchStreamUrl(id);
      if (streamUrl) {
        sendJson(res, 200, { url: streamUrl });
        return true;
      }
    } catch (e) {
      console.log('[stream] Error fetching stream for', id, ':', e.message);
    }
    sendJson(res, 502, { error: 'No stream available' });
    return true;
  }

  // --- PREFETCH / PRELOAD ---
  if (u.pathname === '/api/prefetch' || u.pathname === '/api/preload') {
    const id = (u.searchParams.get('id') || '').trim();
    if (id) {
      const clean = id.replace(/^yt_/, '').trim();
      if (clean) fetchStreamUrl(clean).catch(() => {});
    }
    const ids = u.searchParams.get('ids')?.split(',').filter(Boolean) || [];
    ids.slice(0, 3).forEach(item => {
      const clean = item.replace(/^yt_/, '').trim();
      if (clean) fetchStreamUrl(clean).catch(() => {});
    });
    sendJson(res, 200, { success: true, preloading: Boolean(id || ids.length) });
    return true;
  }

  // --- LYRICS ---
  if (u.pathname === '/api/lyrics') {
    const rawTrack = (u.searchParams.get('track') || u.searchParams.get('title') || '').trim();
    const rawArtist = u.searchParams.get('artist')?.trim() || '';
    if (!rawTrack) {
      sendJson(res, 400, { success: false, error: 'track required' });
      return true;
    }

    const memKey = `${rawArtist.toLowerCase()}:::${rawTrack.toLowerCase()}`;
    const cachedEntry = lyricsMemCache.get(memKey);
    if (cachedEntry && cachedEntry.expires > Date.now()) {
      sendJson(res, 200, cachedEntry.payload);
      return true;
    }

    try {
      const normStr = s => (s || '').toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, '').replace(/\s+/g, ' ').trim();
      const stripJunk = s => (s || '')
        .replace(/\((?:official\s*(?:video|audio|music\s*video|lyric\s*video|clip|hd|hq)|audio|remix|official|video|clip|lyrics|visualizer|slowed|reverb)\)/gi, '')
        .replace(/\[(?:official\s*(?:video|audio|music\s*video|lyric\s*video|clip|hd|hq)|audio|remix|official|video|clip|lyrics|visualizer|slowed|reverb)\]/gi, '')
        .replace(/\s*--\s*|\s*—\s*|\s*–\s*/g, ' - ')
        .replace(/\s+/g, ' ')
        .trim();

      const cleanTrack = stripJunk(rawTrack);
      const cleanArtist = stripJunk(rawArtist);

      let extractedArtist = '';
      let extractedTitle = cleanTrack;
      if (cleanTrack.includes(' - ')) {
        const parts = cleanTrack.split(' - ');
        if (parts.length >= 2) {
          extractedArtist = parts[0].trim();
          extractedTitle = stripJunk(parts.slice(1).join(' - ').trim());
        }
      }

      const primaryArtist = a => (a || '').split(/,|&|\bfeat\.?|\bft\.?/i)[0].trim();
      const pExtractedArtist = primaryArtist(extractedArtist);
      const pCleanArtist = primaryArtist(cleanArtist);

      let plainFallback = null;
      const savePlain = d => {
        if (!plainFallback && d && typeof d === 'object' && d.plainLyrics && d.plainLyrics.trim().length > 0) {
          plainFallback = {
            success: true,
            lyrics: d.plainLyrics,
            syncedLyrics: null,
            plainLyrics: d.plainLyrics,
            track: d.trackName || rawTrack,
            artist: d.artistName || rawArtist,
          };
        }
      };

      const sendSynced = d => {
        const payload = {
          success: true,
          lyrics: d.syncedLyrics || d.plainLyrics || '',
          syncedLyrics: d.syncedLyrics || null,
          plainLyrics: d.plainLyrics || null,
          track: d.trackName || rawTrack,
          artist: d.artistName || rawArtist,
        };
        lyricsMemCache.set(memKey, { payload, expires: Date.now() + LYRICS_CACHE_TTL });
        sendJson(res, 200, payload);
      };

      const hasSynced = d => Boolean(d && typeof d === 'object' && !d.message && d.syncedLyrics && d.syncedLyrics.trim().length > 0);
      const isRateLimited = d => Boolean(d && typeof d === 'object' && (d.status === 429 || d.statusCode === 429 || (d.error && String(d.error).includes('429'))));

      // Attempt 1: Exact get with extracted artist and title
      if (pExtractedArtist && extractedTitle) {
        const url1 = `https://lrclib.net/api/get?track_name=${encodeURIComponent(extractedTitle)}&artist_name=${encodeURIComponent(pExtractedArtist)}`;
        const res1 = await httpGet(url1, 6000);
        if (hasSynced(res1)) {
          sendSynced(res1);
          return true;
        }
        if (isRateLimited(res1)) {
          const empty = { success: false, lyrics: '', syncedLyrics: null, plainLyrics: null, track: rawTrack, artist: rawArtist };
          sendJson(res, 200, empty);
          return true;
        }
        savePlain(res1);
      }

      // Attempt 2: Exact get with provided clean artist and track
      if (pCleanArtist && cleanTrack) {
        const url2 = `https://lrclib.net/api/get?track_name=${encodeURIComponent(cleanTrack)}&artist_name=${encodeURIComponent(pCleanArtist)}`;
        const res2 = await httpGet(url2, 6000);
        if (hasSynced(res2)) {
          sendSynced(res2);
          return true;
        }
        if (isRateLimited(res2)) {
          const empty = { success: false, lyrics: '', syncedLyrics: null, plainLyrics: null, track: rawTrack, artist: rawArtist };
          sendJson(res, 200, empty);
          return true;
        }
        savePlain(res2);
      }

      // Attempt 3: Structured search with extracted metadata
      const searchTrack = extractedTitle || cleanTrack;
      const searchArtist = pExtractedArtist || pCleanArtist;
      if (searchTrack) {
        const url3 = `https://lrclib.net/api/search?track_name=${encodeURIComponent(searchTrack)}${searchArtist ? '&artist_name=' + encodeURIComponent(searchArtist) : ''}`;
        const res3 = await httpGet(url3, 6000);
        if (Array.isArray(res3) && res3.length > 0) {
          const syncedMatch = res3.find(r => r && r.syncedLyrics && r.syncedLyrics.trim().length > 0);
          if (syncedMatch) {
            sendSynced(syncedMatch);
            return true;
          }
          res3.forEach(savePlain);
        } else if (isRateLimited(res3)) {
          const empty = { success: false, lyrics: '', syncedLyrics: null, plainLyrics: null, track: rawTrack, artist: rawArtist };
          sendJson(res, 200, empty);
          return true;
        }
      }

      // Attempt 4: Search query (prioritizing synced results)
      const primaryQuery = (pExtractedArtist && extractedTitle)
        ? `${pExtractedArtist} ${extractedTitle}`
        : ((pCleanArtist && cleanTrack) ? `${pCleanArtist} ${cleanTrack}` : searchTrack);

      if (primaryQuery) {
        const urlQ = `https://lrclib.net/api/search?q=${encodeURIComponent(primaryQuery)}`;
        const resQ = await httpGet(urlQ, 6000);
        if (Array.isArray(resQ) && resQ.length > 0) {
          const syncedMatchQ = resQ.find(r => r && r.syncedLyrics && r.syncedLyrics.trim().length > 0);
          if (syncedMatchQ) {
            sendSynced(syncedMatchQ);
            return true;
          }
          resQ.forEach(savePlain);
        }
      }

      // If no synced lyrics were found, return plain fallback if available
      if (plainFallback) {
        lyricsMemCache.set(memKey, { payload: plainFallback, expires: Date.now() + LYRICS_CACHE_TTL });
        sendJson(res, 200, plainFallback);
        return true;
      }

      const emptyRes = { success: false, lyrics: '', syncedLyrics: null, plainLyrics: null, track: rawTrack, artist: rawArtist };
      lyricsMemCache.set(memKey, { payload: emptyRes, expires: Date.now() + 60000 });
      sendJson(res, 200, emptyRes);
      return true;
    } catch (e) {
      const errRes = { success: false, lyrics: '', syncedLyrics: null, plainLyrics: null, track: rawTrack, artist: rawArtist };
      sendJson(res, 200, errRes);
      return true;
    }
  }

  // --- STREAM URL (returns direct URL for fast download) ---
  if (u.pathname === '/api/stream-url') {
    const id = u.searchParams.get('id')?.trim();
    if (!id) {
      sendJson(res, 400, { error: 'No id' });
      return true;
    }
    try {
      const streamUrl = await fetchStreamUrl(id);
      if (!streamUrl) {
        sendJson(res, 502, { error: 'No stream' });
        return true;
      }
      sendJson(res, 200, { url: streamUrl });
    } catch (e) {
      sendJson(res, 502, { error: e.message });
    }
    return true;
  }

  // --- PLAYLIST IMPORT (YouTube + Spotify) ---
  async function fetchFullUrl(rawUrl, timeout = 12000) {
    return new Promise((resolve) => {
      let current = rawUrl;
      if (!current.startsWith('http://') && !current.startsWith('https://')) {
        current = 'https://' + current;
      }
      function follow(u, hops = 0) {
        if (hops > 6) return resolve({ finalUrl: u, body: '' });
        let parsed;
        try { parsed = new URL(u); } catch { return resolve({ finalUrl: u, body: '' }); }
        const transport = parsed.protocol === 'https:' ? https : http;
        const timer = setTimeout(() => { req.destroy(); resolve({ finalUrl: u, body: '' }); }, timeout);
        const req = transport.get(u, {
          headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Accept-Language': 'ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
          }
        }, res => {
          if ([301, 302, 303, 307, 308].includes(res.statusCode) && res.headers.location) {
            clearTimeout(timer);
            const loc = res.headers.location.startsWith('http')
              ? res.headers.location
              : parsed.origin + res.headers.location;
            return follow(loc, hops + 1);
          }
          let data = '';
          res.on('data', c => (data += c));
          res.on('end', () => {
            clearTimeout(timer);
            resolve({ finalUrl: u, body: data });
          });
        });
        req.on('error', () => {
          clearTimeout(timer);
          resolve({ finalUrl: u, body: '' });
        });
      }
      follow(current);
    });
  }

  // --- SPOTIFY OAUTH & API HELPERS ---
  const SPOTIFY_SCOPES = 'user-library-read playlist-read-private playlist-read-collaborative';
  let spotifyAuth = { verifier: '', state: '', clientId: '', token: '' };

  function getSpotifyRedirectUri(port = 38472) {
    return `http://127.0.0.1:${port}/api/spotify-callback`;
  }

  function exchangeSpotifyCode(code, verifier, clientId, redirectUri) {
    return new Promise((resolve, reject) => {
      const postData = new URLSearchParams({
        grant_type: 'authorization_code',
        code,
        redirect_uri: redirectUri,
        client_id: clientId,
        code_verifier: verifier,
      }).toString();

      const req = https.request('https://accounts.spotify.com/api/token', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'Content-Length': Buffer.byteLength(postData),
        },
        timeout: 15000,
      }, (resp) => {
        let body = '';
        resp.on('data', c => { body += c; });
        resp.on('end', () => {
          try {
            const data = JSON.parse(body);
            resolve({ status: resp.statusCode, data });
          } catch (e) {
            resolve({ status: resp.statusCode, error: e });
          }
        });
      });
      req.on('error', reject);
      req.on('timeout', () => { req.destroy(); reject(new Error('Token exchange timed out')); });
      req.write(postData);
      req.end();
    });
  }

  function spotifyApiGet(requestUrl, token) {
    return new Promise((resolve, reject) => {
      const u = new URL(requestUrl);
      const req = https.get(u, {
        headers: {
          'Authorization': `Bearer ${token}`,
          'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
        },
        timeout: 15000,
      }, (resp) => {
        let body = '';
        resp.on('data', c => { body += c; });
        resp.on('end', () => {
          resolve({ status: resp.statusCode, headers: resp.headers, body });
        });
      });
      req.on('error', reject);
      req.on('timeout', () => { req.destroy(); reject(new Error('Spotify API timed out')); });
    });
  }

  async function spotifyGetWithRetry(requestUrl, token) {
    let resp = await spotifyApiGet(requestUrl, token);
    if (resp.status === 429) {
      const retryAfter = parseInt(resp.headers['retry-after'] || '1', 10);
      await new Promise(r => setTimeout(r, Math.max(1, retryAfter) * 1000));
      resp = await spotifyApiGet(requestUrl, token);
    }
    return resp;
  }

  async function handleSpotifyLibrary(req, res, searchParams) {
    if (!spotifyAuth.token) {
      sendJson(res, 401, { success: false, error: 'Unauthorized' });
      return;
    }

    const wantLikes = searchParams.get('likes') === '1' || searchParams.get('likes') === 'true';
    const wantPlaylists = searchParams.get('playlists') === '1' || searchParams.get('playlists') === 'true';

    let likes = [];
    let playlists = [];

    try {
      if (wantLikes) {
        let nextUrl = 'https://api.spotify.com/v1/me/tracks?limit=50';
        while (nextUrl && likes.length < 5000) {
          const resp = await spotifyGetWithRetry(nextUrl, spotifyAuth.token);
          if (resp.status === 403) throw new Error('SPOTIFY_403');
          if (resp.status < 200 || resp.status >= 300) break;
          let data = {};
          try { data = JSON.parse(resp.body); } catch (e) { break; }
          const itms = Array.isArray(data.items) ? data.items : [];
          for (const it of itms) {
            const t = it?.item || it?.track;
            if (!t || t.is_local || t.type === 'episode') continue;
            const artist = (Array.isArray(t.artists) && t.artists[0]?.name) || '';
            const title = t.name || '';
            const duration = t.duration_ms ? Math.round(t.duration_ms / 1000) : 0;
            if (title) likes.push({ title, artist, duration });
            if (likes.length >= 5000) break;
          }
          nextUrl = data.next || null;
        }
      }

      if (wantPlaylists) {
        let userPlaylists = [];
        let plNext = 'https://api.spotify.com/v1/me/playlists?limit=50';
        while (plNext) {
          const resp = await spotifyGetWithRetry(plNext, spotifyAuth.token);
          if (resp.status === 403) throw new Error('SPOTIFY_403');
          if (resp.status < 200 || resp.status >= 300) break;
          let data = {};
          try { data = JSON.parse(resp.body); } catch (e) { break; }
          const itms = Array.isArray(data.items) ? data.items : [];
          for (const pl of itms) {
            if (pl && pl.id && pl.name) {
              userPlaylists.push({ id: pl.id, name: pl.name });
            }
          }
          plNext = data.next || null;
        }

        for (const pl of userPlaylists) {
          let plTracks = [];
          let itemUrl = `https://api.spotify.com/v1/playlists/${encodeURIComponent(pl.id)}/tracks?limit=50`;
          let resp = await spotifyGetWithRetry(itemUrl, spotifyAuth.token);
          if (resp.status >= 200 && resp.status < 300) {
            let pageData = {};
            try { pageData = JSON.parse(resp.body); } catch (e) {}
            const pageItems = Array.isArray(pageData.items) ? pageData.items : [];
            for (const it of pageItems) {
              const t = it?.item || it?.track;
              if (!t || t.is_local || t.type === 'episode') continue;
              const artist = (Array.isArray(t.artists) && t.artists[0]?.name) || '';
              const title = t.name || '';
              const duration = t.duration_ms ? Math.round(t.duration_ms / 1000) : 0;
              if (title) plTracks.push({ title, artist, duration });
            }
          }
          playlists.push({ name: pl.name, tracks: plTracks });
        }
      }

      sendJson(res, 200, { success: true, likes, playlists });
    } catch (err) {
      console.warn('[spotify-library] Error:', err.message);
      sendJson(res, 500, { success: false, error: err.message || 'Ошибка загрузки библиотеки Spotify' });
    }
  }

  // --- SPOTIFY PROFILE SCRAPER ---
  function fetchSpotifyUserProfile(input) {
    const raw = String(input || '').trim().split('?')[0].split('#')[0];
    const m = raw.match(/user[/:]([a-zA-Z0-9_\-\.]+)/i);
    const userId = m ? m[1] : raw.replace(/^@/, '').replace(/^https?:\/\/[^\/]+\//, '');
    if (!userId) return Promise.reject(new Error('Некорректный ID пользователя Spotify'));

    const profileUrl = `https://open.spotify.com/user/${encodeURIComponent(userId)}`;
    return new Promise((resolve, reject) => {
      const httpsMod = require('https');
      const req = httpsMod.get(profileUrl, {
        headers: {
          'User-Agent': 'Twitterbot/1.0',
          'Accept-Language': 'ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7',
          'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
          'Cache-Control': 'no-cache',
        },
        timeout: 10000,
      }, (resp) => {
        if (resp.statusCode === 404) {
          return reject(new Error('Пользователь Spotify не найден (404)'));
        }
        let html = '';
        resp.on('data', chunk => { html += chunk; });
        resp.on('end', () => {
          const titleMatch = html.match(/property=[\"']og:title[\"']\s+content=[\"'](.*?)[\"']/i) || 
                             html.match(/<title>([^<]+)<\/title>/i);
          const imageMatch = html.match(/property=[\"']og:image[\"']\s+content=[\"'](.*?)[\"']/i);
          const descMatch = html.match(/property=[\"']og:description[\"']\s+content=[\"'](.*?)[\"']/i);

          let name = titleMatch ? titleMatch[1].replace(/ \| Spotify$/i, '').replace(/ on Spotify$/i, '').trim() : userId;
          if (name === 'Spotify – Web Player' || name === 'Spotify - Web Player') {
            name = userId;
          }
          const avatar = imageMatch ? imageMatch[1] : '';
          let description = descMatch ? descMatch[1].trim() : '';
          if (description === 'User · Spotify' || description === 'Пользователь · Spotify') {
            description = '';
          }

          resolve({
            id: userId,
            name: name || userId,
            displayName: name || userId,
            handle: userId,
            avatar: avatar || '',
            about: description || '',
            description: description || '',
            url: profileUrl,
          });
        });
      });
      req.on('error', err => reject(new Error(`Ошибка подключения к Spotify: ${err.message}`)));
      req.on('timeout', () => {
        req.destroy();
        reject(new Error('Время ожидания ответа от Spotify истекло'));
      });
    });
  }

  // --- PLAYLIST EXTRACTION & STREAMING RESOLUTION HELPERS ---
  async function extractTracksFromUrl(url) {
    const rawUrl = String(url || '').trim();
    if (!rawUrl) throw new Error('URL не указан');

    // 1. SPOTIFY (Playlist / Album / Track / Artist / User Profile)
    if (rawUrl.includes('spotify.com') || rawUrl.startsWith('spotify:')) {
      const playlistId = rawUrl.match(/playlist\/([a-zA-Z0-9]+)/)?.[1] || rawUrl.match(/spotify:playlist:([a-zA-Z0-9]+)/)?.[1];
      const albumId = rawUrl.match(/album\/([a-zA-Z0-9]+)/)?.[1] || rawUrl.match(/spotify:album:([a-zA-Z0-9]+)/)?.[1];
      const trackId = rawUrl.match(/track\/([a-zA-Z0-9]+)/)?.[1] || rawUrl.match(/spotify:track:([a-zA-Z0-9]+)/)?.[1];
      const artistId = rawUrl.match(/artist\/([a-zA-Z0-9]+)/)?.[1] || rawUrl.match(/spotify:artist:([a-zA-Z0-9]+)/)?.[1];
      const userId = rawUrl.match(/user\/([a-zA-Z0-9_\-\.]+)/)?.[1] || rawUrl.match(/spotify:user:([a-zA-Z0-9_\-\.]+)/)?.[1];

      let playlistName = 'Spotify импорт';
      let playlistCover = '';
      const items = [];

      if (playlistId || albumId) {
        const targetType = playlistId ? 'playlist' : 'album';
        const targetId = playlistId || albumId;
        const embedUrl = `https://open.spotify.com/embed/${targetType}/${targetId}`;

        // Attempt oEmbed to instantly grab accurate playlist title & cover
        try {
          const oeRes = await fetch(`https://open.spotify.com/oembed?url=https://open.spotify.com/${targetType}/${targetId}`, { signal: AbortSignal.timeout(5000) });
          if (oeRes.ok) {
            const oeData = await oeRes.json();
            if (oeData.title) playlistName = oeData.title;
            if (oeData.thumbnail_url) playlistCover = oeData.thumbnail_url;
          }
        } catch (_) {}

        try {
          const html = await httpGet(embedUrl, 12000);
          const titleMatch = html.match(/<title>([^<]+)<\/title>/i);
          if (titleMatch && titleMatch[1] && (!playlistName || playlistName === 'Spotify импорт')) {
            playlistName = titleMatch[1].replace(/ \| Spotify$/i, '').replace(/^Spotify - /i, '').trim();
          }
          const jsonMatch = html.match(/<script[^>]*id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
          if (jsonMatch) {
            const nextData = JSON.parse(jsonMatch[1]);
            const entity = nextData?.props?.pageProps?.state?.data?.entity || nextData?.props?.pageProps?.state?.data?.playlist || {};
            if (entity.name || entity.title) playlistName = entity.name || entity.title;
            if (entity.coverArt?.sources?.[0]?.url) playlistCover = entity.coverArt.sources[0].url;

            const rawList = entity.trackList || nextData?.props?.pageProps?.tracks?.items || nextData?.props?.pageProps?.data?.playlist?.trackList || [];
            for (const it of rawList) {
              const trk = it.track || it;
              const title = trk.title || trk.name || '';
              const artist = trk.subtitle || trk.artists?.map(a => a.name).join(', ') || '';
              const itemCover = (trk.coverArt?.sources?.[0]?.url && trk.coverArt.sources[0].url !== playlistCover)
                ? trk.coverArt.sources[0].url
                : (trk.album?.images?.[0]?.url && trk.album.images[0].url !== playlistCover)
                ? trk.album.images[0].url
                : '';
              const duration = trk.duration ? trk.duration / (trk.duration > 1000 ? 1000 : 1) : 0;
              if (title) items.push({ title, artist, cover: itemCover, duration });
            }

            // Fallback: deep walk if trackList was organized differently in nextData
            if (items.length === 0) {
              const seen = new Set();
              function walk(node) {
                if (!node || typeof node !== 'object') return;
                if (Array.isArray(node)) { for (const itm of node) walk(itm); return; }
                const uri = typeof node.uri === 'string' ? node.uri : '';
                const title = node.title || node.name;
                const subtitle = node.subtitle || node.artist || (Array.isArray(node.artists) ? node.artists.map(a => typeof a === 'string' ? a : (a.name || a.profile?.name)).filter(Boolean).join(', ') : (node.albumOfTrack?.artists?.items || []).map(a => a.profile?.name).filter(Boolean).join(', '));
                const duration = node.duration?.totalMilliseconds || node.trackDuration?.totalMilliseconds || node.duration_ms || (typeof node.duration === 'number' ? node.duration : 0);
                if (title && typeof title === 'string' && (subtitle || uri.startsWith('spotify:track:')) && (uri.startsWith('spotify:track:') || node.entityType === 'track' || duration)) {
                  const key = `${title}__${subtitle || ''}`;
                  if (!seen.has(key)) {
                    seen.add(key);
                    const durSec = typeof duration === 'number' ? (duration > 1000 ? Math.round(duration / 1000) : Math.round(duration)) : 0;
                    items.push({ title: title.trim(), artist: typeof subtitle === 'string' ? subtitle.trim() : '', cover: playlistCover, duration: durSec });
                  }
                  return;
                }
                for (const k of Object.keys(node)) walk(node[k]);
              }
              walk(nextData);
            }
          }
        } catch (e) {
          console.warn('[spotify] Embed parse failed:', e.message);
        }

        if (items.length === 0) {
          try {
            const mainUrl = `https://open.spotify.com/${targetType}/${targetId}`;
            const mainHtml = await httpGet(mainUrl, 12000);
            const mainMatch = mainHtml.match(/<script[^>]*id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
            if (mainMatch) {
              const mainData = JSON.parse(mainMatch[1]);
              const pData = mainData?.props?.pageProps?.playlist || mainData?.props?.pageProps?.state?.data?.playlist || {};
              if (pData.name) playlistName = pData.name;
              const rawItems = pData?.tracks?.items || [];
              for (const it of rawItems) {
                const trk = it.track || it;
                const title = trk.name || trk.title || '';
                const artist = trk.artists?.map(a => a.name).join(', ') || trk.subtitle || '';
                const itemCover = (trk.album?.images?.[0]?.url && trk.album.images[0].url !== playlistCover)
                  ? trk.album.images[0].url
                  : (trk.coverArt?.sources?.[0]?.url && trk.coverArt.sources[0].url !== playlistCover)
                  ? trk.coverArt.sources[0].url
                  : '';
                const duration = (trk.duration_ms || trk.duration || 0) / 1000;
                if (title && !items.find(x => x.title === title && x.artist === artist)) {
                  items.push({ title, artist, cover: itemCover, duration });
                }
              }
            }
          } catch (e) {
            console.warn('[spotify] Main page parse failed:', e.message);
          }
        }
      } else if (trackId) {
        try {
          try {
            const oeRes = await fetch(`https://open.spotify.com/oembed?url=https://open.spotify.com/track/${trackId}`, { signal: AbortSignal.timeout(5000) });
            if (oeRes.ok) {
              const oeData = await oeRes.json();
              if (oeData.title) {
                playlistName = oeData.title;
                playlistCover = oeData.thumbnail_url || '';
                items.push({ title: oeData.title, artist: oeData.author_name || '', cover: playlistCover, duration: 0 });
              }
            }
          } catch (_) {}

          if (items.length === 0) {
            const html = await httpGet(`https://open.spotify.com/embed/track/${trackId}`, 10000);
            const jsonMatch = html.match(/<script[^>]*id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
            if (jsonMatch) {
              const nextData = JSON.parse(jsonMatch[1]);
              const entity = nextData?.props?.pageProps?.state?.data?.entity || {};
              const title = entity.title || entity.name || 'Трек Spotify';
              const artist = entity.subtitle || entity.artists?.map(a => a.name).join(', ') || '';
              const cover = entity.coverArt?.sources?.[0]?.url || playlistCover || '';
              playlistName = `${artist} - ${title}`;
              items.push({ title, artist, cover, duration: 0 });
            }
          }
        } catch (e) {}
      } else if (artistId) {
        try {
          const oeRes = await fetch(`https://open.spotify.com/oembed?url=https://open.spotify.com/artist/${artistId}`, { signal: AbortSignal.timeout(5000) });
          if (oeRes.ok) {
            const oeData = await oeRes.json();
            if (oeData.title) {
              playlistName = `${oeData.title} — Топ треки`;
              playlistCover = oeData.thumbnail_url || '';
            }
          }
          const html = await httpGet(`https://open.spotify.com/embed/artist/${artistId}`, 10000);
          const jsonMatch = html.match(/<script[^>]*id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
          if (jsonMatch) {
            const nextData = JSON.parse(jsonMatch[1]);
            const rawList = nextData?.props?.pageProps?.state?.data?.entity?.trackList || nextData?.props?.pageProps?.tracks?.items || [];
            for (const it of rawList) {
              const trk = it.track || it;
              const title = trk.title || trk.name || '';
              const artist = trk.subtitle || trk.artists?.map(a => a.name).join(', ') || '';
              const itemCover = trk.coverArt?.sources?.[0]?.url || playlistCover;
              const duration = trk.duration ? trk.duration / (trk.duration > 1000 ? 1000 : 1) : 0;
              if (title) items.push({ title, artist, cover: itemCover, duration });
            }
          }
        } catch (e) {}
      } else if (userId) {
        try {
          const profile = await fetchSpotifyUserProfile(userId);
          playlistName = `${profile.name || userId} — Spotify Профиль`;
          playlistCover = profile.avatar || '';

          // Search for curated playlists or top tracks for this user
          const query = profile.name || userId;
          try {
            const dzRes = await fetch(`https://api.deezer.com/search/playlist?q=${encodeURIComponent(query)}&limit=3`, {
              signal: AbortSignal.timeout(6000),
            });
            if (dzRes.ok) {
              const dzData = await dzRes.json();
              if (dzData.data && dzData.data[0]) {
                const plId = dzData.data[0].id;
                const tracksRes = await fetch(`https://api.deezer.com/playlist/${plId}/tracks?limit=50`, {
                  signal: AbortSignal.timeout(6000),
                });
                if (tracksRes.ok) {
                  const tData = await tracksRes.json();
                  for (const trk of (tData.data || [])) {
                    items.push({
                      title: trk.title,
                      artist: trk.artist?.name || '',
                      cover: trk.album?.cover_medium || playlistCover,
                      duration: trk.duration || 0,
                    });
                  }
                }
              }
            }
          } catch (e) {}

          if (items.length === 0) {
            const ytTracks = await searchTracks(query, 25, false);
            for (const yt of (ytTracks || [])) {
              items.push({
                title: yt.title,
                artist: yt.artist || query,
                cover: yt.cover || playlistCover,
                duration: yt.duration || 0,
              });
            }
          }
        } catch (e) {
          console.warn('[spotify-profile] Profile import error:', e.message);
        }
      }

      if (items.length === 0) throw new Error('Не удалось извлечь треки из Spotify ссылки');
      return { name: playlistName, cover: playlistCover, items, isDirect: false };
    }

    // 2. SOUNDCLOUD
    if (rawUrl.includes('soundcloud.com')) {
      try {
        const result = await scImportPlaylist(rawUrl);
        if (result && Array.isArray(result.tracks) && result.tracks.length > 0) {
          return {
            name: result.name || 'SoundCloud импорт',
            cover: result.tracks[0]?.cover || '',
            items: result.tracks,
            isDirect: true,
          };
        }
      } catch (e) {
        console.warn('[soundcloud] Import fallback:', e.message);
      }
    }

    // 3. APPLE MUSIC (Playlist / Album)
    if (rawUrl.includes('music.apple.com') || rawUrl.includes('itunes.apple.com')) {
      const albumMatch = rawUrl.match(/album\/(?:[^/]+\/)?(\d+)/i) || rawUrl.match(/[?&]id=(\d+)/i);
      if (albumMatch) {
        try {
          const itunesResp = await fetch(`https://itunes.apple.com/lookup?id=${albumMatch[1]}&entity=song`, { signal: AbortSignal.timeout(8000) });
          if (itunesResp.ok) {
            const data = await itunesResp.json();
            const results = (data && Array.isArray(data.results)) ? data.results : [];
            const albumItem = results.find(r => r && r.wrapperType === 'collection');
            if (albumItem) {
              const tracks = results
                .filter(r => r && r.wrapperType === 'track')
                .map(item => ({
                  title: item.trackName || '',
                  artist: item.artistName || albumItem.artistName || '',
                  duration: item.trackTimeMillis ? Math.round(item.trackTimeMillis / 1000) : 0,
                  cover: (item.artworkUrl100 || albumItem.artworkUrl100 || '').replace('100x100', '600x600'),
                }));
              if (tracks.length > 0) {
                return {
                  name: albumItem.collectionName || 'Apple Music альбом',
                  cover: (albumItem.artworkUrl100 || '').replace('100x100', '600x600'),
                  items: tracks,
                  isDirect: false,
                };
              }
            }
          }
        } catch (e) {
          console.warn('[apple] iTunes lookup failed:', e.message);
        }
      }

      try {
        const html = await httpGet(rawUrl, 12000);
        let playlistName = 'Apple Music импорт';
        let playlistCover = '';
        const items = [];

        const titleMatch = html.match(/<title>([^<]+)<\/title>/i);
        if (titleMatch && titleMatch[1]) {
          playlistName = titleMatch[1].replace(/ – Apple Music$/i, '').replace(/ on Apple Music$/i, '').trim();
        }

        const ldMatch = html.match(/<script\s+id=["']schema:music-(?:playlist|album)["']\s+type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/i) ||
                        html.match(/<script\s+type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/i);
        if (ldMatch) {
          try {
            const ld = JSON.parse(ldMatch[1]);
            if (ld.name) playlistName = ld.name;
            if (ld.image) playlistCover = typeof ld.image === 'string' ? ld.image : (ld.image.url || '');
            const trackList = ld.track?.itemListElement || ld.tracks || [];
            for (const t of trackList) {
              const it = t.item || t;
              const title = it.name || '';
              const artist = it.byArtist?.name || (Array.isArray(it.byArtist) ? it.byArtist.map(a => a.name).join(', ') : '');
              if (title) items.push({ title, artist, duration: 0 });
            }
          } catch (e) {}
        }

        if (items.length > 0) {
          return { name: playlistName, cover: playlistCover, items, isDirect: false };
        }
      } catch (e) {
        console.warn('[apple] HTML parse failed:', e.message);
      }
    }

    // 4. YOUTUBE & YOUTUBE MUSIC (Default / yt-dlp)
    try {
      const localYtdlpPath = process.env.YT_DLP_PATH || findYtDlp();
      const proc = spawn(
        localYtdlpPath,
        [
          '--no-check-certificates',
          '--no-warnings',
          '--quiet',
          '--flat-playlist',
          '--print',
          '%(playlist_title)s\t%(id)s\t%(title)s\t%(duration)s\t%(thumbnail)s\t%(uploader)s',
          '--playlist-end',
          '200',
          rawUrl,
        ],
        { stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true }
      );
      const result = await new Promise((resolve, reject) => {
        let out = '';
        proc.stdout.on('data', d => { out += d; });
        proc.stderr.on('data', d => { console.error('[playlist]', d.toString().trim()); });
        proc.on('error', reject);
        proc.on('close', code => {
          if (out.trim()) resolve(out.trim());
          else reject(new Error('Код завершения ' + code));
        });
        setTimeout(() => {
          proc.kill();
          reject(new Error('Таймаут загрузки плейлиста'));
        }, 60000);
      });

      let detectedTitle = 'YouTube импорт';
      const tracks = [];
      for (const line of result.split('\n')) {
        if (!line.trim()) continue;
        const parts = line.split('\t');
        let plTitle = '', id = '', title = '', duration = 0, thumbnail = '', uploader = '';
        if (parts.length >= 6) {
          [plTitle, id, title, duration, thumbnail, uploader] = parts;
        } else if (parts.length >= 4) {
          [id, title, duration, thumbnail] = parts;
        }
        if (plTitle && plTitle !== 'NA') detectedTitle = plTitle.trim();
        if (!id || id === 'NA') continue;
        const cleanId = id.trim();
        // Guarantee unique video cover per YouTube track rather than shared playlist thumbnail
        let trackCover = `https://img.youtube.com/vi/${cleanId}/hqdefault.jpg`;
        if (thumbnail && thumbnail !== 'NA' && thumbnail.includes(cleanId)) {
          trackCover = thumbnail.trim();
        }
        tracks.push({
          id: cleanId,
          title: (title || '').trim() || 'Трек',
          artist: (uploader && uploader !== 'NA') ? uploader.trim() : '',
          duration: parseInt(duration) || 0,
          cover: trackCover,
        });
      }

      if (tracks.length === 0) throw new Error('В плейлисте не найдено треков');
      return { name: detectedTitle, cover: tracks[0]?.cover || '', items: tracks, isDirect: true };
    } catch (e) {
      throw new Error(`Ошибка извлечения плейлиста: ${e.message}`);
    }
  }

  // --- STREAMING PLAYLIST IMPORT ENDPOINT (SSE) ---
  if (u.pathname === '/api/playlist/stream') {
    const url = u.searchParams.get('url')?.trim();
    if (!url) {
      sendJson(res, 400, { error: 'Не указан URL плейлиста' });
      return true;
    }

    res.writeHead(200, {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      'Connection': 'keep-alive',
      'X-Accel-Buffering': 'no',
    });

    const sendEvent = (event, data) => {
      try {
        res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
      } catch (e) {}
    };

    try {
      sendEvent('status', { step: 'extracting', message: 'Анализ ссылки и получение треков...' });
      const { name, cover, items, isDirect } = await extractTracksFromUrl(url);

      sendEvent('metadata', { name, cover, total: items.length, isDirect });

      if (isDirect) {
        // Direct YouTube / SoundCloud items with IDs already resolved
        const resolved = [];
        for (let i = 0; i < items.length; i++) {
          const item = items[i];
          resolved.push(item);
          sendEvent('progress', {
            current: i + 1,
            total: items.length,
            percent: Math.round(((i + 1) / items.length) * 100),
            track: item,
          });
        }
        sendEvent('complete', { name, total: resolved.length, tracks: resolved });
        res.end();
        return true;
      }

      // External metadata (Spotify, Yandex, Apple Music) — resolve audio on YouTube
      const resolved = [];
      const BATCH = 4;
      for (let i = 0; i < items.length; i += BATCH) {
        const batch = items.slice(i, i + BATCH);
        const batchResults = await Promise.allSettled(
          batch.map(async trk => {
            const query = trk.artist ? `${trk.artist} - ${trk.title}` : trk.title;
            const ytResults = await searchTracks(query, 1, false);
            if (ytResults && ytResults.length > 0) {
              const yt = ytResults[0];
              const uniqueCover = yt.cover || `https://img.youtube.com/vi/${yt.id}/hqdefault.jpg`;
              const trackCover = (trk.cover && trk.cover !== cover && !trk.cover.includes('default_playlist'))
                ? trk.cover
                : uniqueCover;
              return {
                id: yt.id,
                title: trk.title || yt.title,
                artist: trk.artist || yt.artist || '',
                duration: yt.duration || trk.duration || 0,
                cover: trackCover,
              };
            }
            return null;
          })
        );

        for (let j = 0; j < batchResults.length; j++) {
          const r = batchResults[j];
          const currIdx = i + j + 1;
          const trkObj = r.status === 'fulfilled' && r.value ? r.value : null;
          if (trkObj) resolved.push(trkObj);

          sendEvent('progress', {
            current: currIdx,
            total: items.length,
            percent: Math.round((currIdx / items.length) * 100),
            track: trkObj || batch[j],
          });
        }
      }

      sendEvent('complete', { name, total: resolved.length, tracks: resolved });
      res.end();
      return true;
    } catch (e) {
      console.error('[import-stream] Error:', e.message);
      sendEvent('error', { error: e.message || 'Ошибка импорта' });
      res.end();
      return true;
    }
  }

  // --- BATCH RESOLVE FROM TEXT / FILE ITEMS (SSE) ---
  if (u.pathname === '/api/playlist/resolve-stream' && req.method === 'POST') {
    let payload;
    try {
      payload = await parseBody(req);
    } catch (e) {
      sendJson(res, 400, { error: 'Неверное тело запроса' });
      return true;
    }

    const playlistName = payload?.name || 'Импортированный список';
    const items = Array.isArray(payload?.items) ? payload.items : [];

    if (items.length === 0) {
      sendJson(res, 400, { error: 'Список треков пуст' });
      return true;
    }

    res.writeHead(200, {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      'Connection': 'keep-alive',
      'X-Accel-Buffering': 'no',
    });

    const sendEvent = (event, data) => {
      try {
        res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
      } catch (e) {}
    };

    sendEvent('metadata', { name: playlistName, total: items.length });

    const resolved = [];
    const BATCH = 4;
    for (let i = 0; i < items.length; i += BATCH) {
      const batch = items.slice(i, i + BATCH);
      const batchResults = await Promise.allSettled(
        batch.map(async trk => {
          const query = trk.artist ? `${trk.artist} - ${trk.title}` : (trk.title || String(trk));
          const ytResults = await searchTracks(query, 1, false);
          if (ytResults && ytResults.length > 0) {
            const yt = ytResults[0];
            const uniqueCover = yt.cover || `https://img.youtube.com/vi/${yt.id}/hqdefault.jpg`;
            const trackCover = trk.cover || uniqueCover;
            return {
              id: yt.id,
              title: trk.title || yt.title,
              artist: trk.artist || yt.artist || '',
              duration: yt.duration || trk.duration || 0,
              cover: trackCover,
            };
          }
          return null;
        })
      );

      for (let j = 0; j < batchResults.length; j++) {
        const r = batchResults[j];
        const currIdx = i + j + 1;
        const trkObj = r.status === 'fulfilled' && r.value ? r.value : null;
        if (trkObj) resolved.push(trkObj);

        sendEvent('progress', {
          current: currIdx,
          total: items.length,
          percent: Math.round((currIdx / items.length) * 100),
          track: trkObj || batch[j],
        });
      }
    }

    sendEvent('complete', { name: playlistName, total: resolved.length, tracks: resolved });
    res.end();
    return true;
  }

  // --- FALLBACK PLAYLIST ENDPOINT ---
  if (u.pathname === '/api/playlist') {
    const url = u.searchParams.get('url')?.trim();
    if (!url) {
      sendJson(res, 400, { error: 'Playlist URL required' });
      return true;
    }
    try {
      const { name, cover, items, isDirect } = await extractTracksFromUrl(url);
      if (isDirect) {
        sendJson(res, 200, { name, cover, tracks: items });
        return true;
      }
      const results = [];
      const BATCH = 5;
      for (let i = 0; i < items.length; i += BATCH) {
        const batch = items.slice(i, i + BATCH);
        const batchResults = await Promise.allSettled(
          batch.map(async trk => {
            const query = trk.artist ? `${trk.artist} - ${trk.title}` : trk.title;
            const ytResults = await searchTracks(query, 1, false);
            if (ytResults && ytResults.length > 0) {
              const yt = ytResults[0];
              const uniqueCover = yt.cover || `https://img.youtube.com/vi/${yt.id}/hqdefault.jpg`;
              const trackCover = (trk.cover && trk.cover !== cover) ? trk.cover : uniqueCover;
              return {
                id: yt.id,
                title: trk.title || yt.title,
                artist: trk.artist || yt.artist || '',
                duration: yt.duration || trk.duration || 0,
                cover: trackCover,
              };
            }
            return null;
          })
        );
        for (const r of batchResults) {
          if (r.status === 'fulfilled' && r.value) results.push(r.value);
        }
      }
      sendJson(res, 200, { name, cover, tracks: results });
      return true;
    } catch (e) {
      console.error('[playlist] Error:', e.message);
      sendJson(res, 502, { error: e.message || 'Ошибка загрузки плейлиста' });
      return true;
    }
  }

  // --- SOUNDCLOUD IMPORT (LEGACY) ---
  if (u.pathname === '/api/soundcloud/import') {
    const url = u.searchParams.get('url')?.trim();
    if (!url) {
      sendJson(res, 400, { error: 'URL required' });
      return true;
    }
    try {
      const result = await scImportPlaylist(url);
      if (result.error) {
        sendJson(res, 502, { error: result.error });
        return true;
      }
      sendJson(res, 200, { name: result.name, tracks: result.tracks });
      return true;
    } catch (e) {
      console.error('[soundcloud] Import error:', e.message);
      sendJson(res, 502, { error: 'Failed to import: ' + e.message });
      return true;
    }
  }

  // --- AUDIO OUTPUT & BLUETOOTH BATTERY INFO ---
  if (u.pathname === '/api/audio-output-info' && req.method === 'GET') {
    const info = await getSystemAudioOutputInfo();
    sendJson(res, 200, info);
    return true;
  }

  // --- LYRICS TRANSLATION (FREE GOOGLE TRANSLATE) ---
  if (u.pathname === '/api/translate' && req.method === 'POST') {
    let payload;
    try {
      payload = await parseBody(req);
    } catch (e) {
      sendJson(res, 400, { error: 'Invalid JSON body' });
      return true;
    }
    const lines = Array.isArray(payload?.texts) ? payload.texts : (Array.isArray(payload?.lines) ? payload.lines : []);
    const targetLang = payload?.targetLang || payload?.to || 'ru';
    if (!lines.length) {
      sendJson(res, 200, { success: true, translations: [] });
      return true;
    }
    try {
      const translations = await translateTexts(lines, targetLang);
      sendJson(res, 200, { success: true, translations });
    } catch (e) {
      sendJson(res, 500, { error: e.message || 'Translation failed' });
    }
    return true;
  }

  // --- DOWNLOAD AUDIO TRACK (MP3 / FLAC) ---
  if (u.pathname === '/api/download') {
    const id = u.searchParams.get('id')?.trim() || '';
    let trackUrl = u.searchParams.get('url')?.trim() || '';
    if (!trackUrl || trackUrl.startsWith('/') || trackUrl.includes('127.0.0.1') || trackUrl.includes('localhost')) {
      const cleanId = id.replace(/^yt-/, '');
      if (cleanId && !cleanId.startsWith('dz-') && !cleanId.startsWith('local-')) {
        trackUrl = cleanId.startsWith('http') ? cleanId : `https://www.youtube.com/watch?v=${cleanId}`;
      } else {
        trackUrl = '';
      }
    }
    const title = u.searchParams.get('title')?.trim() || 'Track';
    const artist = u.searchParams.get('artist')?.trim() || 'Unknown';
    const format = (u.searchParams.get('format') || 'mp3').toLowerCase() === 'flac' ? 'flac' : 'mp3';

    if (!trackUrl) {
      if (title || artist) {
        trackUrl = `ytsearch1:${artist} - ${title}`;
      } else if (id) {
        trackUrl = `https://www.youtube.com/watch?v=${id}`;
      } else {
        sendJson(res, 400, { error: 'Track ID, URL, or Title required' });
        return true;
      }
    }

    const safeTitle = `${artist} - ${title}`.replace(/[<>:"/\\|?*\x00-\x1F]/g, '_').trim();
    const filename = `${safeTitle}.${format}`;
    const localYtdlp = process.env.YT_DLP_PATH || findYtDlp();
    const tmpOut = path.join(os.tmpdir(), `votify-dl-${Date.now()}-${Math.random().toString(36).slice(2, 6)}.${format}`);

    try {
      const proc = spawn(
        localYtdlp,
        [
          '--no-check-certificates',
          '--no-warnings',
          '--quiet',
          '-x',
          '--audio-format', format,
          '--audio-quality', '0',
          '--add-metadata',
          '--embed-thumbnail',
          '-o', tmpOut,
          trackUrl
        ],
        { stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true }
      );

      await new Promise((resolve, reject) => {
        let errOut = '';
        proc.stderr.on('data', d => { errOut += d.toString(); });
        proc.on('error', reject);
        proc.on('close', code => {
          if (code === 0 && fs.existsSync(tmpOut)) resolve();
          else reject(new Error(errOut || `yt-dlp exited with code ${code}`));
        });
      });

      if (u.searchParams.get('save') === '1' || u.searchParams.get('direct') === '1') {
        const downloadsDir = path.join(os.homedir(), 'Downloads');
        if (!fs.existsSync(downloadsDir)) fs.mkdirSync(downloadsDir, { recursive: true });
        const targetPath = path.join(downloadsDir, filename);
        fs.copyFileSync(tmpOut, targetPath);
        try { fs.unlinkSync(tmpOut); } catch (e) {}
        sendJson(res, 200, { success: true, saved: true, path: targetPath, filename });
        return true;
      }

      const stat = fs.statSync(tmpOut);
      res.writeHead(200, {
        'Content-Type': format === 'flac' ? 'audio/flac' : 'audio/mpeg',
        'Content-Length': stat.size,
        'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(filename)}`,
      });
      const stream = fs.createReadStream(tmpOut);
      stream.pipe(res);
      stream.on('end', () => {
        try { fs.unlinkSync(tmpOut); } catch (e) {}
      });
      stream.on('error', () => {
        try { fs.unlinkSync(tmpOut); } catch (e) {}
      });
      return true;
    } catch (err) {
      console.error('[download] Error:', err.message);
      try { if (fs.existsSync(tmpOut)) fs.unlinkSync(tmpOut); } catch (e) {}
      sendJson(res, 502, { error: 'Failed to download track: ' + err.message });
      return true;
    }
  }

  // --- MULTI-PLATFORM PLAYLIST IMPORT ALIAS ---
  if (u.pathname === '/api/playlist-import') {
    const url = u.searchParams.get('url')?.trim();
    if (!url) {
      sendJson(res, 400, { error: 'Playlist URL required' });
      return true;
    }
    try {
      const { name, cover, items, isDirect } = await extractTracksFromUrl(url);
      if (isDirect) {
        sendJson(res, 200, { name, cover, tracks: items });
        return true;
      }
      const results = [];
      const BATCH = 4;
      for (let i = 0; i < items.length; i += BATCH) {
        const batch = items.slice(i, i + BATCH);
        const batchResults = await Promise.allSettled(
          batch.map(async trk => {
            const query = trk.artist ? `${trk.artist} - ${trk.title}` : trk.title;
            const ytResults = await searchTracks(query, 1, false);
            if (ytResults && ytResults.length > 0) {
              const yt = ytResults[0];
              const uniqueCover = yt.cover || `https://img.youtube.com/vi/${yt.id}/hqdefault.jpg`;
              const trackCover = (trk.cover && trk.cover !== cover) ? trk.cover : uniqueCover;
              return {
                id: yt.id,
                title: trk.title || yt.title,
                artist: trk.artist || yt.artist || '',
                duration: yt.duration || trk.duration || 0,
                cover: trackCover,
              };
            }
            return null;
          })
        );
        for (const r of batchResults) {
          if (r.status === 'fulfilled' && r.value) results.push(r.value);
        }
      }
      sendJson(res, 200, { name, cover, tracks: results });
      return true;
    } catch (e) {
      console.error('[playlist-import] Error:', e.message);
      sendJson(res, 502, { error: e.message || 'Ошибка импорта плейлиста' });
      return true;
    }
  }

  return false;
}

module.exports = { handleMusicRoutes };
