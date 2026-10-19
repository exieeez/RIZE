const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const rootDir = path.resolve(__dirname, '..');

test('Artist Screen, Deezer Real Plays, Galaxy Blue Dots & Home Blocks Verification', async (t) => {
  const indexHtml = fs.readFileSync(path.join(rootDir, 'src/index.html'), 'utf8');
  const mainJs = fs.readFileSync(path.join(rootDir, 'src/main.js'), 'utf8');
  const themeCss = fs.readFileSync(path.join(rootDir, 'src/styles/dotify-23-theme.css'), 'utf8');
  const routesMusicJs = fs.readFileSync(path.join(rootDir, 'routes/music.js'), 'utf8');
  const androidArtistScreen = fs.readFileSync(path.join(rootDir, 'android/app/src/main/java/app/votify/mobile/ui/artist/ArtistScreen.kt'), 'utf8');
  const androidDeezerApi = fs.readFileSync(path.join(rootDir, 'android/app/src/main/java/app/votify/mobile/data/DeezerApi.kt'), 'utf8');
  const androidModels = fs.readFileSync(path.join(rootDir, 'android/app/src/main/java/app/votify/mobile/data/Models.kt'), 'utf8');

  await t.test('1. Galaxy Canvas has no cyan/blue dots (strictly dynamic accent & clean white)', () => {
    // Check that blue/cyan particle definition rgba(135, 225, 255) is removed from galaxy particle generator
    assert.ok(!mainJs.includes("rgba(135, 225, 255"), 'Galaxy canvas should not contain hardcoded cyan/blue dots');
    assert.match(mainJs, /p\.variant\s*===\s*0[\s\S]*?rgba\(255,\s*255,\s*255[\s\S]*?rgba\(\$\{accent\.r\}/, 'Particles must be strictly dynamic accent or white');
  });

  await t.test('2. Deezer API integration delivers real stream counts (rank / plays)', () => {
    // Backend
    assert.match(routesMusicJs, /getDeezerArtistData/, 'routes/music.js must implement getDeezerArtistData');
    assert.match(routesMusicJs, /rank:\s*t\.rank\s*\|\|\s*0/, 'routes/music.js must map Deezer track rank');
    assert.match(routesMusicJs, /plays:\s*t\.rank\s*\|\|\s*0/, 'routes/music.js must map Deezer plays');
    assert.match(routesMusicJs, /\/api\/deezer\/album-tracks/, 'routes/music.js must provide /api/deezer/album-tracks');

    // Android models and API
    assert.match(androidModels, /val\s+rank:\s*Int\s*=\s*0/, 'Track model on Android must include rank');
    assert.match(androidDeezerApi, /rank\s*=\s*rank/, 'DeezerApi on Android must map rank to Track');
    assert.match(androidArtistScreen, /track\.rank/, 'ArtistScreen on Android must use track.rank for stream count');
  });

  await t.test('3. PC Artist Screen has phone-style hero header, tabs, and plays count', () => {
    // HTML structure matching mobile
    assert.match(indexHtml, /class="artist-hero-wrap"/, 'Must have phone-style hero wrapper');
    assert.match(indexHtml, /id="artist-hero-bg"/, 'Must have hero background element');
    assert.match(indexHtml, /class="artist-verified-badge"/, 'Must have verified artist badge');
    assert.match(indexHtml, /class="artist-play-pill"/, 'Must have play pill button');
    assert.match(indexHtml, /class="artist-tabs-row"/, 'Must have tab row');
    assert.match(indexHtml, /data-tab="overview"/, 'Must have overview tab');
    assert.match(indexHtml, /data-tab="albums"/, 'Must have albums tab');
    assert.match(indexHtml, /data-tab="singles"/, 'Must have singles tab');
    assert.match(indexHtml, /data-tab="similar"/, 'Must have similar tab');
    assert.match(indexHtml, /id="artist-releases-carousel"/, 'Must have releases carousel');
    assert.match(indexHtml, /id="artist-similar-carousel"/, 'Must have similar artists carousel');

    // CSS styling
    assert.match(themeCss, /\.artist-hero-wrap/, 'CSS must style .artist-hero-wrap');
    assert.match(themeCss, /\.artist-verified-badge/, 'CSS must style .artist-verified-badge');
    assert.match(themeCss, /\.artist-play-pill/, 'CSS must style .artist-play-pill');
    assert.match(themeCss, /\.artist-tabs-row/, 'CSS must style .artist-tabs-row');

    // Frontend JS logic
    assert.match(mainJs, /function\s+formatPlays\s*\(/, 'Must define formatPlays function');
    assert.match(mainJs, /openArtistPage/, 'Must define openArtistPage function');
    assert.match(mainJs, /artist-tab-btn/, 'Must wire tab switching for artist page');
    assert.match(mainJs, /track-plays/, 'Must render track plays count under title');
  });

  await t.test('4. PC Home Screen blocks match mobile HomeScreen.kt ("Для вас" with "Треки", "Плейлисты", "Артисты", "Релизы")', () => {
    assert.match(indexHtml, /id="home-for-you-section"[\s\S]*?Для вас[\s\S]*?Треки/, 'Home screen "Для вас" section must display "Треки" sublabel');
    assert.match(indexHtml, /id="home-playlists-section"[\s\S]*?Плейлисты/, 'Home screen must contain "Плейлисты" section');
    assert.match(indexHtml, /id="home-artists-section"[\s\S]*?Артисты/, 'Home screen must contain "Артисты" section');
    assert.match(indexHtml, /id="home-releases-section"[\s\S]*?Релизы/, 'Home screen must contain "Релизы" section');
  });
});
