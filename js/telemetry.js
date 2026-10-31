/**
 * ZEN AI Web Platform Telemetry & System Utilities
 * Real browser APIs: navigator.storage, navigator.connection, navigator.getBattery, navigator.share
 */

class TelemetryManager {
  static async getStorageTelemetry() {
    try {
      if (navigator.storage && navigator.storage.estimate) {
        const estimate = await navigator.storage.estimate();
        const usageBytes = estimate.usage || 0;
        const quotaBytes = estimate.quota || 0;
        const availableBytes = Math.max(0, quotaBytes - usageBytes);
        const usedPct = quotaBytes > 0 ? Math.round((usageBytes / quotaBytes) * 100) : 0;

        return {
          usageBytes,
          quotaBytes,
          availableBytes,
          formattedAvailable: this.formatBytes(availableBytes),
          formattedQuota: this.formatBytes(quotaBytes),
          formattedUsage: this.formatBytes(usageBytes),
          usedPercentage: usedPct,
          isEstimateAccurate: true
        };
      }
    } catch (e) {
      console.warn("Storage estimate error", e);
    }
    return {
      usageBytes: 0,
      quotaBytes: 0,
      availableBytes: 0,
      formattedAvailable: "Persistent",
      formattedQuota: "Browser Managed",
      formattedUsage: "0 KB",
      usedPercentage: 0,
      isEstimateAccurate: false
    };
  }

  static getNetworkTelemetry() {
    const isOnline = navigator.onLine;
    let type = isOnline ? "Connected" : "Offline";

    if (navigator.connection) {
      const conn = navigator.connection;
      type = conn.effectiveType ? conn.effectiveType.toUpperCase() : (conn.type || type);
    }

    return {
      isOnline,
      networkType: isOnline ? (type === "Connected" ? "Online" : `Online (${type})`) : "Offline"
    };
  }

  static async getBatteryTelemetry() {
    try {
      if (navigator.getBattery) {
        const battery = await navigator.getBattery();
        return {
          supported: true,
          percentage: Math.round(battery.level * 100),
          isCharging: battery.charging
        };
      }
    } catch (_) {}
    return {
      supported: false,
      percentage: 100,
      isCharging: false
    };
  }

  static getDeviceTelemetry() {
    return {
      platform: navigator.platform || "Web / Mobile",
      userAgent: navigator.userAgent,
      cpuCores: navigator.hardwareConcurrency || "Unknown",
      deviceMemory: navigator.deviceMemory ? `${navigator.deviceMemory} GB` : "Standard",
      isPWA: window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true
    };
  }

  static async shareReport(title, text) {
    if (navigator.share) {
      try {
        await navigator.share({ title, text });
        return true;
      } catch (e) {
        if (e.name !== 'AbortError') console.error("Share failed", e);
      }
    }
    // Fallback: copy to clipboard
    try {
      await navigator.clipboard.writeText(text);
      return false; // indicates clipboard fallback
    } catch (_) {
      return null;
    }
  }

  static formatBytes(bytes) {
    const kb = bytes / 1024;
    const mb = kb / 1024;
    const gb = mb / 1024;
    if (gb >= 1) return `${gb.toFixed(2)} GB`;
    if (mb >= 1) return `${mb.toFixed(1)} MB`;
    if (kb >= 1) return `${kb.toFixed(1)} KB`;
    return `${bytes} B`;
  }
}

window.TelemetryManager = TelemetryManager;
