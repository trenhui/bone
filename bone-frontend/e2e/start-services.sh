#!/bin/bash
# synapClaim 前后端 E2E · 后端服务栈（真实 MySQL + IAM + 网关 + 理赔，非 mock）
#
# 用法（在仓库根目录，需后台常驻）：
#   bash bone-frontend/e2e/start-services.sh &
#   bash bone-frontend/e2e/start-frontend.sh &
#   node bone-frontend/e2e/synap-claim-e2e.cjs
#
# 关键前置（2026-11-05 实证）：**必须重建 gateway jar**。源码里已登记 synap-claim-ops
# 路由，但旧 jar 不含它 ⇒ 经 8888 访问 /api/v1/claim-ops/** 全 404，直连 8087 却 200。
# 这不是代码缺陷，是部署态陈旧；本脚本每次先 package 再启动，杜绝该假故障。
# 网关就绪判据用 POST /api/v1/iam/login 返回 200，不要用 /actuator/health：后者是聚合健康度，
# 未启动的 masterdata/blueprint 上游会让它返回 503，但业务路由仍然可用。
set -u
cd "$(dirname "$0")/../.." || exit 1

MVN="${MVN:-mvn}"
"$MVN" -o -f bone-apps/synap-claim/synap-claim-app/pom.xml package -Dmaven.test.skip=true --batch-mode -q || exit 1
"$MVN" -o -f bone-platform/bone-gateway/pom.xml package -Dmaven.test.skip=true --batch-mode -q || exit 1

export BONE_DB_PASSWORD=mysql123
export BONE_IAM_JWT_SECRET_KEY=dev-only-secret-key-minimum-32-bytes-long
export BONE_JWT_SECRET=dev-only-secret-key-minimum-32-bytes-long
export BONE_IAM_URI=http://127.0.0.1:8081
export BONE_MASTERDATA_URI=http://127.0.0.1:8084
export BONE_BLUEPRINT_URI=http://127.0.0.1:8082
# 网关上游必须 127.0.0.1：macOS localhost 优先解析 ::1，Spring Boot 绑 IPv4 ⇒ 503
export BONE_CLAIM_URI=http://127.0.0.1:8087
export BONE_NACOS_ENABLED=false

if command -v redis-server >/dev/null 2>&1; then
  redis-cli ping >/dev/null 2>&1 || redis-server --daemonize yes >/dev/null 2>&1
fi

COMMON=(--spring.cloud.nacos.discovery.enabled=false --spring.cloud.nacos.config.enabled=false)
LOGDIR="${TMPDIR:-/tmp}"

java -Xms256m -Xmx640m -jar bone-platform/bone-iam/target/bone-iam-1.0.0.jar \
  --server.port=8081 "${COMMON[@]}" > "${LOGDIR}/bone-claim-e2e-iam.log" 2>&1 &
java -Xms256m -Xmx640m -jar bone-platform/bone-gateway/target/bone-gateway-1.0.0.jar \
  --server.port=8888 "${COMMON[@]}" > "${LOGDIR}/bone-claim-e2e-gateway.log" 2>&1 &
java -Xms256m -Xmx768m -jar bone-apps/synap-claim/synap-claim-app/target/synap-claim-1.0.0.jar \
  --server.port=8087 "${COMMON[@]}" > "${LOGDIR}/bone-claim-e2e-claim.log" 2>&1 &

echo "backend stack starting: iam=8081 gateway=8888 claim=8087 (logs in ${LOGDIR}/bone-claim-e2e-*.log)"
wait
