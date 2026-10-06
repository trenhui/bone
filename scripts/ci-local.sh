#!/usr/bin/env bash
# 本地复现主 CI 路径（与 .github/workflows/ci.yml 对齐）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> secrets-scan"
bash scripts/scan-secrets.sh

echo "==> spotless"
mvn com.diffplug.spotless:spotless-maven-plugin:2.43.0:check --batch-mode \
  -pl bone-framework,bone-platform,\
bone-engine/bone-metadata-sdk,bone-engine/bone-metadata-server,\
bone-engine/bone-metadata-engine/bone-metadata-engine-domain,bone-engine/bone-metadata-engine/bone-metadata-engine-ports,\
bone-engine/bone-metadata-engine/bone-metadata-engine-runtime,bone-engine/bone-metadata-engine/bone-metadata-engine-starter,\
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

# 全量 python 门禁（此前 ci-local.sh 与 ci.yml 的交集为**零**：ci.yml 有 14 个门禁、
# ci-local.sh 一个都没有，且 ci-local.sh 自述"与 ci.yml 对齐" —— 方向性错误，
# 本地漏检比本地误报更隐蔽。此处改为直接复用 ci-check.sh，而不是把门禁清单再抄一遍：
# 抄第二份必然再次漂移（历史上 ci.yml 与 ci-check.sh 就是这么分开的）。
# ci-check.sh 是全量门禁的唯一实现源：pre-commit 用 check.sh（只跑变更模块，快），
# CI 用 ci.yml（显式逐条 listing，便于单独标注 blocking）。三者职责不重叠。
echo "==> gates (全量 python 门禁)"
bash scripts/ci-check.sh

echo "==> gate liveness"
python3 scripts/check-gate-liveness.py --strict

echo "All local CI steps passed."
