# Next Gen Music

Next Gen Music is a local-first Android music experience for phones, tablets, and foldables. It indexes shared audio through MediaStore or user-selected SAF folders, stores library metadata in SQLite, and plays local media through a lifecycle-safe background service with notification controls.

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK Platform 36 and Build Tools installed

The application id is `com.nextgenmusic.player`. The minimum SDK is API 19 and the target/compile SDK is 36. The main navigation uses XML views to keep the minimum runtime viable; the included Compose entry point is available for API 21+ feature surfaces.

## Build

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew assembleRelease
```

The release build is minified and resource-shrunk. For a local unsigned release, run `assembleRelease` without signing variables. For a signed build, provide:

```text
RELEASE_KEYSTORE_PATH
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

Do not commit a keystore or passwords.

## GitHub Actions signing

The workflow builds and uploads a debug APK on pushes and pull requests. On non-PR pushes it also produces a signed release APK using these repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

`ANDROID_KEYSTORE_BASE64` is the base64-encoded binary keystore. The workflow writes it only to the runner's temporary directory.

## Privacy and platform behavior

- No account is required.
- Media permissions are requested only when scanning is requested.
- API 29+ uses MediaStore and SAF; persisted SAF grants are restored by Android and are safe to revoke.
- Audio playback is local-first and works offline.
- Browser downloads accept only direct HTTPS URLs and never execute downloaded files.
- The games hub opens already-installed games only; it never installs or runs arbitrary native code.
- Low-memory devices use bounded UI behavior and avoid heavy visual effects.