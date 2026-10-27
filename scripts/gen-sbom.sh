#!/usr/bin/env bash
# synap-claim SBOM 生成（合规包 01）。
# 触发 synap-claim 的 `sbom` profile，产出 CycloneDX JSON 物料清单。
# 默认联网下载 cyclonedx-maven-plugin（~/.m2 不含），日常 CI 缓存后即可离线复用。
set -euo pipefail

cd "$(dirname "$0")/.."

echo "[SBOM] 生成 synap-claim 软件物料清单（CycloneDX JSON）..."
mvn -f bone-apps/synap-claim/pom.xml -P sbom -DskipTests verify

echo "[SBOM] 产物：bone-apps/synap-claim/target/bom.json"
echo "[SBOM] 可接入 dependency-check / 漏洞扫描 / 等保测评附件。"
