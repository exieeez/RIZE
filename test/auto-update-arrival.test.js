import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

test('Auto-Update Arrival on Mobile & PC', async (t) => {
  await t.test('1. Android AppUpdateManager checks immediately on startup without 5-minute delay', () => {
    const ktFile = path.resolve('android/app/src/main/java/app/votify/mobile/data/AppUpdateManager.kt');
    const content = fs.readFileSync(ktFile, 'utf8');

    assert.ok(
      content.includes('checkForUpdate(force = false)'),
      'AppUpdateManager should check without forcing dismissed updates'
    );
    assert.ok(
      content.includes('delay(2000L)\n            checkForUpdate(force = false)'),
      'Initial check must execute after 2s startup delay'
    );
    assert.ok(
      !content.includes('// Auto-start download so the update arrives and prepares seamlessly\n                    startDownload(info)'),
      'Must NOT auto-download in background without user consent'
    );
  });

  await t.test('2. PC main.js registers window.checkForVotifyUpdates and auto-arrival logic', () => {
    const mainJs = path.resolve('src/main.js');
    const content = fs.readFileSync(mainJs, 'utf8');

    assert.ok(
      content.includes('window.checkForVotifyUpdates = function'),
      'src/main.js must define window.checkForVotifyUpdates'
    );
    assert.ok(
      content.includes('simulateDownload(availableInfo'),
      'src/main.js must support smooth download progress simulation'
    );
    assert.ok(
      content.includes('Доступно обновление') || content.includes('Доступно авто-обновление'),
      'src/main.js must display update available banner'
    );
    assert.ok(
      content.includes('Перезапустить'),
      'src/main.js must switch action button to Перезапустить upon completion'
    );
  });

  await t.test('3. PC settings nav update item triggers active update check', () => {
    const mainJs = path.resolve('src/main.js');
    const content = fs.readFileSync(mainJs, 'utf8');

    const updateNavSnippet = content.substring(
      content.indexOf("document.getElementById('m3-nav-update-item')"),
      content.indexOf("document.getElementById('m3-nav-update-item')") + 350
    );
    assert.ok(
      updateNavSnippet.includes('checkForVotifyUpdates'),
      'Settings update item must call checkForVotifyUpdates'
    );
  });

  await t.test('4. PC src/app.js registers auto-update arrival and banner handling', () => {
    const appJs = path.resolve('src/app.js');
    const content = fs.readFileSync(appJs, 'utf8');

    assert.ok(
      content.includes('window.checkForRizeUpdates = window.checkForVotifyUpdates = function'),
      'src/app.js must define window.checkForVotifyUpdates'
    );
    assert.ok(
      content.includes('simulateDownload(availableInfo'),
      'src/app.js must support smooth download progress simulation'
    );
    assert.ok(
      content.includes('update-banner'),
      'src/app.js must manage update-banner element'
    );
    assert.ok(
      content.includes('Перезапустить'),
      'src/app.js must switch action button to Перезапустить upon completion'
    );
  });

  await t.test('5. PC src/index.html includes update-banner structure', () => {
    const htmlPath = path.resolve('src/index.html');
    const content = fs.readFileSync(htmlPath, 'utf8');

    assert.ok(content.includes('id="update-banner"'), 'index.html must include #update-banner');
    assert.ok(content.includes('id="update-banner-text"'), 'index.html must include #update-banner-text');
    assert.ok(content.includes('id="update-banner-action"'), 'index.html must include #update-banner-action');
    assert.ok(content.includes('id="update-banner-close"'), 'index.html must include #update-banner-close');
    assert.ok(content.includes('id="update-banner-progress"'), 'index.html must include #update-banner-progress');
  });
});
