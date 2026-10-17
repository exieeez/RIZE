const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const test = require('node:test');

// Isolate test database in temporary directory
const testConfigDir = fs.mkdtempSync(path.join(os.tmpdir(), 'votify-playlists-test-'));
process.env.VOTIFY_CONFIG_DIR = testConfigDir;

const { handlePlaylistRoutes, loadPublicPlaylists, savePublicPlaylists } = require('../routes/playlists.js');

function mockRes() {
  let statusCode = 200;
  let headers = {};
  let body = '';
  return {
    writeHead(code, h) {
      statusCode = code;
      headers = { ...headers, ...h };
    },
    end(data) {
      body = data;
    },
    get statusCode() { return statusCode; },
    get headers() { return headers; },
    get body() { return body; },
    json() {
      try { return JSON.parse(body); } catch (e) { return null; }
    }
  };
}

test('loadPublicPlaylists returns curated initial playlists with valid tracklists', () => {
  const playlists = loadPublicPlaylists();
  assert.ok(Array.isArray(playlists), 'Playlists is an array');
  assert.ok(playlists.length >= 3, 'Contains curated public playlists');

  const top50 = playlists.find(p => p.title.includes('Топ 50'));
  assert.ok(top50, 'Votify Топ 50 playlist found');
  assert.ok(Array.isArray(top50.tracks), 'Tracks is an array');
  assert.ok(top50.tracks.length > 0, 'Contains tracks');
  assert.ok(top50.tracks[0].title, 'Track has a title');
  assert.ok(top50.tracks[0].artist, 'Track has an artist');
});

test('GET /api/playlists returns all public playlists and supports query filtering by title and track', async () => {
  // Test query all
  const reqAll = { method: 'GET' };
  const resAll = mockRes();
  const urlAll = new URL('http://localhost:17217/api/playlists');
  const handledAll = await handlePlaylistRoutes(reqAll, resAll, urlAll);

  assert.equal(handledAll, true);
  assert.equal(resAll.statusCode, 200);
  const dataAll = resAll.json();
  assert.ok(dataAll.ok);
  assert.ok(dataAll.playlists.length >= 3);

  // Test query by playlist title
  const reqTitle = { method: 'GET' };
  const resTitle = mockRes();
  const urlTitle = new URL('http://localhost:17217/api/playlists?q=chill');
  const handledTitle = await handlePlaylistRoutes(reqTitle, resTitle, urlTitle);

  assert.equal(handledTitle, true);
  const dataTitle = resTitle.json();
  assert.ok(dataTitle.playlists.length >= 1);
  assert.ok(dataTitle.playlists.some(p => p.title.toLowerCase().includes('chill')));

  // Test query by track inside playlist (e.g. "Кино" or "Starboy")
  const reqTrack = { method: 'GET' };
  const resTrack = mockRes();
  const urlTrack = new URL('http://localhost:17217/api/playlists?q=starboy');
  const handledTrack = await handlePlaylistRoutes(reqTrack, resTrack, urlTrack);

  assert.equal(handledTrack, true);
  const dataTrack = resTrack.json();
  assert.ok(dataTrack.playlists.length >= 1);
  assert.ok(dataTrack.playlists.some(p => p.tracks.some(t => t.title.toLowerCase().includes('starboy'))));
});

test('POST /api/playlists/publish publishes user playlists with tracks and makes them searchable', async () => {
  const uniqueTitle = `Мой Супер Плейлист ${Date.now()}`;
  const customTracks = [
    { id: 'custom-1', title: 'Уникальный Трек Альфа', artist: 'Артист 1', duration: 200 },
    { id: 'custom-2', title: 'Уникальный Трек Бета', artist: 'Артист 2', duration: 180 }
  ];

  const reqPub = {
    method: 'POST',
    on(event, handler) {
      if (event === 'data') {
        handler(Buffer.from(JSON.stringify({
          playlists: [
            {
              title: uniqueTitle,
              author: 'Тестовый Пользователь',
              cover: 'https://example.com/cover.jpg',
              description: 'Тестовое описание',
              tracks: customTracks
            }
          ]
        })));
      }
      if (event === 'end') handler();
    }
  };
  const resPub = mockRes();
  const urlPub = new URL('http://localhost:17217/api/playlists/publish');
  const handledPub = await handlePlaylistRoutes(reqPub, resPub, urlPub);

  assert.equal(handledPub, true);
  assert.equal(resPub.statusCode, 200);
  const dataPub = resPub.json();
  assert.ok(dataPub.ok);
  assert.equal(dataPub.publishedCount, 1);

  // Now search for this published playlist by title
  const reqSearch = { method: 'GET' };
  const resSearch = mockRes();
  const urlSearch = new URL(`http://localhost:17217/api/playlists?q=${encodeURIComponent(uniqueTitle)}`);
  await handlePlaylistRoutes(reqSearch, resSearch, urlSearch);
  const dataSearch = resSearch.json();

  assert.ok(dataSearch.playlists.length >= 1);
  const found = dataSearch.playlists.find(p => p.title === uniqueTitle);
  assert.ok(found, 'Found published playlist');
  assert.equal(found.author, 'Тестовый Пользователь');
  assert.equal(found.tracks.length, 2);
  assert.equal(found.tracks[0].title, 'Уникальный Трек Альфа');

  // Clean up published test playlist
  const reqClean = {
    method: 'POST',
    on(event, handler) {
      if (event === 'data') {
        handler(Buffer.from(JSON.stringify({ title: uniqueTitle, author: 'Тестовый Пользователь' })));
      }
      if (event === 'end') handler();
    }
  };
  const resClean = mockRes();
  const urlClean = new URL('http://localhost:17217/api/playlists/delete');
  await handlePlaylistRoutes(reqClean, resClean, urlClean);
});

test('Client main.js integrates public and local playlist search with track display and library save action', () => {
  const root = path.join(__dirname, '..');
  const main = fs.readFileSync(path.join(root, 'src/main.js'), 'utf8');

  // searchAllPlaylists function exists and queries local + public
  assert.match(main, /async function searchAllPlaylists\(query\)/);
  assert.match(main, /window\.searchAllPlaylists\s*=\s*searchAllPlaylists/);

  // publishLocalPlaylistsToPublic exists
  assert.match(main, /async function publishLocalPlaylistsToPublic\(\)/);

  // doSearch queries both tracks and playlists
  assert.match(main, /searchAllPlaylists\(query\)/);

  // renderSearchResults handles both playlists filter and all filter playlists section
  assert.match(main, /activeSearchFilter === 'playlists'/);
  assert.match(main, /<h3 class="artist-section-title"><i class="material-icons">queue_music<\/i> Найденные плейлисты/);
  assert.match(main, /<h3 class="artist-section-title"[^>]*><i class="material-icons">queue_music<\/i> Плейлисты<\/h3>/);

  // openPlaylist supports public community playlist with tracks and save button
  assert.match(main, /function openPlaylist\(playlistArg,\s*customTracks,\s*customAuthor,\s*customCover\)/);
  assert.match(main, /['"]pl-screen-save-btn['"]/);
  assert.match(main, /Плейлист «\$\{title\}» добавлен в медиатеку!/);
});

test('POST /api/playlists/delete removes playlist and firebase-client replaces library without merge:true', async () => {
  const root = path.join(__dirname, '..');
  const { handlePlaylistRoutes } = require('../routes/playlists.js');
  const firebaseClient = fs.readFileSync(path.join(root, 'src/firebase-client.js'), 'utf8');
  const main = fs.readFileSync(path.join(root, 'src/main.js'), 'utf8');
  const themeCss = fs.readFileSync(path.join(root, 'src/styles/dotify-23-theme.css'), 'utf8');

  // 1. Verify delete endpoint removes playlist from public playlists
  const delTitle = 'Удаляемый Плейлист Тест';
  const reqDel = {
    method: 'POST',
    on(event, handler) {
      if (event === 'data') {
        handler(Buffer.from(JSON.stringify({ title: delTitle, author: 'Тестовый Автор' })));
      }
      if (event === 'end') handler();
    }
  };
  const resDel = mockRes();
  const urlDel = new URL('http://localhost:17217/api/playlists/delete');
  const handledDel = await handlePlaylistRoutes(reqDel, resDel, urlDel);
  assert.equal(handledDel, true);
  assert.equal(resDel.statusCode, 200);

  // 2. Verify firebase-client overwrites library doc without merge:true so deleted keys do not persist
  assert.match(firebaseClient, /syncRef\(user\.uid,\s*['"]library['"]\),\s*\{\s*playlists:\s*playlists/);
  // Verify it does NOT have { merge: true } right after library doc
  const librarySetMatch = firebaseClient.match(/syncRef\(user\.uid,\s*['"]library['"]\),\s*\{[\s\S]*?\}\s*(\,\s*\{[\s\S]*?\})?\s*\);/);
  assert.ok(librarySetMatch, 'Found library doc batch.set');
  assert.ok(!librarySetMatch[0].includes('merge: true'), 'library doc batch.set must not have merge: true');

  // 3. Verify deletePlaylist in main.js calls syncWithCloud push immediately
  assert.match(main, /async function deletePlaylist\(name\)/);
  assert.match(main, /syncWithCloud\(['"]push['"]\)/);

  // 4. Verify play button triangle centering in dotify-23-theme.css (no margin-left: 3px)
  assert.ok(!themeCss.includes('.my-wave-hero-play .wave-play-svg {\n  margin-left: 3px;'), 'margin-left: 3px must be removed');
  assert.match(themeCss, /\.my-wave-hero-play:not\(\.playing\) \.wave-play-svg/);
});

