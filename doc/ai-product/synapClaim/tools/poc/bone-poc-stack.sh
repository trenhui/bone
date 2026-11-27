#!/bin/bash
# 真实 POC 服务栈：gateway(8888) + synap-claim(8087)。全部上游走 127.0.0.1。
set -u
export JAVA_HOME=/Users/renhui.trh/Library/Java/JavaVirtualMachines/temurin-21/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
export BONE_DB_PASSWORD=mysql123
export BONE_IAM_JWT_SECRET_KEY=dev-only-secret-key-minimum-32-bytes-long
export BONE_JWT_SECRET=dev-only-secret-key-minimum-32-bytes-long
export BONE_IAM_URI=http://127.0.0.1:8081
export BONE_MASTERDATA_URI=http://127.0.0.1:8084
export BONE_BLUEPRINT_URI=http://127.0.0.1:8082
export BONE_CLAIM_URI=http://127.0.0.1:8087
export BONE_FILE_URI=http://127.0.0.1:8107
export BONE_METADATA_SERVER_URI=http://127.0.0.1:9001
export BONE_SYSTEM_URI=http://127.0.0.1:8083
export BONE_INTEGRATION_URI=http://127.0.0.1:8085
export BONE_GENERATOR_URI=http://127.0.0.1:8086
export BONE_EXTENSION_STUDIO_URI=http://127.0.0.1:8088
export BONE_PROCUREMENT_URI=http://127.0.0.1:8090

cd /Users/renhui.trh/wps/bone || exit 1

env -u SERVER__PORT java -Xms256m -Xmx640m -jar \
  bone-apps/synap-claim/synap-claim-app/target/synap-claim-1.0.0.jar \
  --server.port=8087 \
  --spring.cloud.nacos.discovery.enabled=false \
  --spring.cloud.nacos.config.enabled=false \
  > /tmp/bone-poc-claim.log 2>&1 &
CLAIM_PID=$!
echo "claim pid=$CLAIM_PID"

env -u SERVER__PORT java -Xms256m -Xmx640m -jar \
  bone-platform/bone-gateway/target/bone-gateway-1.0.0.jar \
  --server.port=8888 \
  --spring.cloud.nacos.discovery.enabled=false \
  --spring.cloud.nacos.config.enabled=false \
  > /tmp/bone-poc-gateway.log 2>&1 &
GW_PID=$!
echo "gateway pid=$GW_PID"

wait
