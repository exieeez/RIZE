const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');

test('Album track resolution and artist handling', () => {
  // Test normalization and artist string extraction logic
  const album = { id: 918223821, title: 'KIDSCRY2DAY', artist: 'ONDA ANDAR' };
  const trackWithObjArtist = {
    id: 3842359741,
    title: 'sleep mode',
    artist: { id: 285630331, name: 'ONDA ANDAR' },
    duration: 173
  };

  const artistName = typeof trackWithObjArtist.artist === 'string'
    ? trackWithObjArtist.artist
    : (trackWithObjArtist.artist && trackWithObjArtist.artist.name) ||
      (typeof album.artist === 'string' ? album.artist : (album.artist && album.artist.name)) || '';
  const trackTitle = typeof trackWithObjArtist.title === 'string'
    ? trackWithObjArtist.title
    : (trackWithObjArtist.title_short || trackWithObjArtist.name || '');

  const query = `${artistName} ${trackTitle}`.trim();
  assert.strictEqual(query, 'ONDA ANDAR sleep mode');
  assert.ok(!query.includes('[object Object]'));
});

test('Sidebar active indicator styling and non-distortion', () => {
  const css = fs.readFileSync(path.join(__dirname, '../src/styles.css'), 'utf8');

  // Verify nav-active-indicator has 24px pill border-radius
  assert.match(css, /\.nav-active-indicator\s*\{[^}]*border-radius:\s*24px/);

  // Verify sidebar-nav nav-item has 48px dimensions and 24px border-radius
  assert.match(css, /\.nav-item\s*\{[^}]*width:\s*48px/);
  assert.match(css, /\.nav-item\s*\{[^}]*height:\s*48px/);

  // Ensure nav-item is excluded from :active 12px squishing
  assert.match(css, /:not\(\.nav-item\):active/);
});

test('Artist profile parsing supports both object and string format', () => {
  // Test both response formats from /api/artist
  const backendResponse = {
    success: true,
    artist: "Kizaru",
    artistDetails: { picture: "https://example.com/pic.jpg" },
    tracks: [{ id: "dz_123", title: "Test", artist: "Kizaru" }],
    artistData: {
      name: "Kizaru",
      avatar: "https://example.com/pic.jpg",
      isVerified: true,
      tracks: [{ id: "dz_123", title: "Test", artist: "Kizaru" }]
    }
  };

  const cleanName = 'Kizaru';
  const data = backendResponse;
  const artist = (data.artist && typeof data.artist === 'object')
    ? data.artist
    : (data.artistData || {
        name: (typeof data.artist === 'string' ? data.artist : '') || data.name || cleanName,
        avatar: (data.artistDetails && data.artistDetails.picture) || '',
        isVerified: true,
        tracks: data.tracks || []
      });

  assert.ok(artist);
  assert.strictEqual(artist.name, 'Kizaru');
  assert.strictEqual(artist.tracks.length, 1);
  assert.strictEqual(artist.tracks[0].id, 'dz_123');
});

test('Direct streamable check recognizes YouTube, Deezer, SoundCloud, and Demo IDs', () => {
  const isDirectStreamable = (id) => {
    const cleanId = String(id || '').replace(/^yt_/, '').trim();
    const isYtId = /^[a-zA-Z0-9_-]{11}$/.test(cleanId);
    return isYtId || cleanId.startsWith('dz_') || cleanId.startsWith('sc_') || cleanId.startsWith('demo-');
  };

  assert.strictEqual(isDirectStreamable('aAU0o-MbawM'), true);
  assert.strictEqual(isDirectStreamable('yt_aAU0o-MbawM'), true);
  assert.strictEqual(isDirectStreamable('dz_4279190762'), true);
  assert.strictEqual(isDirectStreamable('sc_1234567'), true);
  assert.strictEqual(isDirectStreamable('demo-01'), true);
  assert.strictEqual(isDirectStreamable(''), false);
  assert.strictEqual(isDirectStreamable('invalid_id'), false);
});

test('Album track fallback query always resolves search terms', () => {
  const album = { id: '1876290614', title: 'KIDSCRY2DAY', artist: 'ONDA ANDAR' };
  const track = { no: 1, disc: 1, title: 'KIDSCRY2DAY', artist: 'ONDA ANDAR', durationMs: 134963 };
  const searchQuery = `${track.artist || album.artist} ${track.title}`.trim();
  assert.strictEqual(searchQuery, 'ONDA ANDAR KIDSCRY2DAY');
});

test('Top navigation and titlebar have drag region attributes and CSS', () => {
  const html = fs.readFileSync(path.join(__dirname, '../src/index.html'), 'utf8');
  const css = fs.readFileSync(path.join(__dirname, '../src/styles.css'), 'utf8');

  // Verify header and top-nav have data-tauri-drag-region
  assert.match(html, /<header[^>]*id="titlebar"[^>]*data-tauri-drag-region/);
  assert.match(html, /<div[^>]*class="top-nav"[^>]*data-tauri-drag-region/);

  // Verify CSS enables -webkit-app-region: drag for top-nav
  assert.match(css, /\.top-nav\s*\{[^}]*-webkit-app-region:\s*drag/);
  assert.match(css, /\.search-box-wrapper\s*\{[^}]*-webkit-app-region:\s*no-drag/);
});

test('Fullscreen visualizer, top navigation, sidebar pins, and playbar controls integrity', () => {
  const html = fs.readFileSync(path.join(__dirname, '../src/index.html'), 'utf8');
  const css = fs.readFileSync(path.join(__dirname, '../src/styles.css'), 'utf8');

  // Verify fullscreen player visualizer exists
  assert.match(html, /id="tv-lyrics-visualizer"/);
  assert.match(html, /id="tv-lyrics-vis-current"/);
  assert.match(css, /\.tv-lyrics-visualizer/);

  // Side visualizer removed per user explicit request ("убери вот это")
  assert.doesNotMatch(html, /id="side-lyrics-visualizer"/);

  // Top nav controls: settings on left, avatar profile on right
  assert.match(html, /id="tb-settings-btn"/);
  assert.match(html, /id="tb-avatar-btn"/);
  assert.match(html, /id="tb-profile-name"/);

  // Sidebar pins container
  assert.match(html, /id="sidebar-pins"/);

  // Playbar add to playlist button right after toggle-fav
  assert.match(html, /id="btn-add-to-playlist"/);

  // Library buttons and grid
  assert.match(html, /id="btn-lib-new-playlist"/);
  assert.match(html, /id="btn-lib-import-playlist"/);
  assert.match(html, /id="btn-lib-add-local-tracks"/);
  assert.match(html, /id="lib-playlists-grid"/);

  // CSS centers playbar controls
  assert.match(css, /\.playback-controls\s*\{[^}]*justify-content:\s*center/);
  assert.match(css, /\.scrubber-bar-wrapper/);
});

test('Lyrics response normalization supports both formats without dropping text', () => {
  const parseLyricsResponse = (data) => {
    const raw = (data && (data.lyrics || data.syncedLyrics || data.plainLyrics)) || '';
    const isSuccess = data && data.success !== false && (data.success || !!raw);
    return { isSuccess: Boolean(isSuccess && raw), raw };
  };

  // Format 1: routes/music.js format
  const fmt1 = {
    syncedLyrics: '[00:07.24] line 1\n[00:15.00] line 2',
    plainLyrics: 'line 1\nline 2',
    track: 'herons',
    artist: 'ONDA ANDAR'
  };
  const res1 = parseLyricsResponse(fmt1);
  assert.strictEqual(res1.isSuccess, true);
  assert.ok(res1.raw.includes('line 1'));

  // Format 2: riff server format
  const fmt2 = {
    success: true,
    lyrics: '[00:07.24] line 1\n[00:15.00] line 2',
    isCustom: false
  };
  const res2 = parseLyricsResponse(fmt2);
  assert.strictEqual(res2.isSuccess, true);
  assert.ok(res2.raw.includes('line 1'));

  // Format 3: empty
  const fmt3 = { success: false, lyrics: '' };
  const res3 = parseLyricsResponse(fmt3);
  assert.strictEqual(res3.isSuccess, false);
});

test('No TDZ reference errors for isDraggingScrubber and cacheRafId in src/app.js', () => {
  const appJs = fs.readFileSync(path.join(__dirname, '../src/app.js'), 'utf8');

  // Verify isDraggingScrubber and cacheRafId are declared early in DOMContentLoaded
  const domContentIdx = appJs.indexOf("addEventListener('DOMContentLoaded'");
  const isDraggingDeclIdx = appJs.indexOf('let isDraggingScrubber = false;');
  const cacheRafDeclIdx = appJs.indexOf('let cacheRafId = 0;');

  assert.ok(domContentIdx > 0);
  assert.ok(isDraggingDeclIdx > domContentIdx);
  assert.ok(cacheRafDeclIdx > domContentIdx);

  // Verify declarations happen BEFORE visibilitychange listener
  const visChangeIdx = appJs.indexOf("addEventListener('visibilitychange'");
  assert.ok(isDraggingDeclIdx < visChangeIdx, 'isDraggingScrubber must be declared before visibilitychange');
  assert.ok(cacheRafDeclIdx < visChangeIdx, 'cacheRafId must be declared before visibilitychange');
});


