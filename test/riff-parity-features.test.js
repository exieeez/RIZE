import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

test('Riff Strong Features Integration (PC & Mobile Parity)', async (t) => {
  await t.test('1. 10-band equalizer and studio presets exist on PC and Android', () => {
    const mainJs = fs.readFileSync(path.join(rootDir, 'src/main.js'), 'utf-8');
    const indexHtml = fs.readFileSync(path.join(rootDir, 'src/index.html'), 'utf-8');
    const audioEffectsKt = fs.readFileSync(path.join(rootDir, 'android/app/src/main/java/app/votify/mobile/player/AudioEffectsManager.kt'), 'utf-8');
    const playerScreenKt = fs.readFileSync(path.join(rootDir, 'android/app/src/main/java/app/votify/mobile/ui/player/PlayerScreen.kt'), 'utf-8');

    // PC 10 bands & presets
    assert.match(mainJs, /eqFreqs\s*=\s*\[32,\s*64,\s*125,\s*250,\s*500,\s*1000,\s*2000,\s*4000,\s*8000,\s*16000\]/);
    assert.match(mainJs, /electronic:\s*\[/);
    assert.match(mainJs, /rock:\s*\[/);
    assert.match(mainJs, /acoustic:\s*\[/);

    // PC HTML presets & speed pills
    assert.match(indexHtml, /data-preset="electronic"/);
    assert.match(indexHtml, /data-preset="rock"/);
    assert.match(indexHtml, /data-preset="acoustic"/);
    assert.match(indexHtml, /class="speed-pill-btn[^"]*"\s+data-speed="1\.15"/);

    // Android 10 bands
    assert.match(audioEffectsKt, /32f,\s*64f,\s*125f,\s*250f,\s*500f,\s*1000f,\s*2000f,\s*4000f,\s*8000f,\s*16000f/);
    assert.match(playerScreenKt, /val freqLabels = listOf\("32", "64", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"\)/);
    assert.match(playerScreenKt, /"electronic" to "Электронная"/);
    assert.match(playerScreenKt, /"rock" to "Рок"/);
    assert.match(playerScreenKt, /"acoustic" to "Акустика"/);
  });

  await t.test('2. Audio output status pill exists on PC and Android', () => {
    const indexHtml = fs.readFileSync(path.join(rootDir, 'src/index.html'), 'utf-8');
    const mainJs = fs.readFileSync(path.join(rootDir, 'src/main.js'), 'utf-8');
    const playerScreenKt = fs.readFileSync(path.join(rootDir, 'android/app/src/main/java/app/votify/mobile/ui/player/PlayerScreen.kt'), 'utf-8');
    const audioOutputJs = fs.readFileSync(path.join(rootDir, 'routes/audio-output.js'), 'utf-8');

    // Backend route
    assert.match(audioOutputJs, /getSystemAudioOutputInfo/);

    // PC HTML & JS
    assert.match(indexHtml, /id="audio-output-pill"/);
    assert.match(mainJs, /\/api\/audio-output-info/);
    assert.match(mainJs, /audio-output-text/);

    // Android
    assert.match(playerScreenKt, /audioOutputInfo/);
    assert.match(playerScreenKt, /AudioDeviceInfo\.TYPE_BLUETOOTH_A2DP/);
  });

  await t.test('3. Lyrics translation into Russian exists on PC and Android', () => {
    const indexHtml = fs.readFileSync(path.join(rootDir, 'src/index.html'), 'utf-8');
    const mainJs = fs.readFileSync(path.join(rootDir, 'src/main.js'), 'utf-8');
    const musicRoutesJs = fs.readFileSync(path.join(rootDir, 'routes/music.js'), 'utf-8');
    const playerScreenKt = fs.readFileSync(path.join(rootDir, 'android/app/src/main/java/app/votify/mobile/ui/player/PlayerScreen.kt'), 'utf-8');

    // Backend route
    assert.match(musicRoutesJs, /\/api\/translate/);

    // PC HTML & JS
    assert.match(indexHtml, /id="right-lyrics-translate-btn"/);
    assert.match(indexHtml, /id="full-lyrics-translate-btn"/);
    assert.match(indexHtml, /id="fs-lyrics-translate-btn"/);
    assert.match(mainJs, /toggleLyricsTranslation/);

    // Android
    assert.match(playerScreenKt, /translateLyricsLines/);
    assert.match(playerScreenKt, /translatedLines/);
  });

  await t.test('4. Offline track download action exists on PC and Backend', () => {
    const indexHtml = fs.readFileSync(path.join(rootDir, 'src/index.html'), 'utf-8');
    const mainJs = fs.readFileSync(path.join(rootDir, 'src/main.js'), 'utf-8');
    const musicRoutesJs = fs.readFileSync(path.join(rootDir, 'routes/music.js'), 'utf-8');

    // Backend route
    assert.match(musicRoutesJs, /\/api\/download/);

    // PC HTML buttons & JS handler
    assert.match(indexHtml, /id="right-download-btn"/);
    assert.match(indexHtml, /id="fi-download"/);
    assert.match(mainJs, /downloadCurrentTrack/);
  });

  await t.test('5. Multi-platform playlist import includes Apple Music support', () => {
    const musicRoutesJs = fs.readFileSync(path.join(rootDir, 'routes/music.js'), 'utf-8');
    assert.match(musicRoutesJs, /music\.apple\.com/);
    assert.match(musicRoutesJs, /itunes\.apple\.com\/lookup/);
  });
});
