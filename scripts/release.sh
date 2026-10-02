#!/usr/bin/env bash
# Build, sign and verify a release APK.
#
# The signing key never lives in this repo. By default the keystore is read
# from ~/Keys/GrapheneGalleryFunctio-release.p12 and its password from the
# macOS Keychain item "GrapheneGalleryFunctio-release-key". Override with
# GALLERY_RELEASE_KEYSTORE / GALLERY_RELEASE_KEY_PASSWORD if you build elsewhere.
#
# The script refuses to produce anything unless the finished APK is signed
# by the expected certificate, so a release can never ship with a debug or
# unknown key.
set -euo pipefail

# SHA-256 of the release signing certificate. Public; users verify against it.
EXPECTED_CERT_SHA256="5079aa22cbcd049788ec24f9aa199853327007b43f9d83470cd8a156f5572990"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

KEYSTORE="${GALLERY_RELEASE_KEYSTORE:-$HOME/Keys/GrapheneGalleryFunctio-release.p12}"
[ -f "$KEYSTORE" ] || { echo "error: keystore not found at $KEYSTORE" >&2; exit 1; }

if [ -z "${GALLERY_RELEASE_KEY_PASSWORD:-}" ]; then
    GALLERY_RELEASE_KEY_PASSWORD="$(security find-generic-password \
        -s GrapheneGalleryFunctio-release-key -w)" \
        || { echo "error: release key password not in Keychain" >&2; exit 1; }
fi
export GALLERY_RELEASE_KEY_PASSWORD

: "${JAVA_HOME:?set JAVA_HOME to a JDK 17}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner | sort -V | tail -1)"

./gradlew :app:clean :app:testDebugUnitTest :app:assembleRelease \
    -PGALLERY_RELEASE_KEYSTORE="$KEYSTORE" --max-workers=4

APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "error: no signed release APK (signing not configured?)" >&2; exit 1; }

CERTS="$("$APKSIGNER" verify --verbose --print-certs "$APK")"
ACTUAL="$(grep -m1 'certificate SHA-256 digest' <<<"$CERTS" | awk '{print $NF}')"
if [ "$ACTUAL" != "$EXPECTED_CERT_SHA256" ]; then
    echo "error: APK signed by $ACTUAL, expected $EXPECTED_CERT_SHA256" >&2
    exit 1
fi
grep -q 'Verified using v2 scheme (APK Signature Scheme v2): true' <<<"$CERTS" \
    || { echo "error: APK is not v2-signed" >&2; exit 1; }

VERSION="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)"
OUT_DIR="${RELEASE_OUT_DIR:-$ROOT/build/release}"
mkdir -p "$OUT_DIR"
OUT="$OUT_DIR/GalleryPlusOperum-v$VERSION.apk"
cp "$APK" "$OUT"
( cd "$OUT_DIR" && shasum -a 256 "$(basename "$OUT")" > "$(basename "$OUT").sha256" )

echo
echo "Release APK:        $OUT"
echo "APK SHA-256:        $(cut -d' ' -f1 "$OUT.sha256")"
echo "Signing cert SHA-256: $ACTUAL"
