const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

describe('Spotify Profile Import & Icon Assets & File Import Verification', () => {
  it('all required branding icons exist and have valid sizes', () => {
    const iconPaths = [
      'src/icon.png',
      'src/assets/logo.png',
      'src/assets/logo.svg',
      'public/logo.png',
      'assets/icon.png',
      'assets/icon-mobile.png',
      'assets/icon-new.png',
      'assets/logo.png',
      'src/icons/icon-512.png',
      'src/icons/icon-192.png',
      'src/icons/apple-touch-icon.png',
      'src/icons/icon-maskable-512.png',
      'src-tauri/icons/icon.png',
      'src-tauri/icons/128x128.png',
      'src-tauri/icons/32x32.png',
      'src-tauri/icons/64x64.png',
      'android/app/src/main/res/drawable/ic_votify_logo.png',
      'android/app/src/main/res/drawable-nodpi/ic_launcher_visual.png',
      'android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png',
    ];

    for (const rel of iconPaths) {
      const full = path.join(__dirname, '..', rel);
      assert.ok(fs.existsSync(full), `File ${rel} should exist`);
      const stat = fs.statSync(full);
      assert.ok(stat.size > 100, `File ${rel} should have non-trivial size`);
    }

    // Verify SVG is valid SVG
    const svgPath = path.join(__dirname, '..', 'src/assets/logo.svg');
    const svgContent = fs.readFileSync(svgPath, 'utf8');
    assert.ok(svgContent.includes('<svg'), 'SVG should contain <svg tag');
    assert.ok(svgContent.includes('viewBox'), 'SVG should contain viewBox');
  });

  it('index.html contains modernized file dropzone and Spotify profile import UI', () => {
    const htmlPath = path.join(__dirname, '..', 'src/index.html');
    const html = fs.readFileSync(htmlPath, 'utf8');

    // File dropzone elements
    assert.ok(html.includes('import-dropzone-wrapper'), 'Should contain import-dropzone-wrapper');
    assert.ok(html.includes('import-dropzone-chips'), 'Should contain format chips');
    assert.ok(html.includes('import-dropzone-features'), 'Should contain feature callouts');
    assert.ok(html.includes('import-file-card'), 'Should contain selected file card');
    assert.ok(html.includes('import-file-type-icon'), 'Should contain file type icon');

    // Profile import elements
    assert.ok(html.includes('pc-spotify-import-card'), 'Should contain pc-spotify-import-card');
    assert.ok(html.includes('profile-spotify-url'), 'Should contain profile-spotify-url input');
    assert.ok(html.includes('btn-import-spotify-profile'), 'Should contain btn-import-spotify-profile button');

    // Spotify modal profile tab
    assert.ok(html.includes('data-mode="profile"'), 'Should contain profile mode in spotify modal');
    assert.ok(html.includes('spotify-profile-url-input'), 'Should contain spotify-profile-url-input');
  });

  it('styles.css contains styling for new dropzone and profile import card', () => {
    const cssPath = path.join(__dirname, '..', 'src/styles.css');
    const css = fs.readFileSync(cssPath, 'utf8');

    assert.ok(css.includes('.import-dropzone-wrapper'), 'CSS should have .import-dropzone-wrapper');
    assert.ok(css.includes('.import-dropzone-icon-circle'), 'CSS should have .import-dropzone-icon-circle');
    assert.ok(css.includes('.import-chip-group'), 'CSS should have .import-chip-group');
    assert.ok(css.includes('.import-file-card'), 'CSS should have .import-file-card');
    assert.ok(css.includes('.pc-spotify-import-card'), 'CSS should have .pc-spotify-import-card');
  });

  it('routes/music.js handles Spotify user profile URL extraction correctly', () => {
    const musicRoutesPath = path.join(__dirname, '..', 'routes/music.js');
    const content = fs.readFileSync(musicRoutesPath, 'utf8');

    assert.ok(content.includes('/api/spotify/profile'), 'Should have /api/spotify/profile endpoint');
    assert.ok(content.includes('fetchSpotifyUserProfile'), 'Should have fetchSpotifyUserProfile helper');
    assert.ok(content.includes('userId'), 'Should match userId in Spotify URL parsing');
    assert.ok(content.includes('Twitterbot/1.0'), 'Should use Twitterbot/1.0 for reliable OpenGraph profile scraping');
  });

  it('firebase-client.js wires Spotify profile import correctly', () => {
    const clientPath = path.join(__dirname, '..', 'src/firebase-client.js');
    const client = fs.readFileSync(clientPath, 'utf8');

    assert.ok(client.includes('btn-import-spotify-profile'), 'Should wire btn-import-spotify-profile');
    assert.ok(client.includes('/api/spotify/profile'), 'Should fetch /api/spotify/profile');
    assert.ok(client.includes('profile-display-name'), 'Should update profile-display-name');
    assert.ok(client.includes('profile-avatar-img'), 'Should update profile-avatar-img');
    assert.ok(client.includes('profile-banner-bg'), 'Should update profile-banner-bg');
  });
});
