/**
 * ZEN AI – Personal Intelligence System
 * Primary PWA Controller & Application Logic
 */

let deferredInstallPrompt = null;
let currentSection = 'dashboard';
let activeConversationId = null;
let activeMemoryCategory = 'All';

document.addEventListener('DOMContentLoaded', async () => {
  // 1. Initialize Database
  await window.zenDb.init();

  // 2. Initialize Service Worker
  registerServiceWorker();

  // 3. Setup PWA Install Listener
  setupPwaInstall();

  // 4. Setup Online/Offline Status Listeners
  setupNetworkListeners();

  // 5. Initialize Theme
  initTheme();

  // 6. Bind UI Events
  bindNavigationEvents();
  bindActionEvents();

  // 7. Load Initial Data
  await refreshTelemetry();
  await ensureDefaultConversation();
  await loadMemories();
  await loadDocuments();
  await loadBackupHistory();

  // 8. Handle Hash Routing
  handleHashRoute();
  window.addEventListener('hashchange', handleHashRoute);
});

// --- Service Worker Registration ---
function registerServiceWorker() {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js')
      .then((reg) => {
        console.log('[ZEN AI] Service Worker active with scope:', reg.scope);
      })
      .catch((err) => {
        console.warn('[ZEN AI] Service Worker registration failed:', err);
      });
  }
}

// --- PWA Installation Flow ---
function setupPwaInstall() {
  const installBanner = document.getElementById('install-banner');
  const installBtn = document.getElementById('btn-install-pwa');

  window.addEventListener('beforeinstallprompt', (e) => {
    // Prevent automated mini-infobar in mobile browsers
    e.preventDefault();
    deferredInstallPrompt = e;
    if (installBanner) {
      installBanner.classList.add('active');
    }
  });

  if (installBtn) {
    installBtn.addEventListener('click', async () => {
      if (!deferredInstallPrompt) return;
      deferredInstallPrompt.prompt();
      const { outcome } = await deferredInstallPrompt.userChoice;
      console.log(`[ZEN AI] Install choice: ${outcome}`);
      deferredInstallPrompt = null;
      if (installBanner) installBanner.classList.remove('active');
    });
  }

  window.addEventListener('appinstalled', () => {
    showToast('ZEN AI was installed successfully!');
    if (installBanner) installBanner.classList.remove('active');
    deferredInstallPrompt = null;
  });
}

// --- Network Status ---
function setupNetworkListeners() {
  const banner = document.getElementById('network-banner');
  const updateNetworkUI = () => {
    const isOnline = navigator.onLine;
    const telemetryNetwork = document.getElementById('telemetry-network-val');
    const telemetrySub = document.getElementById('telemetry-network-sub');
    const net = TelemetryManager.getNetworkTelemetry();

    if (telemetryNetwork) telemetryNetwork.textContent = net.isOnline ? "Online" : "Offline";
    if (telemetrySub) telemetrySub.textContent = net.networkType;

    if (!isOnline && banner) {
      banner.textContent = "You are currently offline. Local intelligence, encrypted memories, and indexed files remain fully accessible.";
      banner.classList.add('active');
    } else if (banner) {
      banner.classList.remove('active');
    }
  };

  window.addEventListener('online', updateNetworkUI);
  window.addEventListener('offline', updateNetworkUI);
  updateNetworkUI();
}

// --- Navigation & Routing ---
function handleHashRoute() {
  const hash = window.location.hash;
  const match = hash.match(/section=([a-z_]+)/);
  if (match && match[1]) {
    navigateTo(match[1]);
  } else {
    navigateTo('dashboard');
  }
}

function navigateTo(sectionName) {
  currentSection = sectionName;
  document.querySelectorAll('.section-view').forEach(el => el.classList.remove('active'));

  const target = document.getElementById(`section-${sectionName}`);
  if (target) {
    target.classList.add('active');
  } else {
    document.getElementById('section-dashboard').classList.add('active');
    currentSection = 'dashboard';
  }

  // Update Top Bar
  const backBtn = document.getElementById('btn-nav-back');
  const brandTitle = document.getElementById('top-brand-title');
  if (currentSection === 'dashboard') {
    if (backBtn) backBtn.style.display = 'none';
    if (brandTitle) brandTitle.textContent = 'ZEN AI';
  } else {
    if (backBtn) backBtn.style.display = 'flex';
    if (brandTitle) brandTitle.textContent = getSectionTitle(currentSection);
  }

  window.scrollTo({ top: 0, behavior: 'instant' });
}

function getSectionTitle(name) {
  const titles = {
    'assistant': 'AI Assistant',
    'memory': 'Personal Memory',
    'conversations': 'Conversations',
    'files': 'File Manager',
    'knowledge': 'Knowledge Library',
    'phone': 'Phone & Web Actions',
    'vault': 'Google Drive Vault',
    'backup': 'Backup & Recovery',
    'settings': 'Settings & Security'
  };
  return titles[name] || 'ZEN AI';
}

function bindNavigationEvents() {
  const backBtn = document.getElementById('btn-nav-back');
  if (backBtn) {
    backBtn.addEventListener('click', () => {
      window.location.hash = '';
      navigateTo('dashboard');
    });
  }

  // Dashboard module cards click
  document.querySelectorAll('[data-nav-target]').forEach(card => {
    card.addEventListener('click', () => {
      const target = card.getAttribute('data-nav-target');
      window.location.hash = `section=${target}`;
      navigateTo(target);
    });
  });
}

// --- Live Telemetry Refresh ---
async function refreshTelemetry() {
  const storage = await TelemetryManager.getStorageTelemetry();
  const net = TelemetryManager.getNetworkTelemetry();

  const memCount = (await window.zenDb.getAllMemories()).length;
  const docCount = (await window.zenDb.getAllDocuments()).length;
  const convCount = (await window.zenDb.getAllConversations()).length;
  const backups = await window.zenDb.getAllBackups();
  const latestBackup = backups.length > 0 ? backups[0] : null;

  // Update Dashboard cards
  const elStorageVal = document.getElementById('telemetry-storage-val');
  const elStorageSub = document.getElementById('telemetry-storage-sub');
  if (elStorageVal) elStorageVal.textContent = storage.formattedAvailable;
  if (elStorageSub) elStorageSub.textContent = `${storage.formattedUsage} used (${storage.usedPercentage}% quota)`;

  const elDbVal = document.getElementById('telemetry-db-val');
  const elDbSub = document.getElementById('telemetry-db-sub');
  if (elDbVal) elDbVal.textContent = `${memCount} Records`;
  if (elDbSub) elDbSub.textContent = `${docCount} docs • ${convCount} chats`;

  const elBackupBar = document.getElementById('telemetry-backup-text');
  if (elBackupBar) {
    if (latestBackup) {
      const dateStr = new Date(latestBackup.timestamp).toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
      elBackupBar.textContent = `Encrypted (${dateStr}) • SHA-256 OK`;
    } else {
      elBackupBar.textContent = 'No backup generated yet';
    }
  }
}

// --- AI Assistant & Conversations ---
async function ensureDefaultConversation() {
  const convs = await window.zenDb.getAllConversations();
  if (convs.length === 0) {
    const id = await window.zenDb.createConversation("ZEN Offline Session");
    activeConversationId = id;
    await window.zenDb.addMessage(
      id,
      "ZEN",
      "ZEN AI Local Intelligence is online. All processing runs completely on-device without cloud LLMs or API keys. Your personal memory and indexed files are available offline.",
      "100% On-Device"
    );
  } else {
    activeConversationId = convs[0].id;
  }
  await renderActiveConversation();
  await renderConversationsList();
}

async function renderActiveConversation() {
  if (!activeConversationId) return;
  const windowEl = document.getElementById('chat-window');
  if (!windowEl) return;

  const messages = await window.zenDb.getMessagesForConversation(activeConversationId);
  windowEl.innerHTML = '';

  messages.forEach(msg => {
    const isUser = msg.sender === 'USER';
    const bubble = document.createElement('div');
    bubble.className = `chat-bubble ${isUser ? 'bubble-user' : 'bubble-bot'}`;

    const textEl = document.createElement('div');
    textEl.style.whiteSpace = 'pre-wrap';
    textEl.textContent = msg.content;
    bubble.appendChild(textEl);

    if (msg.metadata) {
      const metaEl = document.createElement('div');
      metaEl.className = 'bubble-meta';
      metaEl.textContent = msg.metadata;
      bubble.appendChild(metaEl);
    }
    windowEl.appendChild(bubble);
  });

  windowEl.scrollTop = windowEl.scrollHeight;
}

async function sendAssistantMessage(text) {
  const query = (text || '').trim();
  if (!query || !activeConversationId) return;

  // Add user message
  await window.zenDb.addMessage(activeConversationId, "USER", query);
  await renderActiveConversation();

  // Process via LocalIntelligenceEngine
  const engine = new LocalIntelligenceEngine(window.zenDb);
  const result = await engine.processQuery(query);

  // Add AI reply
  await window.zenDb.addMessage(
    activeConversationId,
    "ZEN",
    result.replyText,
    `${result.modelArchitecture} • Intent: ${result.intent}`
  );

  await renderActiveConversation();
  await refreshTelemetry();
}

async function renderConversationsList() {
  const listEl = document.getElementById('conversations-list');
  if (!listEl) return;
  const convs = await window.zenDb.getAllConversations();
  listEl.innerHTML = '';

  if (convs.length === 0) {
    listEl.innerHTML = `<div style="text-align:center; padding:32px 0;" class="text-secondary">No local sessions. Start a new chat!</div>`;
    return;
  }

  convs.forEach(conv => {
    const isActive = conv.id === activeConversationId;
    const card = document.createElement('div');
    card.className = `card card-clickable`;
    if (isActive) card.style.borderColor = 'var(--accent-cyan)';

    const dateStr = new Date(conv.updatedAt).toLocaleDateString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    card.innerHTML = `
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <div>
          <div style="font-weight:600; font-size:0.95rem;">${escapeHtml(conv.title)}</div>
          <div style="font-size:0.75rem; color:var(--text-secondary-dark); margin-top:4px;">Updated: ${dateStr}</div>
        </div>
        <button class="icon-btn" data-delete-conv="${conv.id}" title="Delete session" style="color:var(--status-rose);">✕</button>
      </div>
    `;

    card.addEventListener('click', (e) => {
      if (e.target.closest('[data-delete-conv]')) return;
      activeConversationId = conv.id;
      renderActiveConversation();
      navigateTo('assistant');
    });

    const delBtn = card.querySelector('[data-delete-conv]');
    delBtn.addEventListener('click', async (e) => {
      e.stopPropagation();
      if (confirm(`Delete session '${conv.title}'?`)) {
        await window.zenDb.deleteConversation(conv.id);
        await ensureDefaultConversation();
        showToast("Conversation deleted.");
      }
    });

    listEl.appendChild(card);
  });
}

// --- Personal Memory Module ---
async function loadMemories() {
  const listEl = document.getElementById('memories-list');
  if (!listEl) return;

  const searchQuery = (document.getElementById('memory-search-input')?.value || '').toLowerCase().trim();
  const memories = await window.zenDb.getMemoriesByCategory(activeMemoryCategory);

  const filtered = memories.filter(m => {
    if (!searchQuery) return true;
    return m.title.toLowerCase().includes(searchQuery) ||
           m.content.toLowerCase().includes(searchQuery) ||
           (m.tags && m.tags.toLowerCase().includes(searchQuery));
  });

  listEl.innerHTML = '';
  const countEl = document.getElementById('memory-count-badge');
  if (countEl) countEl.textContent = `${filtered.length} Encrypted Records`;

  if (filtered.length === 0) {
    listEl.innerHTML = `
      <div style="text-align:center; padding:40px 16px;">
        <div style="font-size:2rem; margin-bottom:8px;">🔒</div>
        <div style="font-weight:600;">No Personal Memories Found</div>
        <div style="font-size:0.8rem; color:var(--text-secondary-dark); margin-top:6px;">
          ${searchQuery ? `No records match '${searchQuery}'` : 'Tap + Add Memory to store notes, preferences, or secrets locally.'}
        </div>
      </div>
    `;
    return;
  }

  filtered.forEach(m => {
    const card = document.createElement('div');
    card.className = 'card';
    card.style.marginBottom = '10px';

    const dateStr = new Date(m.updatedAt).toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' });
    const tagHtml = m.tags ? m.tags.split(',').map(t => `<span class="badge badge-disconnected" style="margin-right:4px;">#${escapeHtml(t.trim())}</span>`).join('') : '';

    card.innerHTML = `
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
        <span class="badge badge-offline">${escapeHtml(m.category)}</span>
        <div style="font-size:0.75rem; color:var(--text-secondary-dark); display:flex; align-items:center; gap:4px;">
          ${m.isEncrypted ? '🛡️' : ''} ${dateStr}
        </div>
      </div>
      <div style="font-weight:600; font-size:1rem; margin-bottom:4px;">${escapeHtml(m.title)}</div>
      <div style="font-size:0.85rem; color:var(--text-secondary-dark); line-height:1.45; white-space:pre-wrap;">${escapeHtml(m.content)}</div>
      ${tagHtml ? `<div style="margin-top:8px;">${tagHtml}</div>` : ''}
      <div style="display:flex; justify-content:flex-end; gap:8px; margin-top:10px;">
        <button class="btn btn-secondary" style="padding:4px 10px; min-height:32px; font-size:0.75rem;" data-edit-mem="${m.id}">Edit</button>
        <button class="btn btn-danger" style="padding:4px 10px; min-height:32px; font-size:0.75rem;" data-del-mem="${m.id}">Delete</button>
      </div>
    `;

    card.querySelector('[data-edit-mem]').addEventListener('click', () => openEditMemoryModal(m));
    card.querySelector('[data-del-mem]').addEventListener('click', async () => {
      if (confirm(`Permanently delete '${m.title}'?`)) {
        await window.zenDb.deleteMemory(m.id);
        await loadMemories();
        await refreshTelemetry();
        showToast("Memory deleted.");
      }
    });

    listEl.appendChild(card);
  });
}

function openEditMemoryModal(memory = null) {
  const modal = document.getElementById('modal-memory');
  const titleInput = document.getElementById('mem-form-title');
  const catInput = document.getElementById('mem-form-category');
  const contentInput = document.getElementById('mem-form-content');
  const tagsInput = document.getElementById('mem-form-tags');
  const idInput = document.getElementById('mem-form-id');

  if (memory) {
    document.getElementById('modal-memory-title').textContent = 'Edit Personal Memory';
    titleInput.value = memory.title;
    catInput.value = memory.category;
    contentInput.value = memory.content;
    tagsInput.value = memory.tags || '';
    idInput.value = memory.id;
  } else {
    document.getElementById('modal-memory-title').textContent = 'New Personal Memory';
    titleInput.value = '';
    catInput.value = activeMemoryCategory === 'All' ? 'Preferences' : activeMemoryCategory;
    contentInput.value = '';
    tagsInput.value = '';
    idInput.value = '';
  }
  modal.classList.add('active');
}

// --- File Manager (Web File System & File API) ---
async function loadDocuments() {
  const listEl = document.getElementById('documents-list');
  if (!listEl) return;
  const docs = await window.zenDb.getAllDocuments();
  const searchQuery = (document.getElementById('file-search-input')?.value || '').toLowerCase().trim();

  const filtered = docs.filter(d => {
    if (!searchQuery) return true;
    return d.displayName.toLowerCase().includes(searchQuery) ||
           (d.extractedText && d.extractedText.toLowerCase().includes(searchQuery));
  });

  listEl.innerHTML = '';
  if (filtered.length === 0) {
    listEl.innerHTML = `
      <div style="text-align:center; padding:40px 16px;">
        <div style="font-size:2rem; margin-bottom:8px;">📁</div>
        <div style="font-weight:600;">No Documents Indexed</div>
        <div style="font-size:0.8rem; color:var(--text-secondary-dark); margin-top:6px;">
          Import text files or notes using the browser file picker to build your local offline knowledge base.
        </div>
      </div>
    `;
    return;
  }

  filtered.forEach(doc => {
    const card = document.createElement('div');
    card.className = 'card';
    card.style.marginBottom = '10px';

    const dateStr = new Date(doc.indexedAt).toLocaleDateString([], { month: 'short', day: 'numeric', year: 'numeric' });
    card.innerHTML = `
      <div style="display:flex; justify-content:space-between; align-items:flex-start;">
        <div>
          <div style="font-weight:600; font-size:0.95rem;">📄 ${escapeHtml(doc.displayName)}</div>
          <div style="font-size:0.75rem; color:var(--text-secondary-dark); margin-top:2px;">
            ${TelemetryManager.formatBytes(doc.sizeBytes)} • ${escapeHtml(doc.mimeType)} • ${dateStr}
          </div>
        </div>
        <div style="display:flex; gap:6px;">
          <button class="btn btn-secondary" style="padding:4px 8px; min-height:28px; font-size:0.75rem;" data-rename-doc="${doc.id}">Rename</button>
          <button class="btn btn-danger" style="padding:4px 8px; min-height:28px; font-size:0.75rem;" data-del-doc="${doc.id}">Delete</button>
        </div>
      </div>
      ${doc.extractedText ? `
        <div style="background:var(--surface-variant-dark); padding:8px 12px; border-radius:6px; font-size:0.8rem; margin-top:8px; color:var(--text-secondary-dark); line-height:1.4;">
          ${escapeHtml(doc.extractedText.slice(0, 140))}...
        </div>
      ` : ''}
    `;

    card.querySelector('[data-rename-doc]').addEventListener('click', async () => {
      const newName = prompt('New file name:', doc.displayName);
      if (newName && newName.trim()) {
        await window.zenDb.renameDocument(doc.id, newName.trim());
        await loadDocuments();
        showToast('Document renamed.');
      }
    });

    card.querySelector('[data-del-doc]').addEventListener('click', async () => {
      if (confirm(`Remove index for '${doc.displayName}'? (Original file remains untouched)`)) {
        await window.zenDb.deleteDocument(doc.id);
        await loadDocuments();
        await refreshTelemetry();
        showToast('Document index removed.');
      }
    });

    listEl.appendChild(card);
  });

  // Also render Knowledge Library
  renderKnowledgeLibrary(docs);
}

function renderKnowledgeLibrary(docs) {
  const libEl = document.getElementById('knowledge-list');
  if (!libEl) return;
  const readable = docs.filter(d => d.extractedText && d.extractedText.trim().length > 0);
  libEl.innerHTML = '';

  if (readable.length === 0) {
    libEl.innerHTML = `<div style="text-align:center; padding:32px 0;" class="text-secondary">No readable text extracted yet. Import .txt or .md files in File Manager.</div>`;
    return;
  }

  readable.forEach(doc => {
    const card = document.createElement('div');
    card.className = 'card';
    card.style.marginBottom = '10px';
    card.innerHTML = `
      <div style="font-weight:600; color:var(--accent-cyan); margin-bottom:6px;">${escapeHtml(doc.displayName)}</div>
      <div style="font-size:0.85rem; color:var(--text-secondary-dark); line-height:1.45; white-space:pre-wrap;">${escapeHtml(doc.extractedText)}</div>
    `;
    libEl.appendChild(card);
  });
}

// --- Backup & Recovery ---
async function loadBackupHistory() {
  const historyEl = document.getElementById('backup-history-list');
  if (!historyEl) return;
  const backups = await window.zenDb.getAllBackups();
  historyEl.innerHTML = '';

  if (backups.length === 0) {
    historyEl.innerHTML = `<div style="padding:16px 0; color:var(--text-secondary-dark); font-size:0.85rem;">No backup archives recorded yet.</div>`;
    return;
  }

  backups.forEach(b => {
    const item = document.createElement('div');
    item.className = 'sub-card';
    item.style.padding = '10px 14px';
    item.style.marginBottom = '8px';
    item.style.border = '1px solid var(--border-dark)';
    item.style.borderRadius = '8px';

    const dateStr = new Date(b.timestamp).toLocaleString();
    item.innerHTML = `
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <div>
          <div style="font-weight:600; font-size:0.9rem;">${escapeHtml(b.backupName)}</div>
          <div style="font-size:0.75rem; color:var(--text-secondary-dark); margin-top:2px;">
            ${dateStr} • ${b.sizeBytes} bytes • SHA-256 Verified
          </div>
          <div style="font-size:0.65rem; color:var(--accent-cyan); word-break:break-all; margin-top:2px;">
            ${b.checksumSha256}
          </div>
        </div>
        <button class="icon-btn" data-del-backup="${b.id}" style="color:var(--status-rose);">✕</button>
      </div>
    `;

    item.querySelector('[data-del-backup]').addEventListener('click', async () => {
      await window.zenDb.deleteBackup(b.id);
      await loadBackupHistory();
      await refreshTelemetry();
      showToast('Backup record removed.');
    });

    historyEl.appendChild(item);
  });
}

async function executeManualBackup() {
  showToast("Generating encrypted vault archive...");
  try {
    const memories = await window.zenDb.getAllMemories();
    const convs = await window.zenDb.getAllConversations();
    const messages = await window.zenDb.getAll('messages');
    const documents = await window.zenDb.getAllDocuments();

    const rawPackage = {
      format: "ZEN_AI_ENCRYPTED_VAULT",
      version: 1,
      createdAt: Date.now(),
      memories,
      conversations: convs,
      messages,
      documents
    };

    const enc = new TextEncoder();
    const plainBytes = enc.encode(JSON.stringify(rawPackage, null, 2));
    const plainChecksum = await CryptoManager.calculateSha256(plainBytes);
    const encryptedResult = await CryptoManager.encrypt(plainBytes);

    const bundle = {
      header: "ZEN_AI_ENCRYPTED_VAULT",
      version: 1,
      createdAt: Date.now(),
      plainSha256: plainChecksum,
      ivBase64: encryptedResult.ivBase64,
      encryptedPayloadBase64: encryptedResult.cipherTextBase64,
      payloadSizeBytes: plainBytes.byteLength
    };

    const bundleJson = JSON.stringify(bundle, null, 2);
    const fileName = `zen_backup_${Date.now()}.zenvault`;

    // Download file locally to user device
    const blob = new Blob([bundleJson], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);

    // Save record to database
    await window.zenDb.recordBackup({
      backupName: fileName,
      timestamp: Date.now(),
      sizeBytes: plainBytes.byteLength,
      checksumSha256: plainChecksum,
      status: "SUCCESS",
      isEncrypted: true
    });

    await loadBackupHistory();
    await refreshTelemetry();
    showToast(`Backup exported: ${fileName} (SHA-256 OK)`);
  } catch (err) {
    console.error("Backup failed", err);
    showToast(`Backup failed: ${err.message}`);
  }
}

async function restoreBackupBundle(bundleJson) {
  try {
    const bundle = JSON.parse(bundleJson);
    if (bundle.header !== "ZEN_AI_ENCRYPTED_VAULT") {
      throw new Error("Invalid vault bundle header.");
    }

    const decryptedBytes = await CryptoManager.decrypt(
      bundle.ivBase64,
      bundle.encryptedPayloadBase64
    );

    const isValid = await CryptoManager.verifyChecksum(decryptedBytes, bundle.plainSha256);
    if (!isValid) {
      throw new Error("SHA-256 integrity check failed! Archive may be corrupted or tampered.");
    }

    const dec = new TextDecoder();
    const parsed = JSON.parse(dec.decode(decryptedBytes));

    let restoredCount = 0;
    if (Array.isArray(parsed.memories)) {
      for (const m of parsed.memories) {
        delete m.id;
        await window.zenDb.saveMemory(m);
        restoredCount++;
      }
    }

    if (Array.isArray(parsed.documents)) {
      for (const d of parsed.documents) {
        delete d.id;
        await window.zenDb.indexDocument(d);
      }
    }

    await loadMemories();
    await loadDocuments();
    await refreshTelemetry();
    showToast(`Successfully restored ${restoredCount} memories with SHA-256 verification!`);
  } catch (e) {
    console.error("Restore error", e);
    alert(`Restore failed: ${e.message}`);
  }
}

// --- Action & UI Bindings ---
function bindActionEvents() {
  // Theme Toggle
  const themeBtn = document.getElementById('btn-toggle-theme');
  if (themeBtn) {
    themeBtn.addEventListener('click', toggleTheme);
  }

  // Refresh Telemetry
  const refreshBtn = document.getElementById('btn-refresh-telemetry');
  if (refreshBtn) {
    refreshBtn.addEventListener('click', async () => {
      await refreshTelemetry();
      showToast('Telemetry refreshed.');
    });
  }

  // Assistant Send
  const sendBtn = document.getElementById('btn-assistant-send');
  const chatInput = document.getElementById('assistant-chat-input');
  if (sendBtn && chatInput) {
    sendBtn.addEventListener('click', () => {
      if (chatInput.value.trim()) {
        sendAssistantMessage(chatInput.value);
        chatInput.value = '';
      }
    });
    chatInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        if (chatInput.value.trim()) {
          sendAssistantMessage(chatInput.value);
          chatInput.value = '';
        }
      }
    });
  }

  // Assistant Suggestion Chips
  document.querySelectorAll('[data-prompt]').forEach(chip => {
    chip.addEventListener('click', () => {
      const promptText = chip.getAttribute('data-prompt');
      if (chatInput) chatInput.value = promptText;
      sendAssistantMessage(promptText);
    });
  });

  // Assistant New Session
  const newSessionBtn = document.getElementById('btn-new-session');
  if (newSessionBtn) {
    newSessionBtn.addEventListener('click', async () => {
      const name = prompt("Session Name:", `Session ${new Date().toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'})}`);
      if (name) {
        activeConversationId = await window.zenDb.createConversation(name);
        await window.zenDb.addMessage(activeConversationId, "ZEN", "Started new offline session.", "Local");
        await renderActiveConversation();
        await renderConversationsList();
        showToast("New session started.");
      }
    });
  }

  // Memory Categories
  document.querySelectorAll('[data-category]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('[data-category]').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      activeMemoryCategory = btn.getAttribute('data-category');
      loadMemories();
    });
  });

  // Memory Search
  const memSearch = document.getElementById('memory-search-input');
  if (memSearch) memSearch.addEventListener('input', loadMemories);

  // Memory Add FAB & Button
  const addMemFab = document.getElementById('btn-add-memory-fab');
  const addMemBtn = document.getElementById('btn-add-memory');
  if (addMemFab) addMemFab.addEventListener('click', () => openEditMemoryModal());
  if (addMemBtn) addMemBtn.addEventListener('click', () => openEditMemoryModal());

  // Save Memory Form
  const saveMemBtn = document.getElementById('btn-save-memory');
  if (saveMemBtn) {
    saveMemBtn.addEventListener('click', async () => {
      const title = document.getElementById('mem-form-title').value.trim();
      const category = document.getElementById('mem-form-category').value;
      const content = document.getElementById('mem-form-content').value.trim();
      const tags = document.getElementById('mem-form-tags').value.trim();
      const id = document.getElementById('mem-form-id').value;

      if (!title || !content) {
        alert("Please enter both a title and content.");
        return;
      }

      await window.zenDb.saveMemory({
        id: id ? parseInt(id, 10) : undefined,
        title,
        category,
        content,
        tags,
        isEncrypted: true
      });

      document.getElementById('modal-memory').classList.remove('active');
      await loadMemories();
      await refreshTelemetry();
      showToast(id ? "Memory updated." : "Memory saved securely.");
    });
  }

  // Export Memories JSON
  const expMemBtn = document.getElementById('btn-export-memories');
  if (expMemBtn) {
    expMemBtn.addEventListener('click', async () => {
      const memories = await window.zenDb.getAllMemories();
      const json = JSON.stringify({
        type: "ZEN_AI_MEMORY_EXPORT",
        version: 1,
        exportedAt: Date.now(),
        count: memories.length,
        memories
      }, null, 2);

      const blob = new Blob([json], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `zen_memories_${Date.now()}.json`;
      a.click();
      URL.revokeObjectURL(a);
      showToast("Memories exported.");
    });
  }

  // Import Memories JSON
  const impMemInput = document.getElementById('import-memories-file');
  if (impMemInput) {
    impMemInput.addEventListener('change', (e) => {
      const file = e.target.files[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = async (event) => {
        try {
          const parsed = JSON.parse(event.target.result);
          if (Array.isArray(parsed.memories)) {
            for (const m of parsed.memories) {
              delete m.id;
              await window.zenDb.saveMemory(m);
            }
            await loadMemories();
            await refreshTelemetry();
            showToast(`Imported ${parsed.memories.length} memories!`);
          } else {
            alert("Invalid memories export file structure.");
          }
        } catch (err) {
          alert(`Import failed: ${err.message}`);
        }
      };
      reader.readAsText(file);
      impMemInput.value = '';
    });
  }

  // File Manager: Pick File
  const pickFileBtn = document.getElementById('btn-pick-file');
  const filePickerInput = document.getElementById('file-picker-input');
  if (pickFileBtn && filePickerInput) {
    pickFileBtn.addEventListener('click', () => filePickerInput.click());
    filePickerInput.addEventListener('change', async (e) => {
      const files = Array.from(e.target.files);
      if (files.length === 0) return;

      for (const file of files) {
        let extractedText = "";
        if (file.type.startsWith('text/') || file.name.endsWith('.txt') || file.name.endsWith('.md') || file.name.endsWith('.json')) {
          try {
            extractedText = await file.text();
            extractedText = extractedText.slice(0, 2000);
          } catch (_) {}
        }

        await window.zenDb.indexDocument({
          displayName: file.name,
          mimeType: file.type || 'application/octet-stream',
          sizeBytes: file.size,
          lastModified: file.lastModified,
          extractedText: extractedText
        });
      }

      await loadDocuments();
      await refreshTelemetry();
      showToast(`Indexed ${files.length} document(s).`);
      filePickerInput.value = '';
    });
  }

  // Backup Now Button
  const backupBtn = document.getElementById('btn-run-backup-now');
  if (backupBtn) backupBtn.addEventListener('click', executeManualBackup);

  // Restore Backup File Picker
  const restoreFileInput = document.getElementById('restore-file-input');
  if (restoreFileInput) {
    restoreFileInput.addEventListener('change', (e) => {
      const file = e.target.files[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = (event) => {
        restoreBackupBundle(event.target.result);
      };
      reader.readAsText(file);
      restoreFileInput.value = '';
    });
  }

  // Settings: 12-Word Recovery Key
  const genWordsBtn = document.getElementById('btn-generate-recovery-words');
  if (genWordsBtn) {
    genWordsBtn.addEventListener('click', () => {
      const words = CryptoManager.generate12WordRecoveryPassphrase();
      const displayEl = document.getElementById('recovery-words-display');
      displayEl.innerHTML = `
        <div style="background:var(--surface-variant-dark); padding:14px; border-radius:8px; margin-top:10px;">
          <div style="font-size:0.75rem; color:var(--accent-cyan); font-weight:700; margin-bottom:8px;">STORE SAFELY OFFLINE:</div>
          <div style="font-weight:600; line-height:2; font-size:0.95rem;">${words.join('   ')}</div>
          <button class="btn btn-secondary" style="margin-top:10px; font-size:0.75rem; min-height:32px;" id="btn-copy-words">Copy Words</button>
        </div>
      `;
      document.getElementById('btn-copy-words')?.addEventListener('click', () => {
        navigator.clipboard.writeText(words.join(' '));
        showToast("Recovery key copied to clipboard.");
      });
    });
  }

  // Settings: Purge Database
  const purgeBtn = document.getElementById('btn-purge-database');
  if (purgeBtn) {
    purgeBtn.addEventListener('click', async () => {
      if (confirm("DANGER: This will permanently purge all local memories, conversation history, and indexed documents. Continue?")) {
        indexedDB.deleteDatabase('zen_ai_vault_web');
        localStorage.clear();
        alert("Database purged. Reloading app.");
        window.location.reload();
      }
    });
  }

  // Close modals
  document.querySelectorAll('[data-close-modal]').forEach(btn => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('.modal-overlay').forEach(m => m.classList.remove('active'));
    });
  });
}

// --- Theme Management ---
function initTheme() {
  const saved = localStorage.getItem('zen_theme');
  if (saved === 'light') {
    document.body.classList.add('theme-light');
  }
}

function toggleTheme() {
  document.body.classList.toggle('theme-light');
  const isLight = document.body.classList.contains('theme-light');
  localStorage.setItem('zen_theme', isLight ? 'light' : 'dark');
  showToast(`Switched to ${isLight ? 'Light' : 'Dark'} Theme`);
}

// --- Toast Utility ---
function showToast(message) {
  const toast = document.getElementById('toast');
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('show');
  setTimeout(() => {
    toast.classList.remove('show');
  }, 2800);
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
}
