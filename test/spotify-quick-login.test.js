const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

test('Spotify-style quick login screen structure and styling', () => {
  const html = fs.readFileSync(path.join(__dirname, '..', 'src', 'index.html'), 'utf8');
  const css = fs.readFileSync(path.join(__dirname, '..', 'src', 'styles.css'), 'utf8');
  const firebaseClient = fs.readFileSync(path.join(__dirname, '..', 'src', 'firebase-client.js'), 'utf8');

  // 1. Structure in index.html
  assert.ok(html.includes('id="auth-quick-login"'), 'Must have auth-quick-login form');
  assert.ok(html.includes('id="quick-login-avatar"'), 'Must have circular avatar container');
  assert.ok(html.includes('id="quick-login-name"'), 'Must have quick login username');
  assert.ok(html.includes('id="quick-login-email"'), 'Must have quick login email');
  assert.ok(html.includes('id="quick-login-password"'), 'Must have quick login password input');
  assert.ok(html.includes('id="quick-login-btn"'), 'Must have quick login button');
  assert.ok(html.includes('id="quick-login-switch"'), 'Must have switch account link');

  // 2. Strictly NO logo on top-left per user requirement
  assert.ok(css.includes('.auth-overlay .auth-logo {\n  display: none !important;'), 'Auth logo must be hidden');
  assert.ok(html.includes('class="auth-top-left"'), 'Auth top left bar must not contain a logo element');
  assert.ok(!html.includes('class="auth-top-left"><img'), 'Auth top left must not have logo image');

  // 3. Top bar window controls
  assert.ok(html.includes('id="auth-tb-min"'), 'Must have minimize control');
  assert.ok(html.includes('id="auth-tb-max"'), 'Must have maximize control');
  assert.ok(html.includes('id="auth-tb-close"'), 'Must have close control');

  // 4. Firebase client logic
  assert.ok(firebaseClient.includes('getSavedLoginUser'), 'Must define getSavedLoginUser');
  assert.ok(firebaseClient.includes('updateQuickLoginUi'), 'Must define updateQuickLoginUi');
  assert.ok(firebaseClient.includes('auth-quick-login'), 'showAuthForm must handle auth-quick-login');
  assert.ok(firebaseClient.includes('quick-login-password'), 'Must handle quick login password interaction');
  assert.ok(firebaseClient.includes('quick-login-btn'), 'Must handle quick login submit');
});
