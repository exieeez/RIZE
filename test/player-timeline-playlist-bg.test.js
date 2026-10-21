const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

test('Player timeline seeking, playlist pure black background, and titlebar settings button left of avatar', () => {
  const root = path.join(__dirname, '..');
  const indexHtml = fs.readFileSync(path.join(root, 'src/index.html'), 'utf8');
  const themeCss = fs.readFileSync(path.join(root, 'src/styles/dotify-23-theme.css'), 'utf8');
  const stylesCss = fs.readFileSync(path.join(root, 'src/styles.css'), 'utf8');
  const mainJs = fs.readFileSync(path.join(root, 'src/main.js'), 'utf8');

  // 1. Settings button positioned to the left of avatar button in index.html
  assert.ok(indexHtml.includes('id="tb-settings-btn"'), 'tb-settings-btn exists in HTML');
  assert.ok(indexHtml.includes('id="tb-avatar-btn"'), 'tb-avatar-btn exists in HTML');
  const settingsIndex = indexHtml.indexOf('id="tb-settings-btn"');
  const avatarIndex = indexHtml.indexOf('id="tb-avatar-btn"');
  assert.ok(settingsIndex < avatarIndex, 'tb-settings-btn must appear before tb-avatar-btn in top nav');
  assert.ok(indexHtml.includes('top-nav-actions-group'), 'actions group wrapper exists');

  // 2. Settings button styling in theme CSS
  assert.match(themeCss, /\.top-nav-settings-btn[\s\S]*border-radius:\s*50%/);
  assert.match(themeCss, /\.top-nav-settings-btn[\s\S]*background:\s*rgba\(255,\s*255,\s*255/);

  // 3. Settings button wired to toggleSettingsOverlay in main.js
  assert.match(mainJs, /const tbSettingsEl = document\.getElementById\('tb-settings-btn'\);/);
  assert.match(mainJs, /tbSettingsEl\.onclick[\s\S]*toggleSettingsOverlay\(\);/);

  // 4. Playlist screen dynamic/transparent background adhering to theme
  assert.match(themeCss, /#playlist-screen[\s\S]*background:\s*transparent/);
  assert.match(themeCss, /#playlist-screen[\s\S]*background-color:\s*transparent/);
  assert.match(stylesCss, /#playlist-screen[\s\S]*background:\s*transparent/);

  // 5. Full player timeline (#page-player-progress) styling: track with gradient and thumb visible on interaction
  assert.match(themeCss, /#page-player-progress::-webkit-slider-runnable-track[\s\S]*background:\s*linear-gradient/);
  assert.match(themeCss, /#page-player-progress::-webkit-slider-thumb[\s\S]*border-radius:\s*50%/);
  assert.match(themeCss, /\.pp-timeline-bar:hover #page-player-progress::-webkit-slider-thumb[\s\S]*opacity:\s*1\s*!important/);

  // 6. Thumb must NOT be hidden in normal/default mode for #page-player-progress
  const normalThumbRuleMatch = themeCss.match(/body\[data-player-slider-type="normal"\][\s\S]*?\{[\s\S]*?display:\s*none\s*!important;/);
  if (normalThumbRuleMatch) {
    assert.ok(!normalThumbRuleMatch[0].includes('#page-player-progress::-webkit-slider-thumb'), 'Thumb should not be hidden for #page-player-progress');
  }

  // 7. Timeline interactive seeking in main.js
  assert.match(mainJs, /ppProgress\.addEventListener\('input'/);
  assert.match(mainJs, /ppProgress\.addEventListener\('change'/);
  assert.match(mainJs, /ppTimelineBar\.addEventListener\('click'/);
  assert.match(mainJs, /on\('state:duration'/);
});
