#!/usr/bin/env bash
# 校验 Studio Generator OpenAPI 草案（Bone-API-规范 §15）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
echo "[openapi] lint generator-v1.yaml ..."
npx --yes @redocly/cli@1 lint doc/architecture/openapi/generator-v1.yaml \
  --config doc/architecture/openapi/redocly.yaml
echo "[openapi] OK"
