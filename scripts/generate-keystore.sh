#!/usr/bin/env bash
# Creates the upload keystore for Google Play on your own computer.
# Requires a JDK (Android Studio bundles one: <AS install>/jbr/bin/keytool).
#
# Usage: ./scripts/generate-keystore.sh [alias] [output-file]
set -euo pipefail

ALIAS="${1:-quickqr-upload}"
OUT="${2:-upload-keystore.jks}"

if [ -e "$OUT" ]; then
  echo "Refusing to overwrite existing $OUT" >&2
  exit 1
fi

echo "keytool will now ask for a password (use the SAME one for store and key)"
echo "and your certificate details. Remember the password!"
echo
keytool -genkeypair -v -keystore "$OUT" -storetype PKCS12 \
  -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000

echo
echo "Created $OUT (alias: $ALIAS)."
echo "1) BACK IT UP somewhere safe (password manager / cloud drive) - never commit it."
echo "2) For GitHub Actions, copy this single line into the KEYSTORE_BASE64 secret:"
echo
if base64 --help 2>&1 | grep -q -- '-w'; then base64 -w0 "$OUT"; else base64 -i "$OUT" | tr -d '\n'; fi
echo
