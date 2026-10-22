const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('Cover resolution resilience and CloudSync Android blob parsing', () => {
  const root = path.join(__dirname, '..');
  const appJs = fs.readFileSync(path.join(root, 'src/app.js'), 'utf8');
  const firebaseClientJs = fs.readFileSync(path.join(root, 'src/firebase-client.js'), 'utf8');

  // 1. updateNowPlayingUI extracts fallback thumbnails (thumbnail, cover, image) if getTrackThumbUrl returns falsy
  assert.match(
    appJs,
    /const thumb = getTrackThumbUrl\(track\) \|\| track\.thumbnail \|\| track\.cover \|\| track\.image \|\| '';/,
    'thumb must fall back to track.thumbnail, track.cover, track.image'
  );

  // 2. getNextFallbackThumb provides robust YouTube fallback when image fails to load
  assert.match(
    appJs,
    /function updateNowPlayingUI[\s\S]*getNextFallbackThumb[\s\S]*img\.youtube\.com/,
    'must implement fallback to img.youtube.com or direct track thumbnails on error'
  );

  // 3. m3cInit guards against reflected document URL on empty src
  assert.match(
    appJs,
    /!curAttr\.includes\('index\.html'\)/,
    'm3cInit must ignore empty src attribute reflecting document URL'
  );

  // 4. CloudSync Android sync blob handles both array and object structures for Избранное without throwing TypeError
  assert.match(
    firebaseClientJs,
    /Array\.isArray\(playlists\['Избранное'\]\)/,
    'must support array playlists.Избранное'
  );
  assert.match(
    firebaseClientJs,
    /playlists\['Избранное'\]\.tracks/,
    'must support object playlists.Избранное with tracks array'
  );
});
