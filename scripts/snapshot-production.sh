#!/usr/bin/env bash
# Copy current project tree to snapshots/production-<version>/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-}"

if [[ -z "$VERSION" ]]; then
  VERSION="$(grep "versionName" "$ROOT/app/build.gradle" | head -1 | sed "s/.*'\([^']*\)'.*/\1/")"
fi

DEST="$ROOT/snapshots/production-$VERSION"
mkdir -p "$DEST"

rsync -a --delete \
  --exclude '.git' \
  --exclude 'snapshots' \
  --exclude 'build' \
  --exclude '**/build' \
  --exclude '.gradle' \
  --exclude 'keystore.properties' \
  --exclude '*.jks' \
  --exclude 'release-keystore.jks' \
  --exclude 'local.properties' \
  --exclude '.idea' \
  --exclude '*.apk' \
  --exclude '*.aab' \
  --exclude '.DS_Store' \
  --exclude 'captures' \
  "$ROOT/" "$DEST/"

DATE="$(date +%Y-%m-%d)"
if [[ ! -f "$DEST/SNAPSHOT.md" ]]; then
  cat > "$DEST/SNAPSHOT.md" <<EOF
# Production snapshot — Marine Weather $VERSION

**Captured:** $DATE  
**Package:** \`fi.veneappi.app\`

See [snapshots/README.md](../../README.md) for restore instructions.
EOF
fi

echo "Snapshot written to $DEST"
du -sh "$DEST"
