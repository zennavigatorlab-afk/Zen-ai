# ZEN AI – Installable Debug APK Artifact

### Package & Build Specifications
- **Application ID**: `com.zenai.personalassistant`
- **Version**: `1.0` (Version Code: `1`)
- **Compile SDK**: `36` (Android 16 API)
- **Min SDK**: `24` (Android 7.0+)
- **Build Type**: `debug`
- **Output Artifact**: `zen_ai_debug.apk` (Size: ~20 MB)
- **SHA-256 Checksum**: `fec649c5cc4241e302df9b454fe7dfbd603ccc84478606b87cd3b470f39c0158`

---

### How to Download & Install

#### Method 1: Artifacts / File Explorer
The APK is saved in:
- `app/build/outputs/apk/debug/app-debug.apk`
- `/.aistudio/artifacts/brain/500c9452-6296-44a0-bf07-7a86c3b6bbaa/zen_ai_debug.apk`

In AI Studio's File Explorer or Artifacts panel, right-click `zen_ai_debug.apk` (or `app-debug.apk`) and choose **Download**.

#### Method 2: AI Studio Project Settings Menu
1. Click the **Settings (gear / kebab icon)** in the top right header of Google AI Studio.
2. Select **Export project as ZIP** or **Generate APK / AAB**.
3. If downloading the ZIP, you can run `./gradlew assembleDebug` locally or find the pre-built APK in `app/build/outputs/apk/debug/app-debug.apk`.
