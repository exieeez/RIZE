const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { handleAuthRoutes } = require('../routes/auth.js');

function mockRes() {
  let statusCode = 200;
  let headers = {};
  let body = '';
  return {
    writeHead(code, h) {
      statusCode = code;
      headers = { ...headers, ...h };
    },
    end(data) {
      body = data;
    },
    get statusCode() { return statusCode; },
    get headers() { return headers; },
    get body() { return body; },
    json() {
      try { return JSON.parse(body); } catch (e) { return null; }
    }
  };
}

test('POST /api/auth/google endpoint validates clientId and returns error when invalid', async () => {
  const orig = process.env.VOTIFY_GOOGLE_DESKTOP_CLIENT_ID;
  const origConf = process.env.VOTIFY_FIREBASE_CONFIG;
  try {
    process.env.VOTIFY_GOOGLE_DESKTOP_CLIENT_ID = 'invalid';
    process.env.VOTIFY_FIREBASE_CONFIG = JSON.stringify({ googleDesktopClientId: '' });
    const req = {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      on: (event, cb) => {
        if (event === 'end') cb();
        return req;
      }
    };
    const res = mockRes();
    const url = new URL('http://localhost:17217/api/auth/google');
    const handled = await handleAuthRoutes(req, res, url);
    assert.equal(handled, true, 'Route /api/auth/google should be handled');
    assert.equal(res.statusCode, 400);
    assert.match(res.json().error, /googleDesktopClientId/);
  } finally {
    if (orig !== undefined) process.env.VOTIFY_GOOGLE_DESKTOP_CLIENT_ID = orig;
    else delete process.env.VOTIFY_GOOGLE_DESKTOP_CLIENT_ID;
    if (origConf !== undefined) process.env.VOTIFY_FIREBASE_CONFIG = origConf;
    else delete process.env.VOTIFY_FIREBASE_CONFIG;
  }
});

test('firebase-client.js provides Google authentication without platform restriction error', () => {
  const clientCode = fs.readFileSync(path.join(__dirname, '../src/firebase-client.js'), 'utf8');
  assert.doesNotMatch(
    clientCode,
    /throw new Error\(['"]Вход через Google доступен только в приложении Votify['"]\)/,
    'Must not throw restriction error on Google sign in'
  );
  assert.match(
    clientCode,
    /\/api\/auth\/google/,
    'Client should support calling /api/auth/google'
  );
});
