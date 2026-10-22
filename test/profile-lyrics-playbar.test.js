const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

test('Profile overlay, lyrics tools in right panel header, and centered playbar controls with cover seek', () => {
  const root = path.join(__dirname, '..');
  const indexHtml = fs.readFileSync(path.join(root, 'src/index.html'), 'utf8');
  const stylesCss = fs.readFileSync(path.join(root, 'src/styles.css'), 'utf8');
  const appJs = fs.readFileSync(path.join(root, 'src/app.js'), 'utf8');

  // 1. Material Icons loaded in src/index.html (without legacy dotify-23 stylesheet clobbering UI)
  assert.ok(indexHtml.includes('Material+Icons'), 'Material Icons font must be loaded in index.html');
  assert.ok(!indexHtml.includes('styles/dotify-23-theme.css'), 'dotify-23-theme.css must NOT override global styles');

  // 2. Lyrics action buttons moved to right panel header actions to the left of #btn-lyrics-mode
  assert.ok(indexHtml.includes('id="right-panel-tool-pill"'), 'right-panel-tool-pill must exist in right-panel-header-actions');
  const pillIndex = indexHtml.indexOf('id="right-panel-tool-pill"');
  const wordBtnIndex = indexHtml.indexOf('id="btn-lyrics-mode"');
  assert.ok(pillIndex < wordBtnIndex, 'Tool pill must appear before Word button in right-panel-header-actions');
  assert.ok(indexHtml.includes('id="btn-add-custom-lyrics"'));
  assert.ok(indexHtml.includes('id="btn-sync-lyrics"'));
  assert.ok(indexHtml.includes('id="btn-translate-lyrics"'));
  assert.ok(indexHtml.includes('id="btn-tv-karaoke"'));

  // 3. Tool pill styling in styles.css has compact/comfortable height and wrapping
  assert.match(stylesCss, /\.right-panel-tool-pill[\s\S]*height:\s*(?:28px|38px)/);
  assert.match(stylesCss, /\.right-panel-tool-pill:\s*hover[\s\S]*opacity:\s*1/);

  // 4. Playbar center playback controls centered and squiggly timeline hidden in playbar
  assert.match(stylesCss, /\.playbar\s*\.scrubber-bar-wrapper[\s\S]*display:\s*none\s*!important/);
  assert.match(stylesCss, /\.playback-controls[\s\S]*margin:\s*0\s*auto\s*!important/);

  // 5. Interactive cover ring seek implemented on #playbar-artwork-wrapper
  assert.match(appJs, /seekFromWrapEvent/);
  assert.match(appJs, /wrap\.addEventListener\('pointerdown'/);
  assert.match(appJs, /wrap\.addEventListener\('pointermove'/);
  assert.match(stylesCss, /#playbar-artwork-wrapper[\s\S]*cursor:\s*pointer\s*!important/);

  // 6. Profile and auth overlays hidden by default and beautifully styled
  assert.match(stylesCss, /#profile-overlay\[style\*="display:\s*none"\][\s\S]*display:\s*none\s*!important/);
  assert.match(stylesCss, /#auth-overlay\[style\*="display:\s*none"\][\s\S]*display:\s*none\s*!important/);
  assert.match(stylesCss, /\.pc-profile-modal[\s\S]*border-radius:\s*28px/);
  assert.match(stylesCss, /\.auth-card[\s\S]*border-radius:\s*28px/);
  assert.match(stylesCss, /\.google-icon[\s\S]*width:\s*18px\s*!important/);

  // 7. Liked pin styled with unified grey background and white heart (NO gradient)
  assert.match(stylesCss, /\.sidebar-pin\.pin-liked[\s\S]*background:\s*rgba\(255,\s*255,\s*255,\s*0\.08\)\s*!important/);
  assert.match(stylesCss, /\.sidebar-pin\.pin-liked svg[\s\S]*fill:\s*#ffffff/);

  // 8. Track switch syncs with home screen and sidebar
  assert.match(appJs, /syncHomeWithPlayingTrack/);
  assert.match(appJs, /updateSidebarPlayingTrack/);

  // 9. Fullscreen player visualizers hidden per user request
  assert.match(stylesCss, /#tvk-visualizer[\s\S]*display:\s*none\s*!important/);

  // 10. Favorites card in library view has subtle light shade, NO gradient, white icon
  assert.match(stylesCss, /\.lib-playlist-card-cover\.fav-gradient[\s\S]*background:\s*rgba\(255,\s*255,\s*255,\s*0\.08\)\s*!important/);
  assert.match(stylesCss, /\.lib-playlist-card-cover\.fav-gradient svg[\s\S]*fill:\s*#ffffff/);

  // 11. Sidebar Liked pin is larger, clickable, separated from playlists below
  assert.match(stylesCss, /\.sidebar-pin\.pin-liked[\s\S]*width:\s*(?:52px|56px)\s*!important/);
  assert.match(stylesCss, /\.sidebar-pin\.pin-liked[\s\S]*height:\s*(?:52px|56px)\s*!important/);
  assert.match(stylesCss, /\.sidebar-playlists-sep/);
  assert.match(stylesCss, /\.sidebar-pin\.pin-playlist/);
  assert.match(appJs, /sidebar-playlists-sep/);

  // 12. Instant track artwork updates and cached image handling in m3cSetImage
  assert.match(appJs, /function m3cSetImage/);
  assert.match(appJs, /img\.complete && img\.naturalWidth > 0/);

  // 13. Cloud library & sync blob integration in src/app.js
  assert.match(appJs, /ingestCloudPlaylists/);
  assert.match(appJs, /ingestCloudSyncBlob/);
  assert.match(appJs, /votify:cloud-library-updated/);
  assert.match(appJs, /votify:cloud-sync-blob-updated/);

  // 14. Playlist deletion: instant UI refresh, card delete button, and cloud sync
  assert.match(appJs, /promptDeletePlaylist/);
  assert.match(appJs, /deleteCurrentPlaylist/);
  assert.match(appJs, /lib-playlist-card-del-btn/);
  assert.match(stylesCss, /\.lib-playlist-card-del-btn/);
});
