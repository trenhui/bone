#!/usr/bin/env bash
# 校验控制台 OpenAPI 草案（Bone-API-规范 §2.3 console 域）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
echo "[openapi] lint console-v1.yaml ..."
npx --yes @redocly/cli@1 lint doc/architecture/openapi/console-v1.yaml \
  --config doc/architecture/openapi/redocly.yaml
OPENAPI_FILE="doc/architecture/openapi/console-v1.yaml"
if grep -q '@PreAuthorize.*\[Target\]' "$OPENAPI_FILE" 2>/dev/null; then
  echo "[openapi] FAIL: console-v1.yaml still marks @PreAuthorize as [Target]" >&2
  exit 1
fi
if ! grep -q 'bearerAuth' "$OPENAPI_FILE"; then
  echo "[openapi] FAIL: missing bearerAuth security scheme" >&2
  exit 1
fi
echo "[openapi] OK"
