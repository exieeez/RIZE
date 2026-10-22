const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

test('App Icon Switcher: Assets, UI, Styling, and Logic Integrity', () => {
  const root = path.join(__dirname, '..');
  const indexHtml = fs.readFileSync(path.join(root, 'src/index.html'), 'utf8');
  const stylesCss = fs.readFileSync(path.join(root, 'src/styles.css'), 'utf8');
  const appJs = fs.readFileSync(path.join(root, 'src/app.js'), 'utf8');
  const mainJs = fs.readFileSync(path.join(root, 'main.js'), 'utf8');
  const preloadJs = fs.readFileSync(path.join(root, 'preload.js'), 'utf8');

  // 1. Verify all 5 new variants + default exist on disk
  const iconVariants = ['crimson', 'mono', 'mint', 'lime', 'violet', 'default'];
  for (const variant of iconVariants) {
    const iconFile = path.join(root, 'src/assets/icons', `icon-${variant}.png`);
    assert.ok(fs.existsSync(iconFile), `Icon file icon-${variant}.png must exist`);
    const stat = fs.statSync(iconFile);
    assert.ok(stat.size > 1000, `Icon file icon-${variant}.png must have valid non-empty size`);
  }

  // 2. Verify HTML markup in settings
  assert.match(indexHtml, /id="setting-app-icon-row"/, 'Settings must contain setting-app-icon-row');
  assert.match(indexHtml, /id="app-icon-grid"/, 'Settings must contain app-icon-grid');
  assert.match(indexHtml, /id="btn-custom-app-icon"/, 'Settings must contain custom icon upload button');
  assert.match(indexHtml, /id="input-custom-app-icon"/, 'Settings must contain custom icon file input');

  for (const variant of iconVariants) {
    assert.match(indexHtml, new RegExp(`data-icon="${variant}"`), `HTML must contain card for ${variant}`);
  }

  // 3. Verify CSS rules
  assert.match(stylesCss, /\.app-icon-grid\s*\{[^}]*display:\s*grid/);
  assert.match(stylesCss, /\.app-icon-card/);
  assert.match(stylesCss, /\.app-icon-preview/);
  assert.match(stylesCss, /\.app-icon-card\.active/);

  // 4. Verify JS switching logic
  assert.match(appJs, /const APP_ICONS\s*=/);
  assert.match(appJs, /function applyAppIcon/);
  assert.match(appJs, /votify_app_icon/);
  assert.match(appJs, /window\.applyAppIcon\s*=\s*applyAppIcon/);

  // 5. Verify IPC bindings in electron
  assert.match(mainJs, /ipcMain\.handle\('set-app-icon'/);
  assert.match(preloadJs, /setAppIcon:\s*iconName/);
});
