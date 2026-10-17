const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

test('Home track and playlist cards match mobile phone design from HomeScreen.kt', () => {
  const root = path.join(__dirname, '..');
  const themeCss = fs.readFileSync(path.join(root, 'src/styles/dotify-23-theme.css'), 'utf8');
  const main = fs.readFileSync(path.join(root, 'src/main.js'), 'utf8');

  // 1. Column card width: 135px (matching Modifier.width(135.dp))
  assert.match(themeCss, /width:\s*135px\s*!important/);
  assert.match(themeCss, /flex:\s*0\s+0\s+135px\s*!important/);

  // 2. Artwork size: 135px with 16px border-radius (matching Modifier.size(135.dp).clip(RoundedCornerShape(16.dp)))
  assert.match(themeCss, /\.dotify-card-cover-wrap[\s\S]*width:\s*135px\s*!important/);
  assert.match(themeCss, /\.dotify-card-cover-wrap[\s\S]*height:\s*135px\s*!important/);
  assert.match(themeCss, /\.dotify-card-cover-wrap[\s\S]*border-radius:\s*16px\s*!important/);
  assert.match(themeCss, /\.rec-tile-cover[\s\S]*border-radius:\s*16px\s*!important/);

  // 3. Carousel gap: 14px (matching Arrangement.spacedBy(14.dp))
  assert.match(themeCss, /\.rec-grid\.for-you-carousel[\s\S]*gap:\s*14px\s*!important/);

  // 4. Spacer between artwork and text: 8px (matching Spacer(Modifier.height(8.dp)))
  assert.match(themeCss, /\.dotify-card-cover-wrap[\s\S]*margin-bottom:\s*8px\s*!important/);

  // 5. Typography: title 14px semi-bold, subtitle 12px (matching 14.sp and 12.sp)
  assert.match(themeCss, /\.rec-tile-title[\s\S]*font-size:\s*14px\s*!important/);
  assert.match(themeCss, /\.rec-tile-title[\s\S]*font-weight:\s*600\s*!important/);
  assert.match(themeCss, /\.rec-tile-artist[\s\S]*font-size:\s*12px\s*!important/);

  // 6. Transparent card container (no artificial dark box around column)
  assert.match(themeCss, /\.rec-tile[\s\S]*background:\s*transparent\s*!important/);
  assert.match(themeCss, /\.rec-tile[\s\S]*border:\s*none\s*!important/);

  // 7. No green circle play button hovering over card on phone
  assert.match(themeCss, /\.rec-tile-play[\s\S]*display:\s*none\s*!important/);

  // 8. Placeholders exist for both tracks and playlists in main.js
  assert.match(main, /class="playlist-cover-placeholder"><i class="material-icons">queue_music<\/i><\/div>/);
  assert.match(main, /class="playlist-cover-placeholder"><i class="material-icons">music_note<\/i><\/div>/);
});
