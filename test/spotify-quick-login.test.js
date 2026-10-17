const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');

test('Spotify-style two-step quick login screen structure and styling', () => {
  const html = fs.readFileSync(path.join(__dirname, '..', 'src', 'index.html'), 'utf8');
  const css = fs.readFileSync(path.join(__dirname, '..', 'src', 'styles.css'), 'utf8');
  const firebaseClient = fs.readFileSync(path.join(__dirname, '..', 'src', 'firebase-client.js'), 'utf8');
  const authRoutes = fs.readFileSync(path.join(__dirname, '..', 'routes', 'auth.js'), 'utf8');

  // 1. Structure in index.html: Email Step + Quick Login Password Step
  assert.ok(html.includes('id="auth-email-step"'), 'Must have auth-email-step form');
  assert.ok(html.includes('id="auth-initial-email"'), 'Must have email input for step 1');
  assert.ok(html.includes('id="auth-email-next-btn"'), 'Must have next button for step 1');
  assert.ok(html.includes('id="auth-quick-login"'), 'Must have auth-quick-login form for step 2');
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

  // 4. Two-step verification logic in client & server
  assert.ok(firebaseClient.includes('checkEmailExists'), 'Must define checkEmailExists');
  assert.ok(firebaseClient.includes('handleEmailNext'), 'Must define handleEmailNext');
  assert.ok(firebaseClient.includes('auth-email-step'), 'showAuthForm must handle auth-email-step');
  assert.ok(firebaseClient.includes('auth-quick-login'), 'showAuthForm must handle auth-quick-login');
  assert.ok(firebaseClient.includes('register-email'), 'Must pre-fill register-email when account not found');
  assert.ok(authRoutes.includes('/api/auth/check-email'), 'Must support /api/auth/check-email endpoint');
});
