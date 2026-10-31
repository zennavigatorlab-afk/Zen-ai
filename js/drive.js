/**
 * Google Drive Vault Architecture & Conflict Policy
 * Follows least-privilege scope: https://www.googleapis.com/auth/drive.file
 * Shows 'Not connected' until valid Google OAuth client is authorized.
 */

class GoogleDriveVault {
  constructor() {
    this.status = 'NOT_CONNECTED';
    this.account = null;
    this.dedicatedFolder = 'ZEN_AI_VAULT';
    this.requiredScope = 'https://www.googleapis.com/auth/drive.file';
  }

  getStatus() {
    return this.status;
  }

  analyzeConflict(localTimestamp, remoteTimestamp) {
    const diff = localTimestamp - remoteTimestamp;
    if (Math.abs(diff) < 2000) {
      return {
        hasConflict: false,
        recommendedAction: 'USE_LOCAL',
        description: 'Local vault and remote archive are in sync.'
      };
    }
    if (localTimestamp > remoteTimestamp) {
      return {
        hasConflict: true,
        recommendedAction: 'USE_LOCAL',
        description: 'Local device records are newer than remote cloud backup.'
      };
    }
    return {
      hasConflict: true,
      recommendedAction: 'USE_REMOTE',
      description: 'Remote cloud backup is newer than local storage.'
    };
  }

  getSetupInstructions() {
    return `Google Drive Web Vault Configuration:
1. Origin: ${window.location.origin}
2. Authorized redirect URI: ${window.location.origin}/
3. Enable Google Drive API v3 in Google Cloud Console.
4. Create an OAuth 2.0 Web Client ID.
5. Scope required: ${this.requiredScope}
6. Backups will be placed strictly inside dedicated folder '${this.dedicatedFolder}'.`;
  }
}

window.googleDriveVault = new GoogleDriveVault();
