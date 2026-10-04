#!/usr/bin/env bash
# 本地一键启动 BONE 5 个消费模块(联合启动验证用)
# 固化两条本机环境坑:
#   1) 陈旧 JDK17 进程常持已删旧 jar 占着 8081/8083/8084,先按端口杀掉再起
#   2) jar 在两次 mvn 调用之间会被清,故 --build 时"构建"与"启动"必须在同一条命令内完成
# 用法:
#   ./scripts/local-up.sh            # 直接拉起已有的 5 个 jar
#   ./scripts/local-up.sh --build    # 先重新构建(与启动同进程,避免 jar 被清)再拉起
set -e

BONE_ROOT=/Users/renhui.trh/wps/bone
PLAT=$BONE_ROOT/bone-platform
JAVA=/Users/renhui.trh/Library/Java/JavaVirtualMachines/temurin-21/Contents/Home/bin/java
PORTS=(8081 8083 8084 8107 8085)
MODULES=(iam system masterdata file integration)
JARS=(bone-iam/target/bone-iam-1.0.0.jar \
      bone-system/target/bone-system-1.0.0.jar \
      bone-masterdata/target/bone-masterdata-1.0.0.jar \
      bone-file/target/bone-file-1.0.0.jar \
      bone-integration/target/bone-platform-integration-1.0.0.jar)
LOG=/tmp/svc

# 1) 清理占用目标端口的陈旧进程
echo "[1/4] 清理占用端口 ${PORTS[*]} 的陈旧进程"
for p in "${PORTS[@]}"; do
  lsof -tiTCP:$p -sTCP:LISTEN 2>/dev/null | xargs -r kill -9 || true
done
sleep 2

# 2) 确保 MinIO 在跑(console 用 9002 避 9001 冲突)
echo "[2/4] 检查 MinIO(:9000)..."
if ! lsof -iTCP:9000 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "  启动 MinIO (console :9002)..."
  nohup minio server /tmp/minio-data --address :9000 --console-address :9002 >/tmp/minio.log 2>&1 &
  sleep 3
fi

# 3) 可选构建(--build): 与启动同进程,杜绝 jar 被清
if [ "${1:-}" = "--build" ]; then
  echo "[3/4] 构建 5 模块(与启动同进程)..."
  ( cd "$PLAT" && mvn -pl bone-iam,bone-system,bone-masterdata,bone-file,bone-integration \
        -am -DskipTests -Dpmd.skip=true -Dcheckstyle.skip=true -Dspotless.check.skip=true package )
fi

# 4) 启动(必须 JDK21; discovery 关掉只验证配置中心,不依赖 Nacos 注册)
echo "[4/4] 启动 5 模块(JDK21)..."
export BONE_DB_PASSWORD=mysql123 \
       BONE_IAM_JWT_SECRET_KEY=dev-only-secret-key-minimum-32-bytes-long \
       BONE_JWT_SECRET=dev-only-secret-key-minimum-32-bytes-long \
       BONE_CONFIG_ENCRYPT_KEY=dev-only-config-encrypt-key-local \
       SPRING_PROFILES_ACTIVE=dev
for i in "${!MODULES[@]}"; do
  nohup "$JAVA" -Xms256m -Xmx640m -jar "$PLAT/${JARS[$i]}" \
        --spring.cloud.nacos.discovery.enabled=false > "$LOG-${MODULES[$i]}.log" 2>&1 &
  echo "  launched ${MODULES[$i]} (pid $!)"
done

echo "等待 90s 启动..."
sleep 90
echo "=== 启动结果(Nacos 配置加载 / 是否完整 Started) ==="
for m in "${MODULES[@]}"; do
  echo "--- $m ---"
  grep -E "Load config\[dataId=bone-common.yaml|Started .*Application in|Tomcat started on port|APPLICATION FAILED|Error creating bean" "$LOG-$m.log" | head -4
done
