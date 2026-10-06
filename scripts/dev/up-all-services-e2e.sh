#!/usr/bin/env bash
# Bone 全模块联调环境：严格串行拉起（并发启动会被内存压力全部吃掉）
# 用法：bash /tmp/bone-up-serial.sh
#
# 本机三条硬约束（每条都是踩坑换来的，勿删）：
#  1) SERVER__PORT=59538 是 IDE 注入的，会劫持所有服务端口 ⇒ 启动必须 env -u SERVER__PORT
#     （症状：日志说 Started，端口却不通，因为 Tomcat 实际起在 59538）
#  2) shell 有 HTTP_PROXY ⇒ 探测 127.0.0.1 必须 curl --noproxy '*'，否则 000/502 是假象
#  3) iam/system/masterdata/integration/file 有 spring.config.import: nacos:bone-common.yaml
#     ⇒ 必须先起 Nacos；且这 5 个模块不能用 fat jar（java -jar）起——nested jar 会让
#        SpringFactoriesLoader 扫不到 Nacos 的 ConfigDataLocationResolver，报
#        "Incorrect ConfigDataLocationResolver chosen"。必须 mvn spring-boot:run（exploded classpath）。
#     另 5 个模块（blueprint/metadata/generator/extension/gateway）无 config.import，java -jar 即可。
set -uo pipefail
ROOT=/Users/renhui.trh/wps/bone
JAVA=/Users/renhui.trh/Library/Java/JavaVirtualMachines/temurin-21/Contents/Home/bin/java
MVN_DIR=/Users/renhui.trh/java/apache-maven-3.8.6/bin
LOG=/tmp/bone-e2e
mkdir -p "$LOG"

while IFS='=' read -r k v; do case "$k" in ''|'#'*) continue;; esac; export "$k=$v"; done < "${ROOT}/.env"
export BONE_IAM_JWT_SECRET_KEY="${BONE_IAM_JWT_SECRET_KEY:-dev-only-secret-key-minimum-32-bytes-long}"
export BONE_JWT_SECRET="${BONE_JWT_SECRET:-dev-only-secret-key-minimum-32-bytes-long}"
export SPRING_PROFILES_ACTIVE=dev
unset SERVER__PORT SERVER__HOST SERVER_PORT

health() { curl -s -m 2 --noproxy '*' -o /dev/null -w "%{http_code}" "http://127.0.0.1:$1/actuator/health" 2>/dev/null; }

# 上一个起来再起下一个：并发必被 OOM 吃掉
wait_up() { # port label
  for _ in $(seq 1 60); do
    c=$(health "$1")
    if [ "${c}" != "000" ]; then echo "    ✓ $2 (:$1) http=$c"; return 0; fi
    sleep 3
  done
  echo "    ✗ $2 (:$1) TIMEOUT"
  grep -oE 'Incorrect ConfigDataLocationResolver[^,]*|APPLICATION FAILED TO START|Started [A-Za-z]+ in [0-9.]+|Tomcat started on port [0-9]+' "$LOG/$2.log" 2>/dev/null | tail -3
  return 1
}

mvn_svc() { # module name port
  if [ -n "$(lsof -nP -iTCP:"$3" -sTCP:LISTEN -t 2>/dev/null | head -1)" ]; then echo "[skip] $2 (:$3)"; return; fi
  echo "[start] $2 → :$3 (mvn spring-boot:run)"
  ( cd "$ROOT/$1" && nohup env -u SERVER__PORT -u SERVER__HOST \
      PATH="$MVN_DIR:$JAVA_HOME/bin:$PATH" JAVA_HOME=/Users/renhui.trh/Library/Java/JavaVirtualMachines/temurin-21/Contents/Home \
      mvn -o -q spring-boot:run \
      -Dspring-boot.run.jvmArguments="-Xms96m -Xmx448m -XX:TieredStopAtLevel=1 -Dserver.port=$3" \
      > "$LOG/$2.log" 2>&1 < /dev/null & disown ) 2>/dev/null
  wait_up "$3" "$2"
}

jar_svc() { # jar name port heap
  if [ -n "$(lsof -nP -iTCP:"$3" -sTCP:LISTEN -t 2>/dev/null | head -1)" ]; then echo "[skip] $2 (:$3)"; return; fi
  echo "[start] $2 → :$3 (java -jar)"
  ( cd "$ROOT" && nohup "$JAVA" -Xms96m -Xmx"${4:-448}m" -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=192m \
      -jar "$ROOT/$1" --server.port="$3" \
      --spring.cloud.nacos.discovery.enabled=false --spring.cloud.nacos.config.enabled=false \
      > "$LOG/$2.log" 2>&1 < /dev/null & disown ) 2>/dev/null
  wait_up "$3" "$2"
}

echo "=== 内存 ==="; vm_stat | grep "Pages free"
echo "=== 阶段1：Nacos（config center）==="
if [ "$(health 8848)" = "000" ]; then
  if lsof -nP -iTCP:8848 -sTCP:LISTEN -t >/dev/null 2>&1; then echo "[skip] nacos 端口在监听"; else
    echo "[start] nacos → :8848"
    ( cd /Users/renhui.trh/apps/nacos && nohup env JAVA_HOME=/Users/renhui.trh/Library/Java/JavaVirtualMachines/temurin-21/Contents/Home \
        bash bin/startup.sh -m standalone > /tmp/nacos-start.log 2>&1 < /dev/null & disown ) 2>/dev/null
    for _ in $(seq 1 40); do sleep 3; curl -s -m 2 --noproxy '*' -o /dev/null "http://127.0.0.1:8848/nacos/" 2>/dev/null && break; done
  fi
fi
echo "    nacos 8848 = $(health 8848)"

echo "=== 阶段2：网关（其余服务都经它）==="
jar_svc bone-platform/bone-gateway/target/bone-gateway-1.0.0.jar gateway 8888 384

echo "=== 阶段3：依赖 Nacos 的 5 个服务（必须 mvn 方式）==="
mvn_svc bone-platform/bone-iam         iam         8081
mvn_svc bone-platform/bone-system      system      8083
mvn_svc bone-platform/bone-masterdata  masterdata  8084
mvn_svc bone-platform/bone-integration integration 8085
mvn_svc bone-platform/bone-file        file        8107

echo "=== 阶段4：不依赖 Nacos 的 5 个服务（jar 即可）==="
jar_svc bone-blueprint/target/bone-blueprint-1.0.0.jar blueprint 8082 448
jar_svc bone-engine/bone-metadata-server/target/bone-metadata-server-1.0.0-SNAPSHOT.jar metadata 9001 512
jar_svc bone-engine/studio-generator/target/bone-studio-generator-1.0.0.jar generator 8086 448
jar_svc bone-engine/bone-extension-engine/bone-extension-studio/target/bone-extension-studio-1.0.0.jar extension 8088 512

echo "=== 终态 ==="
for p in 8888 8081 8082 8083 8084 8085 8086 8088 8107 9001; do
  printf "  :%-5s %s\n" "$p" "$(health "$p")"
done
vm_stat | grep "Pages free"
echo "=== 网关路由冒烟 ==="
curl -s -m 8 --noproxy '*' -o /dev/null -w "  POST /api/v1/iam/login -> %{http_code}\n" \
  -X POST http://127.0.0.1:8888/api/v1/iam/login -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 0" -d '{"username":"admin","password":"123456"}' 2>/dev/null
