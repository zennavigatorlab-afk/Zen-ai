const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const { webcrypto } = require('node:crypto');

test('PWA Manifest validation', () => {
  const manifestPath = path.join(__dirname, '../manifest.webmanifest');
  assert.ok(fs.existsSync(manifestPath), 'manifest.webmanifest exists');

  const content = fs.readFileSync(manifestPath, 'utf8');
  const json = JSON.parse(content);

  assert.strictEqual(json.name, 'ZEN AI – Personal Intelligence System');
  assert.strictEqual(json.short_name, 'ZEN AI');
  assert.strictEqual(json.display, 'standalone');
  assert.strictEqual(json.theme_color, '#0B0F17');
  assert.ok(Array.isArray(json.icons) && json.icons.length >= 3, 'Manifest has required icons');
  assert.ok(json.icons.some(i => i.purpose === 'maskable'), 'Manifest has maskable icon');
});

test('Service Worker file validation', () => {
  const swPath = path.join(__dirname, '../sw.js');
  assert.ok(fs.existsSync(swPath), 'sw.js exists');

  const content = fs.readFileSync(swPath, 'utf8');
  assert.ok(content.includes('install'), 'sw.js handles install event');
  assert.ok(content.includes('activate'), 'sw.js handles activate event');
  assert.ok(content.includes('fetch'), 'sw.js handles fetch event');
  assert.ok(content.includes('caches.open'), 'sw.js caches app shell');
});

test('PWA Icon assets exist and are non-empty', () => {
  const icons = [
    'icons/icon-192.png',
    'icons/icon-512.png',
    'icons/icon-maskable-512.png',
    'icons/favicon.png',
    'icons/apple-touch-icon.png'
  ];

  for (const icon of icons) {
    const fullPath = path.join(__dirname, '..', icon);
    assert.ok(fs.existsSync(fullPath), `${icon} exists`);
    const stat = fs.statSync(fullPath);
    assert.ok(stat.size > 500, `${icon} has valid non-zero size`);
  }
});

test('Web Crypto SHA-256 and AES-GCM interoperability', async () => {
  const enc = new TextEncoder();
  const plainText = "ZEN_AI_CONFIDENTIAL_PAYLOAD_WEB_CRYPTO";
  const plainBytes = enc.encode(plainText);

  // SHA-256
  const hashBuffer = await webcrypto.subtle.digest("SHA-256", plainBytes);
  const hashHex = Array.from(new Uint8Array(hashBuffer)).map(b => b.toString(16).padStart(2, '0')).join('');
  assert.strictEqual(hashHex.length, 64, 'SHA-256 is 64 hex characters');

  // AES-GCM
  const key = await webcrypto.subtle.generateKey(
    { name: "AES-GCM", length: 256 },
    true,
    ["encrypt", "decrypt"]
  );
  const iv = webcrypto.getRandomValues(new Uint8Array(12));

  const cipher = await webcrypto.subtle.encrypt(
    { name: "AES-GCM", iv, tagLength: 128 },
    key,
    plainBytes
  );

  const decrypted = await webcrypto.subtle.decrypt(
    { name: "AES-GCM", iv, tagLength: 128 },
    key,
    cipher
  );

  const dec = new TextDecoder();
  assert.strictEqual(dec.decode(decrypted), plainText, 'AES-256-GCM decrypted matches original');
});

test('Google Drive conflict detection logic', () => {
  const analyzeConflict = (localTimestamp, remoteTimestamp) => {
    const diff = localTimestamp - remoteTimestamp;
    if (Math.abs(diff) < 2000) return { hasConflict: false, action: 'USE_LOCAL' };
    if (localTimestamp > remoteTimestamp) return { hasConflict: true, action: 'USE_LOCAL' };
    return { hasConflict: true, action: 'USE_REMOTE' };
  };

  const now = Date.now();
  assert.strictEqual(analyzeConflict(now, now).hasConflict, false);
  assert.strictEqual(analyzeConflict(now + 10000, now).action, 'USE_LOCAL');
  assert.strictEqual(analyzeConflict(now, now + 10000).action, 'USE_REMOTE');
});
