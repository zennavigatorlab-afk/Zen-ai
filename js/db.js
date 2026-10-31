/**
 * ZEN AI IndexedDB Data Architecture
 * Implements Room-equivalent schema with 7 stores and fallback handling.
 */

const DB_NAME = 'zen_ai_vault_web';
const DB_VERSION = 1;

class ZenDatabase {
  constructor() {
    this.db = null;
    this.fallbackMode = false;
    this.memoryFallback = {
      memories: [],
      conversations: [],
      messages: [],
      documents: [],
      sync_queue: [],
      backup_history: [],
      app_settings: {}
    };
  }

  async init() {
    if (!window.indexedDB) {
      console.warn("IndexedDB not supported, falling back to localStorage.");
      this.fallbackMode = true;
      this.loadLocalStorageFallback();
      return;
    }

    return new Promise((resolve) => {
      const request = indexedDB.open(DB_NAME, DB_VERSION);

      request.onupgradeneeded = (event) => {
        const db = event.target.result;

        if (!db.objectStoreNames.contains('memories')) {
          const store = db.createObjectStore('memories', { keyPath: 'id', autoIncrement: true });
          store.createIndex('category', 'category', { unique: false });
          store.createIndex('updatedAt', 'updatedAt', { unique: false });
        }

        if (!db.objectStoreNames.contains('conversations')) {
          const store = db.createObjectStore('conversations', { keyPath: 'id', autoIncrement: true });
          store.createIndex('updatedAt', 'updatedAt', { unique: false });
        }

        if (!db.objectStoreNames.contains('messages')) {
          const store = db.createObjectStore('messages', { keyPath: 'id', autoIncrement: true });
          store.createIndex('conversationId', 'conversationId', { unique: false });
          store.createIndex('timestamp', 'timestamp', { unique: false });
        }

        if (!db.objectStoreNames.contains('documents')) {
          const store = db.createObjectStore('documents', { keyPath: 'id', autoIncrement: true });
          store.createIndex('displayName', 'displayName', { unique: false });
          store.createIndex('indexedAt', 'indexedAt', { unique: false });
        }

        if (!db.objectStoreNames.contains('sync_queue')) {
          const store = db.createObjectStore('sync_queue', { keyPath: 'id', autoIncrement: true });
          store.createIndex('status', 'status', { unique: false });
        }

        if (!db.objectStoreNames.contains('backup_history')) {
          const store = db.createObjectStore('backup_history', { keyPath: 'id', autoIncrement: true });
          store.createIndex('timestamp', 'timestamp', { unique: false });
        }

        if (!db.objectStoreNames.contains('app_settings')) {
          db.createObjectStore('app_settings', { keyPath: 'key' });
        }
      };

      request.onsuccess = (event) => {
        this.db = event.target.result;
        resolve(this);
      };

      request.onerror = (event) => {
        console.error("IndexedDB open error:", event.target.error);
        this.fallbackMode = true;
        this.loadLocalStorageFallback();
        resolve(this);
      };
    });
  }

  loadLocalStorageFallback() {
    try {
      const data = localStorage.getItem('zen_ai_fallback_store');
      if (data) {
        this.memoryFallback = JSON.parse(data);
      }
    } catch (e) {
      console.warn("Storage restricted, using in-memory store", e);
    }
  }

  saveLocalStorageFallback() {
    try {
      localStorage.setItem('zen_ai_fallback_store', JSON.stringify(this.memoryFallback));
    } catch (_) {}
  }

  // --- Memories DAO ---
  async getAllMemories() {
    if (this.fallbackMode) return [...this.memoryFallback.memories].sort((a,b) => b.updatedAt - a.updatedAt);
    return this.getAll('memories', 'updatedAt', 'prev');
  }

  async getMemoriesByCategory(category) {
    const list = await this.getAllMemories();
    if (category === 'All') return list;
    return list.filter(m => m.category === category);
  }

  async saveMemory(item) {
    const now = Date.now();
    const memory = {
      ...item,
      updatedAt: now,
      createdAt: item.createdAt || now,
      isEncrypted: item.isEncrypted !== undefined ? item.isEncrypted : true
    };
    if (this.fallbackMode) {
      if (memory.id) {
        const idx = this.memoryFallback.memories.findIndex(m => m.id === memory.id);
        if (idx >= 0) this.memoryFallback.memories[idx] = memory;
        else this.memoryFallback.memories.push(memory);
      } else {
        memory.id = Date.now();
        this.memoryFallback.memories.push(memory);
      }
      this.saveLocalStorageFallback();
      return memory.id;
    }
    return this.put('memories', memory);
  }

  async deleteMemory(id) {
    if (this.fallbackMode) {
      this.memoryFallback.memories = this.memoryFallback.memories.filter(m => m.id !== id);
      this.saveLocalStorageFallback();
      return;
    }
    return this.delete('memories', id);
  }

  // --- Conversations & Messages DAO ---
  async getAllConversations() {
    if (this.fallbackMode) return [...this.memoryFallback.conversations].sort((a,b) => b.updatedAt - a.updatedAt);
    return this.getAll('conversations', 'updatedAt', 'prev');
  }

  async createConversation(title) {
    const now = Date.now();
    const conv = {
      title: title || `Session ${new Date().toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})}`,
      createdAt: now,
      updatedAt: now
    };
    if (this.fallbackMode) {
      conv.id = Date.now();
      this.memoryFallback.conversations.push(conv);
      this.saveLocalStorageFallback();
      return conv.id;
    }
    return this.add('conversations', conv);
  }

  async getMessagesForConversation(conversationId) {
    if (this.fallbackMode) {
      return this.memoryFallback.messages.filter(m => m.conversationId === conversationId).sort((a,b) => a.timestamp - b.timestamp);
    }
    const all = await this.getAll('messages');
    return all.filter(m => m.conversationId === conversationId).sort((a, b) => a.timestamp - b.timestamp);
  }

  async addMessage(conversationId, sender, content, metadata = "") {
    const now = Date.now();
    const msg = {
      conversationId,
      sender,
      content,
      timestamp: now,
      metadata
    };
    if (this.fallbackMode) {
      msg.id = Date.now() + Math.floor(Math.random() * 100);
      this.memoryFallback.messages.push(msg);
      // update conversation
      const c = this.memoryFallback.conversations.find(conv => conv.id === conversationId);
      if (c) c.updatedAt = now;
      this.saveLocalStorageFallback();
      return msg.id;
    }

    const msgId = await this.add('messages', msg);
    // update conv
    const conv = await this.get('conversations', conversationId);
    if (conv) {
      conv.updatedAt = now;
      await this.put('conversations', conv);
    }
    return msgId;
  }

  async deleteConversation(id) {
    if (this.fallbackMode) {
      this.memoryFallback.conversations = this.memoryFallback.conversations.filter(c => c.id !== id);
      this.memoryFallback.messages = this.memoryFallback.messages.filter(m => m.conversationId !== id);
      this.saveLocalStorageFallback();
      return;
    }
    const msgs = await this.getMessagesForConversation(id);
    for (const m of msgs) {
      await this.delete('messages', m.id);
    }
    await this.delete('conversations', id);
  }

  // --- Documents DAO ---
  async getAllDocuments() {
    if (this.fallbackMode) return [...this.memoryFallback.documents].sort((a,b) => b.indexedAt - a.indexedAt);
    return this.getAll('documents', 'indexedAt', 'prev');
  }

  async indexDocument(doc) {
    const now = Date.now();
    const item = {
      ...doc,
      indexedAt: now,
      tags: doc.tags || 'pwa_import'
    };
    if (this.fallbackMode) {
      item.id = Date.now();
      this.memoryFallback.documents.push(item);
      this.saveLocalStorageFallback();
      return item.id;
    }
    return this.add('documents', item);
  }

  async renameDocument(id, newName) {
    if (this.fallbackMode) {
      const doc = this.memoryFallback.documents.find(d => d.id === id);
      if (doc) {
        doc.displayName = newName;
        this.saveLocalStorageFallback();
      }
      return;
    }
    const doc = await this.get('documents', id);
    if (doc) {
      doc.displayName = newName;
      await this.put('documents', doc);
    }
  }

  async deleteDocument(id) {
    if (this.fallbackMode) {
      this.memoryFallback.documents = this.memoryFallback.documents.filter(d => d.id !== id);
      this.saveLocalStorageFallback();
      return;
    }
    return this.delete('documents', id);
  }

  // --- Backup History DAO ---
  async getAllBackups() {
    if (this.fallbackMode) return [...this.memoryFallback.backup_history].sort((a,b) => b.timestamp - a.timestamp);
    return this.getAll('backup_history', 'timestamp', 'prev');
  }

  async recordBackup(backup) {
    if (this.fallbackMode) {
      backup.id = Date.now();
      this.memoryFallback.backup_history.push(backup);
      this.saveLocalStorageFallback();
      return backup.id;
    }
    return this.add('backup_history', backup);
  }

  async deleteBackup(id) {
    if (this.fallbackMode) {
      this.memoryFallback.backup_history = this.memoryFallback.backup_history.filter(b => b.id !== id);
      this.saveLocalStorageFallback();
      return;
    }
    return this.delete('backup_history', id);
  }

  // --- App Settings DAO ---
  async getSetting(key) {
    if (this.fallbackMode) return this.memoryFallback.app_settings[key] || null;
    const res = await this.get('app_settings', key);
    return res ? res.value : null;
  }

  async setSetting(key, value) {
    if (this.fallbackMode) {
      this.memoryFallback.app_settings[key] = value;
      this.saveLocalStorageFallback();
      return;
    }
    return this.put('app_settings', { key, value });
  }

  // --- Generic Helpers ---
  async get(storeName, key) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction(storeName, 'readonly');
      const store = tx.objectStore(storeName);
      const req = store.get(key);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async getAll(storeName, indexName = null, direction = 'next') {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction(storeName, 'readonly');
      const store = tx.objectStore(storeName);
      const target = indexName ? store.index(indexName) : store;
      const req = target.getAll ? (indexName ? target.openCursor(null, direction) : store.getAll()) : store.getAll();

      if (indexName) {
        const results = [];
        target.openCursor(null, direction).onsuccess = (e) => {
          const cursor = e.target.result;
          if (cursor) {
            results.push(cursor.value);
            cursor.continue();
          } else {
            resolve(results);
          }
        };
      } else {
        req.onsuccess = () => resolve(req.result || []);
        req.onerror = () => reject(req.error);
      }
    });
  }

  async put(storeName, value) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction(storeName, 'readwrite');
      const store = tx.objectStore(storeName);
      const req = store.put(value);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async add(storeName, value) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction(storeName, 'readwrite');
      const store = tx.objectStore(storeName);
      const req = store.add(value);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async delete(storeName, key) {
    return new Promise((resolve, reject) => {
      const tx = this.db.transaction(storeName, 'readwrite');
      const store = tx.objectStore(storeName);
      const req = store.delete(key);
      req.onsuccess = () => resolve();
      req.onerror = () => reject(req.error);
    });
  }
}

window.zenDb = new ZenDatabase();
