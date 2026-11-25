#!/usr/bin/env bash
# Rebuilds binary assets (launcher icons, signing keystore, prebuilt APK)
# from the base64 copies stored under tools/bin/. Everything is committed
# as plain text, so this script just decodes it back to real bytes.
set -euo pipefail
cd "$(dirname "$0")/.."

targets=$(find tools/bin -type f \( -name '*.b64' -o -name '*.b64.[0-9][0-9]' \) \
          | sed -E 's#^tools/bin/##; s#\.b64(\.[0-9]+)?$##' | sort -u)

for t in $targets; do
  mkdir -p "$(dirname "$t")"
  cat tools/bin/"$t".b64* | base64 -d > "$t"
  echo "restored: $t ($(wc -c < "$t") bytes)"
done

echo "Done. Binary assets are ready."
