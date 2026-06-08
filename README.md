# Vault Gallery

A modern Android app that securely hides photos and videos. On the home screen it
looks like an ordinary **calculator**; the real encrypted gallery only opens when you
type your secret PIN into the calculator and press `=`.

> Built with Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Room, Media3/ExoPlayer,
> CameraX, WorkManager and the Android Keystore. Minimum SDK 26 (Android 8.0).

---

## How the disguise works

- The only launcher icon is **`DisguiseActivity`** — a fully functional calculator.
- Typing a numeric code and pressing `=` checks it against your stored credentials:
  - **Real PIN** → opens the real vault.
  - **Decoy PIN** → opens the fake/decoy vault (plausible deniability).
  - **Anything else** → behaves like a normal calculator and shows the result.
- First run: type `0000` then `=` to reach onboarding and create your PIN.
- The real `MainActivity` is `exported="false"` and is never a launcher, so the vault
  cannot be discovered by browsing installed apps or launcher shortcuts.

## Security architecture

| Concern | Implementation |
|---|---|
| Media encryption | **AES-256-GCM** streaming via `CryptoEngine`, key in hardware-backed **Android Keystore** (`KeystoreManager`) |
| PIN / password | **PBKDF2-HMAC-SHA256** (120k iterations) + per-credential salt in `EncryptedSharedPreferences` (`CredentialManager`) |
| Biometric | Fingerprint / face via AndroidX `BiometricPrompt` (`BiometricAuthenticator`) |
| Auto-lock | `ProcessLifecycleOwner` locks the session on background (`AppLockManager`) |
| Screenshot block | `FLAG_SECURE` toggled live from settings |
| Intruder selfie | Silent front-camera capture via CameraX after N failed attempts (`IntruderCaptureManager`), stored encrypted |
| Fake/decoy vault | Separate PIN → separate `VaultScope`; data is isolated at the query layer |
| Backup leakage | `allowBackup=false` + data-extraction rules exclude all vault data |

Media bytes are stored as encrypted blobs in app-private storage; **only metadata**
lives in Room. Thumbnails are encrypted too and decrypted on-the-fly for the grid via
a custom Coil `Fetcher` (`EncryptedImageFetcher`).

## Features implemented

- Import photos/videos via the system photo picker (spans internal storage, SD card,
  camera, downloads), preview, optional removal of originals, **encrypt-on-import** in a
  background `WorkManager` foreground service.
- Photos / Videos / Albums tabs, grid view, search, sort (date/name/size), favorites.
- Built-in **Media3 / ExoPlayer** video player: hardware-accelerated, gesture controls
  (brightness, volume, seek), 0.5x–3x speed, picture-in-picture, external subtitles
  (SRT/ASS/VTT), resume from last position, duration/metadata.
- **External player handoff** (e.g. XPlayer): decrypts to a short-lived cache file,
  shares it through a private `FileProvider`, and wipes it on exit. Toggle in Settings.
- Full-screen image viewer with pinch-to-zoom/pan.
- Material 3 UI, light/dark/system theme, dynamic color, multi-language (EN/ES/FR,
  easily extensible), onboarding with PIN creation.

## Building

This project targets Android Studio (Koala+). It was authored in an environment without
the Android SDK, so it has **not been compiled here** — open it in Android Studio, which
will sync Gradle and download dependencies.

```
# from Android Studio: Open -> select this folder -> let Gradle sync -> Run 'app'
# or via CLI once the SDK + wrapper are present:
./gradlew :app:assembleDebug
```

If the Gradle wrapper jar is missing, run `gradle wrapper --gradle-version 8.9` once.

## Notes / intended extensions

- **DB-at-rest encryption:** Room currently stores only metadata in app-private storage.
  For defense-in-depth, wire SQLCipher's `SupportFactory` in `DatabaseModule` (commented
  there).
- **Decoy PIN UX:** the Settings toggle currently sets a placeholder decoy PIN; a
  production flow would prompt the user to enter it.
- **Cloud backup encryption:** blobs are already AES-256 encrypted at rest, so they can be
  safely synced as-is; a provider integration is the remaining piece.

## Module map

```
security/crypto   KeystoreManager, CryptoEngine (AES-256-GCM), CredentialManager (PBKDF2)
security          VaultSession, AppLockManager, BiometricAuthenticator, IntruderCaptureManager
data/local        Room entities, DAOs, VaultDatabase
data/prefs        VaultPreferences (DataStore settings)
data/repository   VaultRepository (single source of truth, scope-isolated)
media             MediaStoreSource, MediaImporter, PlaybackPreparer, ExternalPlayerLauncher, SecureFileProvider
work              ImportWorker (+ scheduler) — background encrypt-on-import
ui/disguise       Calculator launcher
ui/auth           Lock screen + AuthViewModel
ui/home           Gallery (tabs/grid/search/sort)
ui/album          Album detail
ui/viewer         Image viewer (zoom/pan)
ui/player         Media3 player (gestures/speed/PiP/subtitles/resume)
ui/imports        Import + preview
ui/settings       Settings
ui/onboarding     Onboarding + PIN creation
ui/image          Coil EncryptedImageFetcher
```
