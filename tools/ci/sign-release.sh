#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/signing-certificates.sh"

for name in ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD \
  ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD ANDROID_SIGNING_CERT_SHA256 RELEASE_TAG; do
  test -n "${!name:-}" || { echo "Missing configuration: $name" >&2; exit 1; }
done
[[ "$RELEASE_TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]

umask 077
signing_dir=$(mktemp -d "$RUNNER_TEMP/crticket-signing.XXXXXX")
trap 'rm -rf -- "$signing_dir"' EXIT
printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$signing_dir/release.jks"
printf '%s' "$ANDROID_KEYSTORE_PASSWORD" > "$signing_dir/store-password"
printf '%s' "$ANDROID_KEY_PASSWORD" > "$signing_dir/key-password"
unset ANDROID_KEYSTORE_BASE64

bundletool_version=1.18.3
curl --fail --location --retry 3 \
  "https://github.com/google/bundletool/releases/download/$bundletool_version/bundletool-all-$bundletool_version.jar" \
  --output "$signing_dir/bundletool.jar"

mapfile -t apks < <(find unsigned -name '*-unsigned.apk' -type f)
mapfile -t bundles < <(find unsigned -name '*.aab' -type f)
test "${#apks[@]}" -eq 1
test "${#bundles[@]}" -eq 1
mkdir -p dist
apk="dist/CRTicket-$RELEASE_TAG.apk"
apks_archive="dist/CRTicket-$RELEASE_TAG.apks"
build_tools="$ANDROID_HOME/build-tools/37.0.0"

"$build_tools/apksigner" sign \
  --ks "$signing_dir/release.jks" \
  --ks-key-alias "$ANDROID_KEY_ALIAS" \
  --ks-pass env:ANDROID_KEYSTORE_PASSWORD \
  --key-pass env:ANDROID_KEY_PASSWORD \
  --out "$apk" "${apks[0]}"

java -jar "$signing_dir/bundletool.jar" build-apks \
  --bundle="${bundles[0]}" --output="$apks_archive" \
  --ks="$signing_dir/release.jks" --ks-key-alias="$ANDROID_KEY_ALIAS" \
  --ks-pass="file:$signing_dir/store-password" \
  --key-pass="file:$signing_dir/key-password"

expected=$(printf '%s' "$ANDROID_SIGNING_CERT_SHA256" | tr -d ':[:space:]' | tr '[:upper:]' '[:lower:]')
[[ "$expected" =~ ^[0-9a-f]{64}$ ]]
verify_apk() {
  local report
  report=$("$build_tools/apksigner" verify --verbose --print-certs "$1")
  verify_signing_certificates "$report" "$expected"
}
verify_apk "$apk"
unzip -q "$apks_archive" -d "$signing_dir/apks"
count=0
while IFS= read -r -d '' split; do
  verify_apk "$split"
  count=$((count + 1))
done < <(find "$signing_dir/apks" -name '*.apk' -type f -print0)
test "$count" -gt 0

# apksigner can create an installation sidecar; publish only the requested packages.
rm -f -- "$apk.idsig"
(cd dist && sha256sum ./*.apk ./*.apks > SHA256SUMS.txt)
