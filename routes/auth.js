const {
  parseBody,
  sendJson,
  getAuthUser,
  generateToken,
  loadUsers,
  saveUsers,
  bcrypt,
  SALT_ROUNDS,
  generateResetCode,
  createEmailTransporter,
  resetCodes,
} = require('./utils.js');

const fs = require('fs');
const path = require('path');
const PERSISTENT_DIR = require('./utils.js').PERSISTENT_DIR;

let smtpConfig = null;
function loadSmtpConfig() {
  try {
    const smtpFile = path.join(PERSISTENT_DIR, 'smtp.json');
    if (fs.existsSync(smtpFile)) {
      smtpConfig = JSON.parse(fs.readFileSync(smtpFile, 'utf-8'));
    }
  } catch (e) {
    /* ignore */
  }
}
loadSmtpConfig();

let googleAuthPromise = null;

function getGoogleDesktopCredentials() {
  let clientId = process.env.VOTIFY_GOOGLE_DESKTOP_CLIENT_ID || '';
  let clientSecret = process.env.VOTIFY_GOOGLE_DESKTOP_CLIENT_SECRET || '';
  let config = null;
  if (process.env.VOTIFY_FIREBASE_CONFIG) {
    try {
      config = JSON.parse(process.env.VOTIFY_FIREBASE_CONFIG);
    } catch (e) {}
  }
  if (!config) {
    try {
      const configPath = path.join(__dirname, '..', 'firebase-config.json');
      if (fs.existsSync(configPath)) {
        config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
      }
    } catch (e) {}
  }
  clientId = clientId || config?.googleDesktopClientId || '';
  clientSecret = clientSecret || config?.googleDesktopClientSecret || '';
  const { isGoogleClientId } = require('../google-oauth.js');
  const normalizedClientId = String(clientId).trim();
  return {
    clientId: isGoogleClientId(normalizedClientId) ? normalizedClientId : '',
    clientSecret: String(clientSecret).trim(),
  };
}

async function handleAuthRoutes(req, res, u) {
  // --- GOOGLE OAUTH ---
  if (u.pathname === '/api/auth/google' && req.method === 'POST') {
    const { startGoogleOAuth } = require('../google-oauth.js');
    const { clientId, clientSecret } = getGoogleDesktopCredentials();
    if (!clientId) {
      sendJson(res, 400, {
        error: 'Добавьте googleDesktopClientId из Google Cloud в firebase-config.json',
      });
      return true;
    }

    if (!googleAuthPromise) {
      googleAuthPromise = startGoogleOAuth({
        clientId,
        clientSecret,
        openExternal: url => {
          const { exec } = require('child_process');
          const isWindows = process.platform === 'win32';
          const isMac = process.platform === 'darwin';
          const cmd = isWindows ? `start "" "${url}"` : isMac ? `open "${url}"` : `xdg-open "${url}"`;
          exec(cmd, err => {
            if (err) console.warn('[google-auth] Failed to open external browser:', err.message);
          });
        },
      }).finally(() => {
        googleAuthPromise = null;
      });
    }

    try {
      const tokens = await googleAuthPromise;
      sendJson(res, 200, { tokens });
    } catch (error) {
      sendJson(res, 500, { error: error.message || 'Не удалось выполнить вход через Google' });
    }
    return true;
  }

  // --- REGISTER ---
  if (u.pathname === '/api/auth/register' && req.method === 'POST') {
    const { email, password, username } = await parseBody(req);
    if (!email || !password) {
      sendJson(res, 400, { error: 'Email and password required' });
      return true;
    }

    const users = loadUsers();
    if (users.find(u => u.email === email.toLowerCase())) {
      sendJson(res, 409, { error: 'User already exists' });
      return true;
    }
    const hash = await bcrypt.hash(password, SALT_ROUNDS);
    const user = {
      id: Date.now().toString(36) + Math.random().toString(36).slice(2),
      email: email.toLowerCase(),
      username: username || email.split('@')[0],
      password: hash,
      createdAt: Date.now(),
    };
    users.push(user);
    saveUsers(users);
    const token = generateToken(user.id, user.email);
    sendJson(res, 200, {
      token,
      user: { id: user.id, email: user.email, username: user.username },
    });
    return true;
  }

  // --- LOGIN ---
  if (u.pathname === '/api/auth/login' && req.method === 'POST') {
    const { email, password } = await parseBody(req);
    if (!email || !password) {
      sendJson(res, 400, { error: 'Email and password required' });
      return true;
    }

    const users = loadUsers();
    const user = users.find(u => u.email === email.toLowerCase());
    if (!user) {
      sendJson(res, 401, { error: 'Invalid credentials' });
      return true;
    }
    const valid = await bcrypt.compare(password, user.password);
    if (!valid) {
      sendJson(res, 401, { error: 'Invalid credentials' });
      return true;
    }
    const token = generateToken(user.id, user.email);
    sendJson(res, 200, {
      token,
      user: { id: user.id, email: user.email, username: user.username },
    });
    return true;
  }

  // --- ME ---
  if (u.pathname === '/api/auth/me') {
    const auth = getAuthUser(req);
    if (!auth) {
      sendJson(res, 401, { error: 'Not authenticated' });
      return true;
    }

    const users = loadUsers();
    const user = users.find(u => u.id === auth.userId);
    if (!user) {
      sendJson(res, 404, { error: 'User not found' });
      return true;
    }
    sendJson(res, 200, { user: { id: user.id, email: user.email, username: user.username } });
    return true;
  }

  // --- FORGOT PASSWORD ---
  if (u.pathname === '/api/auth/forgot-password' && req.method === 'POST') {
    const { email } = await parseBody(req);
    if (!email) {
      sendJson(res, 400, { error: 'Email required' });
      return true;
    }

    const users = loadUsers();
    const user = users.find(u => u.email === email.toLowerCase());
    if (!user) {
      sendJson(res, 200, { message: 'If this email exists, a code was sent' });
      return true;
    }
    const code = generateResetCode();
    resetCodes.set(email.toLowerCase(), { code, expires: Date.now() + 15 * 60 * 1000 });
    if (smtpConfig) {
      try {
        const transporter = createEmailTransporter(smtpConfig);
        await transporter.sendMail({
          from: smtpConfig.from || smtpConfig.user,
          to: email,
          subject: 'Votify - Password Reset Code',
          text: `Your password reset code is: ${code}`,
          html: `<div style="font-family:sans-serif;padding:20px;"><h2 style="color:#6750A4;">Votify</h2><p>Your password reset code:</p><h1 style="font-size:48px;color:#6750A4;letter-spacing:8px;">${code}</h1></div>`,
        });
      } catch (err) {
        /* ignore email transport failure */
      }
    }
    sendJson(res, 200, { message: 'If this email exists, a code was sent', code: code });
    return true;
  }

  // --- RESET PASSWORD ---
  if (u.pathname === '/api/auth/reset-password' && req.method === 'POST') {
    const { email, code, newPassword } = await parseBody(req);
    if (!email || !code || !newPassword) {
      sendJson(res, 400, { error: 'All fields required' });
      return true;
    }
    const stored = resetCodes.get(email.toLowerCase());
    if (!stored || stored.code !== code || stored.expires < Date.now()) {
      sendJson(res, 400, { error: 'Invalid or expired code' });
      return true;
    }
    resetCodes.delete(email.toLowerCase());

    const users = loadUsers();
    const user = users.find(u => u.email === email.toLowerCase());
    if (!user) {
      sendJson(res, 404, { error: 'User not found' });
      return true;
    }
    user.password = await bcrypt.hash(newPassword, SALT_ROUNDS);
    saveUsers(users);
    const token = generateToken(user.id, user.email);
    sendJson(res, 200, {
      message: 'Password reset successful',
      token,
      user: { id: user.id, email: user.email, username: user.username },
    });
    return true;
  }

  // --- UPDATE PASSWORD (authenticated user) ---
  if (u.pathname === '/api/auth/update-password' && req.method === 'POST') {
    const { newPassword } = await parseBody(req);
    if (!newPassword) {
      sendJson(res, 400, { error: 'newPassword required' });
      return true;
    }
    const auth = getAuthUser(req);
    if (!auth) {
      sendJson(res, 401, { error: 'Not authenticated' });
      return true;
    }
    const users = loadUsers();
    const user = users.find(u => u.id === auth.userId);
    if (!user) {
      sendJson(res, 404, { error: 'User not found' });
      return true;
    }
    user.password = await bcrypt.hash(newPassword, SALT_ROUNDS);
    saveUsers(users);
    sendJson(res, 200, { message: 'Password updated' });
    return true;
  }

  // --- LOGOUT ---
  if (u.pathname === '/api/auth/logout' && req.method === 'POST') {
    sendJson(res, 200, { message: 'Logged out' });
    return true;
  }

  return false;
}

module.exports = { handleAuthRoutes };
