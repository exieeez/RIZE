const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const root = path.join(__dirname, '..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');

test('PC titlebar logo is removed from HTML and hidden in CSS', () => {
  const html = read('src/index.html');
  const css = read('src/styles.css');

  // Titlebar logo span element removed from header
  assert.doesNotMatch(html, /<span class="titlebar-logo" id="titlebar-logo"/);
  // CSS hides .titlebar-logo
  assert.match(css, /\.titlebar-logo\s*\{[\s\S]*display:\s*none\s*!important/);
});

test('No fake tracks or fake artists are injected into recent sections when history is empty', () => {
  const main = read('src/main.js');

  const homeContentMatch = main.match(/async function loadHomeContent\(\)\s*\{([\s\S]*?)\n\}/);
  assert.ok(homeContentMatch, 'loadHomeContent function found');
  const homeContentBody = homeContentMatch[1];

  // loadHomeContent does not fetch search or insert mock tracks when history is empty
  assert.doesNotMatch(homeContentBody, /fetch\('\/api\/search\?q='/);
  assert.doesNotMatch(homeContentBody, /SPOTIFY_MOCK_DATA/);
  assert.doesNotMatch(homeContentBody, /The Weeknd/);

  // In renderRecentArtists, empty recent artists hide section and do not inject fake artists
  const recentArtistsMatch = main.match(/function renderRecentArtists\(\)\s*\{([\s\S]*?)\n\}/);
  assert.ok(recentArtistsMatch, 'renderRecentArtists function found');
  const recentArtistsBody = recentArtistsMatch[1];
  assert.doesNotMatch(recentArtistsBody, /Стеклянный Оркестр/);
  assert.doesNotMatch(recentArtistsBody, /Кассетный Дом/);
  assert.doesNotMatch(recentArtistsBody, /Ночной Рейс/);
});

test('Bottom floating island player is hidden when empty and slides up on play', () => {
  const html = read('src/index.html');
  const css = read('src/styles.css');
  const main = read('src/main.js');

  // HTML initializes floating island as hidden
  assert.match(
    html,
    /id="floating-island"[^>]*player-island-hidden|class="[^"]*player-island-hidden[^"]*"[^>]*id="floating-island"/
  );

  // CSS specifies slide transition and offscreen translation when hidden
  assert.match(
    css,
    /\.floating-island\.player-island-hidden[\s\S]*translate:\s*-50%\s*calc\(100%\s*\+\s*48px\)/
  );
  assert.match(css, /\.floating-island\.player-island-visible[\s\S]*translate:\s*-50%\s*0/);

  // JS has showFloatingPlayer / hideFloatingPlayer and calls showFloatingPlayer in playTrack
  assert.match(main, /function showFloatingPlayer\(\)/);
  assert.match(main, /function hideFloatingPlayer\(\)/);
  assert.match(main, /async function playTrack\([\s\S]*showFloatingPlayer\(\)/);
});

test('Desktop settings modal is redesigned with mobile-style Stitch cards and typography', () => {
  const themeCss = read('src/styles/dotify-23-theme.css');

  // Clean dark background without green color-mix wash
  assert.match(themeCss, /\.dotify-23-settings-modal[\s\S]*background:\s*#111114/);

  // Settings sections styled as rounded dark cards
  assert.match(
    themeCss,
    /\.dotify-section\s*\{[\s\S]*background:\s*#18181b[\s\S]*border-radius:\s*18px/
  );

  // Section titles styled as uppercase secondary labels
  assert.match(
    themeCss,
    /\.dotify-section-title\s*\{[\s\S]*text-transform:\s*uppercase[\s\S]*color:\s*#8E8E93/
  );

  // Navigation pill tab active state is crisp white/contrast
  assert.match(
    themeCss,
    /\.dotify-pill-tab\.active[\s\S]*background:\s*#ffffff[\s\S]*color:\s*#000000/
  );

  // Toggle switch has iOS-style clean green active slider
  assert.match(
    themeCss,
    /\.dotify-switch input:checked \+ \.dotify-switch-slider\s*\{[\s\S]*background-color:\s*#34C759/
  );
});

test('Favorites card on Home is redesigned matching mobile QuickTile and other objects transparency', () => {
  const html = read('src/index.html');
  const css = read('src/styles.css');
  const themeCss = read('src/styles/dotify-23-theme.css');
  const main = read('src/main.js');

  // Spotify purple-to-mint gradient icon is completely removed
  assert.doesNotMatch(html, /linear-gradient\(135deg,\s*#450af5,\s*#c4efd9\)/);

  // Votify QuickTile structure matching mobile HomeScreen
  assert.match(html, /class="votify-quick-tile"[^>]*id="tile-liked"/);
  assert.match(html, /class="votify-quick-tile-icon"/);
  assert.match(html, /class="votify-quick-tile-title"/);
  assert.match(html, /id="fav-subtitle"/);

  // Play button is removed from the card
  assert.doesNotMatch(html, /id="tile-liked-play-btn"/);

  // CSS defines surface with 18px radius
  assert.match(themeCss, /\.votify-quick-tile[\s\S]*border-radius:\s*18px/);

  // Both styles.css and dotify-23-theme.css style votify-quick-tile in the same transparency style as other objects
  assert.match(css, /body\.transparency-enabled\s+#home-screen\s+\.votify-quick-tile/);
  assert.match(
    themeCss,
    /body\.transparency-enabled\s+#home-screen\s+\.votify-quick-tile[\s\S]*color-mix\(in srgb,\s*var\(--bg-surface/
  );
  assert.match(
    themeCss,
    /body\.transparency-enabled\s+#home-screen\s+\.votify-quick-tile[\s\S]*backdrop-filter:\s*blur/
  );

  // main.js updates subtitle count and opens favorites on tile click
  assert.match(main, /safeClick\('tile-liked'/);
  assert.match(main, /document\.getElementById\('fav-subtitle'\)/);
  assert.doesNotMatch(main, /safeClick\('tile-liked-play-btn'/);
});

test('Settings modal features Material Design 3 phone layout adapted for PC', () => {
  const html = read('src/index.html');
  const themeCss = read('src/styles/dotify-23-theme.css');
  const main = read('src/main.js');

  // Left master navigation with phone categories matching SettingsScreen.kt
  assert.match(html, /class="m3-settings-nav"\s+id="m3-settings-nav"/);
  assert.match(html, /class="m3-settings-title-pill">Настройки<\/div>/);
  assert.match(html, /class="m3-section-label">ОСНОВНЫЕ<\/div>/);
  assert.match(html, /class="m3-section-label">ВНЕШНИЙ ВИД<\/div>/);
  assert.match(html, /class="m3-section-label">КАСТОМИЗАЦИЯ<\/div>/);
  assert.match(html, /class="m3-section-label">ИНТЕГРАЦИИ<\/div>/);

  // Nav rows with icons, titles, and chevrons
  assert.match(html, /data-settings-section="gen-main"[\s\S]*Основные/);
  assert.match(html, /data-settings-section="app-player"[\s\S]*Плеер/);
  assert.match(html, /data-settings-section="app-ui"[\s\S]*Интерфейс/);

  // SettingsScaffold header with circle buttons and title pill
  assert.match(html, /id="m3-settings-back-btn"/);
  assert.match(html, /id="m3-active-category-title"/);
  assert.match(html, /id="m3-category-icon-pill"/);
  assert.match(html, /id="settings-close-btn"/);

  // M3 CSS tokens
  assert.match(themeCss, /\.m3-settings-modal[\s\S]*border-radius:\s*26px/);
  assert.match(themeCss, /\.m3-settings-nav\s*\{[\s\S]*width:\s*310px/);
  assert.match(themeCss, /\.m3-settings-circle-btn\s*\{[\s\S]*border-radius:\s*50%/);
  assert.match(themeCss, /\.m3-settings-title-pill\s*\{[\s\S]*border-radius:\s*22px/);
  assert.match(themeCss, /\.dotify-select\s*\{[\s\S]*border-radius:\s*12px/);

  // Responsive mobile transition
  assert.match(
    themeCss,
    /@media\s*\(max-width:\s*768px\)[\s\S]*\.m3-settings-modal\.detail-active\s+\.m3-settings-detail/
  );

  // Main.js wires up M3 navigation and category titles
  assert.match(main, /M3_SECTION_TITLES/);
  assert.match(main, /m3-settings-back-btn/);
  assert.match(main, /m3-active-category-title/);
});

test('Home screen redesign features My Wave hero card with dynamic smoke and wave adapting to accent, wide pill cards, and For You carousel', () => {
  const html = read('src/index.html');
  const themeCss = read('src/styles/dotify-23-theme.css');

  // Hero card structure
  assert.match(html, /id="my-wave-hero"|class="my-wave-hero-card"/);
  assert.match(html, /class="wave-smoke-glow"/);
  assert.match(html, /class="wave-stripes-svg"/);
  assert.match(html, /class="wave-stripe-path"/);
  assert.match(html, /class="wave-stripe-accent-loop"/);
  assert.match(html, /id="wave-hero-cover-img"/);
  assert.match(html, /class="my-wave-hero-title">Моя<br>волна<\/h1>/);
  assert.match(html, /id="my-wave-mode-badge"/);
  assert.match(html, /id="wave-thumb-top"/);
  assert.match(html, /id="wave-thumb-bottom"/);
  assert.match(html, /id="home-play-wave-btn"/);

  // Dynamic accent adaptation in CSS
  assert.match(themeCss, /\.wave-smoke-glow[\s\S]*color-mix\(in srgb,\s*var\(--accent\)/);
  assert.match(themeCss, /\.wave-stripe-path[\s\S]*stroke:\s*var\(--accent\)/);
  assert.match(
    themeCss,
    /\.my-wave-hero-play[\s\S]*background:\s*color-mix\(in srgb,\s*var\(--accent\)/
  );

  // Middle row wide pill banners
  assert.match(html, /class="home-quick-banner-row"/);
  assert.match(html, /id="tile-history"[\s\S]*История/);
  assert.match(html, /id="tile-liked"[\s\S]*Любимые треки/);
  assert.match(themeCss, /\.home-quick-banner-row[\s\S]*display:\s*flex/);
  assert.match(themeCss, /\.home-quick-banner-card[\s\S]*border-radius:\s*24px/);

  // For You section
  assert.match(html, /class="[^"]*for-you-heading[^"]*">Для вас<\/h2>/);
  assert.match(html, /id="for-you-results"/);
  assert.match(themeCss, /\.rec-grid\.for-you-carousel[\s\S]*overflow-x:\s*auto/);
  assert.match(themeCss, /\.dotify-source-badge[\s\S]*display:\s*none/);
});

test('My Wave hero adheres to transparent background, Roboto font, smooth bezier wave and larger rounded accent play triangle', () => {
  const html = read('src/index.html');
  const themeCss = read('src/styles/dotify-23-theme.css');

  // 1. Roboto font on hero title
  assert.match(themeCss, /\.my-wave-hero-title[\s\S]*font-family:[^;]*Roboto/);

  // 2. Transparent background, no card container box
  assert.match(themeCss, /\.my-wave-hero-card[\s\S]*background:\s*transparent/);
  assert.match(themeCss, /\.my-wave-hero-card[\s\S]*border:\s*none/);

  // 3. Play button with larger rounded triangle in accent color
  assert.match(html, /class="wave-play-svg"/);
  assert.match(themeCss, /\.my-wave-hero-play[\s\S]*color:\s*var\(--accent\)/);
  assert.match(themeCss, /\.my-wave-hero-play \.wave-play-svg[\s\S]*fill:\s*var\(--accent\)/);

  // 4. Smooth bezier wave path with no sharp angles
  assert.match(html, /class="wave-stripes-svg"[^>]*viewBox="0 0 524 250"/);
  assert.match(themeCss, /\.wave-stripe-path[\s\S]*stroke-linecap:\s*round/);
  assert.match(themeCss, /\.wave-stripe-path[\s\S]*stroke-linejoin:\s*round/);
});

test('Riff-style top header and mobile phone sections (Playlists, Popular Artists, Releases, Trending)', () => {
  const html = read('src/index.html');
  const themeCss = read('src/styles/dotify-23-theme.css');
  const main = read('src/main.js');

  // Riff header layout & profile pill button
  assert.match(html, /class="titlebar-top-row"/);
  assert.match(
    html,
    /class="app-logo"[\s\S]*class="logo-dot"[\s\S]*class="logo-text">Votify<\/span>/
  );
  assert.match(html, /class="top-nav"/);
  assert.match(html, /class="search-box"[\s\S]*id="tb-search-input"/);
  assert.match(html, /class="platform-pill-switch"[\s\S]*id="platform-yt"[\s\S]*id="platform-sc"/);
  assert.match(
    html,
    /class="top-nav-profile-container"[\s\S]*id="tb-avatar-btn"[\s\S]*id="tb-avatar-img"[\s\S]*id="tb-profile-name"/
  );

  // Phone home sections matching HomeScreen.kt
  assert.match(html, /id="home-playlists-section"[\s\S]*Плейлисты/);
  assert.match(html, /id="home-artists-section"[\s\S]*Популярные артисты/);
  assert.match(html, /id="home-releases-section"[\s\S]*Новые релизы/);
  assert.match(html, /id="home-trending-section"[\s\S]*В тренде/);

  // CSS rules
  assert.match(themeCss, /\.titlebar-strip[\s\S]*width:\s*100%/);
  assert.match(themeCss, /\.top-nav-profile-btn/);
  assert.match(themeCss, /\.artists-grid\.artists-carousel/);
  assert.match(themeCss, /\.artist-card-item/);
  assert.match(themeCss, /\.artist-avatar-wrap/);

  // Main.js loader functions
  assert.match(main, /loadHomePhonePlaylists/);
  assert.match(main, /loadHomePhoneArtists/);
  assert.match(main, /loadHomePhoneReleases/);
  assert.match(main, /loadHomePhoneTrending/);
  assert.match(main, /loadHomePhoneSections/);
});
