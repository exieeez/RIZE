import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

test('Auto-Update Arrival on Mobile & PC', async (t) => {
  await t.test('1. Android AppUpdateManager checks immediately on startup without 5-minute delay', () => {
    const ktFile = path.resolve('android/app/src/main/java/app/votify/mobile/data/AppUpdateManager.kt');
    const content = fs.readFileSync(ktFile, 'utf8');

    assert.ok(
      content.includes('checkForUpdate(force = true)'),
      'AppUpdateManager should trigger immediate checkForUpdate(force = true) on launch'
    );
    assert.ok(
      content.includes('delay(1500L)\n            checkForUpdate(force = true)'),
      'Initial check must execute after 1.5s startup delay'
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
});
