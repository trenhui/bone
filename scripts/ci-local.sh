#!/usr/bin/env bash
# 本地复现主 CI 路径（与 .github/workflows/ci.yml 对齐）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> secrets-scan"
bash scripts/scan-secrets.sh

echo "==> spotless"
mvn com.diffplug.spotless:spotless-maven-plugin:2.43.0:check --batch-mode \
  -pl bone-framework,bone-platform,bone-sdk,bone-tool,\
bone-engine/bone-metadata-sdk,bone-engine/bone-metadata-server,\
bone-engine/bone-metadata-engine/bone-metadata-engine-core,bone-engine/bone-metadata-engine/bone-metadata-engine-starter,\
bone-engine/bone-integration,\
bone-engine/bone-extension-engine/bone-extension-sdk,bone-engine/bone-extension-engine/bone-extension-studio \
  -am

echo "==> blueprint"
mvn -f bone-blueprint/pom.xml clean test --batch-mode

echo "==> backend verify (skip spotless re-check)"
mvn clean verify --batch-mode -DskipITs=true -Dspotless.check.skip=true

echo "==> frontend"
(
  cd bone-frontend
  npm ci
  npm run lint
  npm run build --workspace=bone-shell
)

echo "All local CI steps passed."
