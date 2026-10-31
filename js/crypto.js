/**
 * ZEN AI Cryptographic Security Engine (Web Crypto API)
 * Mirrors Android Keystore AES-256-GCM and SHA-256 specifications.
 */

const RECOVERY_WORD_LIST = [
  "zenith", "crystal", "quantum", "shield", "beacon", "nebula",
  "cipher", "matrix", "vector", "galaxy", "summit", "aurora",
  "obsidian", "plasma", "glacier", "phoenix", "canyon", "voyager",
  "titan", "horizon", "solace", "timber", "cradle", "harbor",
  "echo", "prism", "falcon", "orbit", "starlight", "granite",
  "falcon", "delta", "shadow", "vortex", "anchor", "stellar"
];

class CryptoManager {
  static async getOrCreateMasterKey() {
    let rawKey = localStorage.getItem('zen_ai_raw_master_key_enc');
    if (!rawKey) {
      const key = await window.crypto.subtle.generateKey(
        { name: "AES-GCM", length: 256 },
        true,
        ["encrypt", "decrypt"]
      );
      const exported = await window.crypto.subtle.exportKey("jwk", key);
      localStorage.setItem('zen_ai_raw_master_key_enc', JSON.stringify(exported));
      return key;
    }
    const jwk = JSON.parse(rawKey);
    return await window.crypto.subtle.importKey(
      "jwk",
      jwk,
      { name: "AES-GCM" },
      true,
      ["encrypt", "decrypt"]
    );
  }

  static async encrypt(plainBytes) {
    const key = await this.getOrCreateMasterKey();
    const iv = window.crypto.getRandomValues(new Uint8Array(12));

    const cipherBuffer = await window.crypto.subtle.encrypt(
      { name: "AES-GCM", iv: iv, tagLength: 128 },
      key,
      plainBytes
    );

    const checksum = await this.calculateSha256(plainBytes);

    return {
      ivBase64: this.bytesToBase64(iv),
      cipherTextBase64: this.bytesToBase64(new Uint8Array(cipherBuffer)),
      sha256Checksum: checksum
    };
  }

  static async decrypt(ivBase64, cipherTextBase64) {
    const key = await this.getOrCreateMasterKey();
    const iv = this.base64ToBytes(ivBase64);
    const cipherBytes = this.base64ToBytes(cipherTextBase64);

    const decryptedBuffer = await window.crypto.subtle.decrypt(
      { name: "AES-GCM", iv: iv, tagLength: 128 },
      key,
      cipherBytes
    );

    return new Uint8Array(decryptedBuffer);
  }

  static async calculateSha256(dataBytes) {
    const hashBuffer = await window.crypto.subtle.digest("SHA-256", dataBytes);
    const hashArray = Array.from(new Uint8Array(hashBuffer));
    return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
  }

  static async verifyChecksum(dataBytes, expectedSha256) {
    const actual = await this.calculateSha256(dataBytes);
    return actual.toLowerCase() === expectedSha256.toLowerCase();
  }

  static generate12WordRecoveryPassphrase() {
    const words = [];
    const arr = new Uint32Array(12);
    window.crypto.getRandomValues(arr);
    for (let i = 0; i < 12; i++) {
      words.push(RECOVERY_WORD_LIST[arr[i] % RECOVERY_WORD_LIST.length]);
    }
    return words;
  }

  static async hashPassphrase(passphrase) {
    const salt = window.crypto.getRandomValues(new Uint8Array(16));
    const enc = new TextEncoder();
    const keyMaterial = await window.crypto.subtle.importKey(
      "raw",
      enc.encode(passphrase),
      { name: "PBKDF2" },
      false,
      ["deriveBits", "deriveKey"]
    );

    const derivedBits = await window.crypto.subtle.deriveBits(
      {
        name: "PBKDF2",
        salt: salt,
        iterations: 10000,
        hash: "SHA-256"
      },
      keyMaterial,
      256
    );

    const saltHex = Array.from(salt).map(b => b.toString(16).padStart(2, '0')).join('');
    const hashHex = Array.from(new Uint8Array(derivedBits)).map(b => b.toString(16).padStart(2, '0')).join('');
    return `${saltHex}:${hashHex}`;
  }

  static async verifyPassphrase(candidate, storedSaltAndHash) {
    const parts = storedSaltAndHash.split(':');
    if (parts.length !== 2) return false;
    const salt = this.hexToBytes(parts[0]);
    const expectedHash = parts[1];

    const enc = new TextEncoder();
    const keyMaterial = await window.crypto.subtle.importKey(
      "raw",
      enc.encode(candidate),
      { name: "PBKDF2" },
      false,
      ["deriveBits", "deriveKey"]
    );

    const derivedBits = await window.crypto.subtle.deriveBits(
      {
        name: "PBKDF2",
        salt: salt,
        iterations: 10000,
        hash: "SHA-256"
      },
      keyMaterial,
      256
    );

    const actualHash = Array.from(new Uint8Array(derivedBits)).map(b => b.toString(16).padStart(2, '0')).join('');
    return actualHash.toLowerCase() === expectedHash.toLowerCase();
  }

  static bytesToBase64(bytes) {
    let binary = '';
    const len = bytes.byteLength;
    for (let i = 0; i < len; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return window.btoa(binary);
  }

  static base64ToBytes(base64) {
    const binaryString = window.atob(base64);
    const len = binaryString.length;
    const bytes = new Uint8Array(len);
    for (let i = 0; i < len; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes;
  }

  static hexToBytes(hex) {
    const bytes = new Uint8Array(hex.length / 2);
    for (let i = 0; i < bytes.length; i++) {
      bytes[i] = parseInt(hex.substr(i * 2, 2), 16);
    }
    return bytes;
  }
}

window.CryptoManager = CryptoManager;
