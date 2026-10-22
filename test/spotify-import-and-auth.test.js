const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

describe('Spotify Import & Auth Leak Prevention Verification', () => {
  it('index.html must not contain hardcoded personal user email or nickname in quick login', () => {
    const htmlPath = path.join(__dirname, '..', 'src/index.html');
    const html = fs.readFileSync(htmlPath, 'utf8');

    // Quick login container
    const quickLoginMatch = html.match(/<div class="auth-form" id="auth-quick-login"[\s\S]*?<\/div>\s*<!--/);
    assert.ok(quickLoginMatch, 'auth-quick-login block must exist in index.html');
    const quickLoginBlock = quickLoginMatch[0];

    assert.ok(!quickLoginBlock.includes('vovan232012s@gmail.com'), 'auth-quick-login must not have hardcoded personal email');
    assert.ok(!quickLoginBlock.includes('exieeez'), 'auth-quick-login must not have hardcoded username');
    assert.ok(quickLoginBlock.includes('style="display:none !important;"'), 'auth-quick-login must be hidden by default');
  });

  it('styles.css must allow hiding #auth-quick-login without forced !important display', () => {
    const cssPath = path.join(__dirname, '..', 'src/styles.css');
    const css = fs.readFileSync(cssPath, 'utf8');

    // Check auth-quick-login definition
    const match = css.match(/#auth-quick-login\s*\{([^}]+)\}/);
    assert.ok(match, '#auth-quick-login rule should exist in styles.css');
    const body = match[1];

    assert.ok(!body.includes('display: flex !important;'), '#auth-quick-login should NOT have display: flex !important;');
    assert.ok(css.includes('#auth-quick-login[style*="display: none"]'), 'CSS should enforce display: none !important when styled hidden');
  });

  it('firebase-client.js getSavedLoginUser must return null fallback instead of hardcoded account', () => {
    const clientPath = path.join(__dirname, '..', 'src/firebase-client.js');
    const code = fs.readFileSync(clientPath, 'utf8');

    assert.ok(!code.includes("email: 'vovan232012s@gmail.com'"), 'firebase-client.js must not contain hardcoded email fallback');
    assert.ok(!code.includes("displayName: 'exieeez'"), 'firebase-client.js must not contain hardcoded displayName fallback');
    assert.ok(code.includes('return null;'), 'getSavedLoginUser should return null when not saved in localStorage');
  });

  it('firebase-client.js and import modals must use dynamic apiBase for Electron compatibility', () => {
    const clientPath = path.join(__dirname, '..', 'src/firebase-client.js');
    const code = fs.readFileSync(clientPath, 'utf8');
    assert.ok(code.includes('${apiBase}/api/spotify/profile'), 'Spotify profile fetch should use apiBase');

    const importPath = path.join(__dirname, '..', 'src/import-modal-logic.js');
    const importCode = fs.readFileSync(importPath, 'utf8');
    assert.ok(importCode.includes('${apiBase}/api/playlist/stream'), 'Import modal EventSource should use apiBase');
    assert.ok(importCode.includes('${apiBase}/api/playlist/resolve-stream'), 'Import modal batch resolve should use apiBase');
  });

  it('routes/music.js routes /api/spotify-import and handles Spotify playlist extraction', () => {
    const routesPath = path.join(__dirname, '..', 'routes/music.js');
    const content = fs.readFileSync(routesPath, 'utf8');

    assert.ok(content.includes('/api/spotify-import'), 'routes/music.js should handle /api/spotify-import');
    assert.ok(content.includes('/api/spotify-status'), 'routes/music.js should handle /api/spotify-status');
    assert.ok(content.includes('/api/spotify-library'), 'routes/music.js should handle /api/spotify-library');
    assert.ok(content.includes('extractTracksFromUrl'), 'routes/music.js should have extractTracksFromUrl');
    assert.ok(content.includes('walk(nextData)'), 'routes/music.js should have resilient deep tracklist walker');
  });
});
