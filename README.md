# GrapheneGalleryFunctio

*Functio* — Latin for function. A full-featured photo and video gallery for privacy-focused Android, built on the GrapheneOS fork of the AOSP Gallery2 app.

The stock AOSP gallery is private because it does very little: no network, no accounts, no cloud — but also no real way to organise your photos. This fork keeps every bit of that privacy, tightens it further, and adds the functionality a gallery should have. There is no reason a private gallery has to be a limited one.

> Independent project. Not affiliated with or endorsed by GrapheneOS or the Android Open Source Project.

## Privacy

- **No internet access.** The app does not request `INTERNET`, so Android will not let it open a network connection — no telemetry, no analytics, no cloud.
- **Minimal permissions.** Location, microphone, accounts/contacts, NFC, sync, settings-write, vibrate, network-state and exact-alarm permissions have all been removed. What remains has real code behind it: photos & videos, media location metadata, notifications for background file operations, and restart-on-boot for auto-file and trash purge.
- **No sensor use.** The legacy orientation listener that silently read the accelerometer is gone.
- **Sharing is per item.** Share uses Android's system share sheet with a one-time read grant for exactly the items you choose. The app does not enumerate your installed apps.
- **No file-provider exposure** of external storage.
- **Opt-in only.** "Manage media without asking" (`MANAGE_MEDIA`) is available in Settings for people who want fewer permission prompts; the app never asks for it on its own.

## Features

On top of the classic AOSP gallery (albums, viewer, slideshow, editor, crop, video player):

- **Move and copy** photos and videos between real folders on disk, one or many at a time, with background progress and cancel.
- **One permission dialog per batch.** When Android needs your consent to modify media the app didn't create, you are asked once for the whole selection, and the operation completes after you allow it.
- **Folders:** create, rename, move/nest and delete (the Camera folder is protected).
- **Favourites** album.
- **Trash** with restore, delete permanently, empty trash and automatic purge — photos and videos alike.
- **Undo** for the last operation.
- **Auto-file:** optionally files new camera photos into `Pictures/YYYY/MM` after a delay you choose. It only ever touches photos taken after you switch it on.

What's coming next — fast filing, export, an encrypted vault and more — is in the [roadmap](ROADMAP.md).

## Install

Download the APK from the [Releases](https://github.com/cpw7776/GrapheneGalleryFunctio/releases) page, or let [Obtainium](https://github.com/ImranR98/Obtainium) track this repository and install updates for you (add the repo URL as an app source). The app itself never checks for updates — it has no network access.

**Verify what you install.** Every release is signed with the same key. Its certificate SHA-256 fingerprint is:

```
50:79:AA:22:CB:CD:04:97:88:EC:24:F9:AA:19:98:53:32:70:07:B4:3F:9D:83:47:0C:D8:A1:56:F5:57:29:90
```

Check it with [AppVerifier](https://github.com/soupslurpr/AppVerifier) on the phone, or with `apksigner verify --print-certs <apk>` on a computer. Each release also lists the APK's own SHA-256 checksum.

The release package is `org.gallery2.fork`. It installs alongside the stock gallery and does not replace it.

## Building

The upstream sources build inside the AOSP tree (`Android.bp`). This fork also carries a standalone Gradle/CMake harness:

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:testDebugUnitTest --max-workers=4
./gradlew :app:assembleDebug --max-workers=4
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17, Android SDK with NDK/CMake. Min SDK 29, target SDK 33. Point Gradle at your SDK with `ANDROID_HOME` or a `local.properties` file containing `sdk.dir=/path/to/sdk`.

Debug builds install as a separate app (`org.gallery2.fork.debug`). Release builds are signed by `scripts/release.sh`, which needs the maintainer's key and refuses to output an APK signed by anything else; a release build without the key is left unsigned.

## Status

Under active development, tested by hand on a GrapheneOS Pixel before each release. One release per epic — see the [roadmap](ROADMAP.md).

## License

[Apache License 2.0](LICENSE) — you are free to use, modify, fork and redistribute this project, including commercially, as long as you keep the license and copyright notices and state your changes. See [NOTICE](NOTICE) for attribution. Original code © The Android Open Source Project; GrapheneOS changes © their respective authors.
