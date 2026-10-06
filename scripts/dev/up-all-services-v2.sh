#!/usr/bin/env bash
# Bone 后端全量常驻拉起（v2：修掉 v1 的 mvn_svc 子 shell 失效问题）
#
# v1（up-all-services-e2e.sh）里 mvn_svc 用 `( cd X && nohup env ... mvn ... & disown )`
# 把「cd + nohup + mvn」整条链丢进后台，实测在本次环境里子 shell 起不来 mvn：
#   - 无 mvn 进程、iam.log 从未被重写 ⇒ mvn 根本没执行，脚本却卡在 wait_up 里空转
#   - gateway（java -jar 同类写法）却正常 ⇒ 差异只出现在 mvn 通道
# v2 改为：每个服务单独 nohup 后台起，本脚本全程常驻（作为父进程）轮询健康检查，
# 脚本不退出 ⇒ 子进程不会被回收。
set -uo pipefail
ROOT=/Users/renhui.trh/wps/bone
JAVA=/Users/renhui.trh/Library/Java/JavaVirtualMachines/temurin-21/Contents/Home/bin/java
M=/Users/renhui.trh/java/apache-maven-3.8.6/bin
LOG=/tmp/bone-e2e
mkdir -p "$LOG"
while IFS='=' read -r k v; do case "$k" in ''|'#'*) continue;; esac; export "$k=$v"; done < "${ROOT}/.env"
export BONE_IAM_JWT_SECRET_KEY="${BONE_IAM_JWT_SECRET_KEY:-dev-only-secret-key-minimum-32-bytes-long}"
export BONE_JWT_SECRET="${BONE_JWT_SECRET:-dev-only-secret-key-minimum-32-bytes-long}"
export SPRING_PROFILES_ACTIVE=dev
unset SERVER__PORT SERVER__HOST SERVER_PORT

health() { curl -s -m 2 --noproxy '*' -o /dev/null -w "%{http_code}" "http://127.0.0.1:$1/actuator/health" 2>/dev/null; }

# 串行等待某个端口健康；返回 0=起来了 1=超时
wait_up() {
  for i in $(seq 1 80); do
    c=$(health "$1")
    if [ "$c" != "000" ]; then echo "  ✓ $2 (:$1) http=$c  [$((${i}*3))s]"; return 0; fi
    sleep 3
  done
  echo "  ✗ $2 (:$1) TIMEOUT"
  grep -oE 'APPLICATION FAILED TO START|Started [A-Za-z]+ in [0-9.]+|Tomcat started on port [0-9]+|Incorrect ConfigDataLocationResolver[^,]*|Caused by: .*' "$LOG/$2.log" 2>/dev/null | tail -4
  return 1
}

# 已在跑就跳过，否则 nohup 后台起
up_mvn() { # module name port
  if lsof -nP -iTCP:"$3" -sTCP:LISTEN -t >/dev/null 2>&1; then echo "  · $2 (:$3) 已在运行"; return 0; fi
  echo "  [start] $2 → :$3 (mvn spring-boot:run)"
  ( cd "$ROOT/$1" && nohup env -u SERVER__PORT -u SERVER__HOST \
      PATH="$M:$JAVA_HOME/bin:$PATH" JAVA_HOME=/Users/renhui.trh/Library/Java/JavaVirtualMachines/temurin-21/Contents/Home \
      mvn -o spring-boot:run \
      -Dspring-boot.run.jvmArguments="-Xms96m -Xmx448m -XX:TieredStopAtLevel=1 -Dserver.port=$3" \
      > "$LOG/$2.log" 2>&1 < /dev/null & ) 2>/dev/null
  wait_up "$3" "$2"
}

up_jar() { # jar name port heap
  if lsof -nP -iTCP:"$3" -sTCP:LISTEN -t >/dev/null 2>&1; then echo "  · $2 (:$3) 已在运行"; return 0; fi
  echo "  [start] $2 → :$3 (java -jar)"
  ( cd "$ROOT" && nohup "$JAVA" -Xms96m -Xmx"${4:-448}m" -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=192m \
      -jar "$ROOT/$1" --server.port="$3" \
      --spring.cloud.nacos.discovery.enabled=false --spring.cloud.nacos.config.enabled=false \
      > "$LOG/$2.log" 2>&1 < /dev/null & ) 2>/dev/null
  wait_up "$3" "$2"
}

echo "=== 阶段2：网关 ==="
up_jar bone-platform/bone-gateway/target/bone-gateway-1.0.0.jar gateway 8888 384

echo "=== 阶段3：依赖 Nacos（必须 mvn 通道）==="
up_mvn bone-platform/bone-system      system      8083
up_mvn bone-platform/bone-masterdata  masterdata  8084
up_mvn bone-platform/bone-integration integration 8085
up_mvn bone-platform/bone-file        file        8107

echo "=== 阶段4：无 Nacos（jar 即可）==="
up_jar bone-blueprint/target/bone-blueprint-1.0.0.jar blueprint 8082 448
up_jar bone-engine/bone-metadata-server/target/bone-metadata-server-1.0.0-SNAPSHOT.jar metadata 9001 512
up_jar bone-engine/studio-generator/target/bone-studio-generator-1.0.0.jar generator 8086 448
up_jar bone-engine/bone-extension-engine/bone-extension-studio/target/bone-extension-studio-1.0.0.jar extension 8088 512

echo "=== 终态 ==="
rc=0
for p in 8888 8081 8082 8083 8084 8085 8086 8087 8088 8107 9001; do
  printf "  :%-5s %s\n" "$p" "$(health "$p")"
done
echo "=== 常驻保活：本脚本不退出，子进程不被回收 ==="
while true; do sleep 30; done
