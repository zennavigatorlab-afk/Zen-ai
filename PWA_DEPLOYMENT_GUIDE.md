# ZEN AI – Progressive Web App (PWA) Deployment & Installation Guide

This project contains a fully functional, installable, and offline-first Progressive Web App (PWA) representing **ZEN AI – Personal Intelligence System**.

---

## 1. How to Run Locally

You can run the PWA locally using any static HTTP server:

### Option A: Node.js (Included with zero dependencies)
```bash
node server.js
```
The application will be live at `http://localhost:3000`.

### Option B: npm start / dev
```bash
npm start
# or
npm run dev
```

### Option C: Python 3
```bash
python3 -m http.server 3000 --bind 0.0.0.0
```

### Option D: npx serve
```bash
npx serve -l 3000 .
```

---

## 2. PWA Features & Architecture

- **Manifest (`manifest.webmanifest`)**:
  - `name`: "ZEN AI – Personal Intelligence System"
  - `short_name`: "ZEN AI"
  - `display`: `standalone` (launches in full-screen window without browser address bar)
  - `theme_color` & `background_color`: `#0B0F17` (Obsidian Minimal Dark Theme)
  - `icons`: 192x192, 512x512, and 512x512 maskable icon for Android adaptive icon rendering.
- **Service Worker (`sw.js`)**:
  - Pre-caches core app shell (`index.html`, `css/style.css`, `js/*.js`, `manifest.webmanifest`, `icons/*`).
  - Cache-first strategy for instant offline launch.
  - Background freshness updates.
- **In-App PWA Install Banner**:
  - Listens to the browser's `beforeinstallprompt` event.
  - Renders a prominent in-app **"Install"** button.
  - Gracefully hides once installed or if the browser does not support installation.
- **Local Persistence (IndexedDB + localStorage fallback)**:
  - 7 stores mirroring Room entities: `memories`, `conversations`, `messages`, `documents`, `sync_queue`, `backup_history`, `app_settings`.
  - Survives browser restarts and system reboots.
- **Hardware-Guarded Security (Web Crypto API)**:
  - `AES-256-GCM` encryption with 128-bit authentication tag.
  - Unique cryptographically secure 12-byte random nonces (IVs) generated per operation.
  - Cryptographic `SHA-256` checksum calculation and tamper verification.
  - Salted `PBKDF2WithHmacSHA256` password hashing (passphrases are never stored in plain text).
  - 12-word secure mnemonic recovery key generation.
- **File Manager**:
  - Uses the standard HTML5 File API and Web File System Access (`<input type="file" multiple>`).
  - Zero broad device storage permissions requested.
  - Reads real file metadata (size, MIME type, last modified date, and text preview).
- **Google Drive Vault Foundation**:
  - Accurately displays **"Not connected"**.
  - Provides OAuth 2.0 Web Client ID configuration instructions and conflict analysis.
- **Offline Local Intelligence**:
  - Real intent parser (Memory note capture `"remember that..."`, memory search, document lookup, system status).
  - Transparently marked as **100% Offline • Zero External Server Calls**.

---

## 3. How to Deploy Over HTTPS

PWAs require HTTPS (except on `localhost`) to register service workers and trigger the browser install flow.

### Option A: Firebase Hosting (Recommended)
1. Initialize Firebase:
   ```bash
   npm install -g firebase-tools
   firebase login
   firebase init hosting
   ```
2. Set public directory to `.` (current directory) and configure single-page app to `No`.
3. Deploy:
   ```bash
   firebase deploy --only hosting
   ```

### Option B: Cloudflare Pages
1. Push your repository to GitHub.
2. In Cloudflare Dashboard, go to **Workers & Pages > Create application > Pages**.
3. Select your repository.
4. Set build command to empty or `npm run build` and output directory to `/`.
5. Deploy.

### Option C: Vercel or Netlify
```bash
# Vercel:
npx vercel deploy --prod

# Netlify:
npx netlify deploy --prod --dir=.
```

---

## 4. How to Install on Android

1. **Via Google Chrome on Android**:
   - Navigate to your deployed HTTPS URL in Chrome.
   - You will see the in-app **"Install ZEN AI App"** banner at the top. Tap **Install**.
   - Alternatively, tap Chrome's menu (three dots) > **Add to Home screen** / **Install app**.
   - The app will install to the Android app drawer and home screen with the custom ZEN AI icon.
   - When launched, it opens in standalone mode without browser chrome, functioning completely offline.

2. **Via Microsoft Edge on Android**:
   - Open Edge on Android and visit the HTTPS URL.
   - Tap the bottom menu > **Add to phone** > **Install**.

3. **Via Safari on iOS**:
   - Open the URL in Safari.
   - Tap the **Share** button > **Add to Home Screen**.
