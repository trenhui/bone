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
# 路径真源（2026-10-26 修）：此前硬编码 `/Users/renhui.trh/wps/bone` 与 temurin-21 绝对路径，
# 仓库一换目录/机器即全部失效（表现为 mvn 找不到、java 找不到、.env 读不到）。
# 现统一按「脚本位置推仓库根 + 本机探测 JDK/Maven」取值，任何 checkout 都能直接跑。
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
detect_java() {
  local c
  for c in "${JAVA_HOME:-}" /Users/renhui.trh/tools/jdk/jdk-21.0.5.jdk/Contents/Home \
           /Library/Java/JavaVirtualMachines/*/Contents/Home /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home; do
    if [ -n "$c" ] && [ -x "$c/bin/java" ]; then printf '%s' "$c/bin/java"; return 0; fi
  done
  command -v java || true
}
JAVA="$(detect_java)"
[ -n "$JAVA" ] || { echo "[fatal] 未找到 JDK 21（设置 JAVA_HOME 或安装 JDK21）" >&2; exit 1; }
detect_mvn_dir() {
  local m
  m="$(command -v mvn 2>/dev/null)" && { printf '%s' "$(dirname "$m")"; return 0; }
  for m in /Users/renhui.trh/.m2/wrapper/dists/apache-maven-*/bin/apache-maven-*/bin/mvn \
           /Users/renhui.trh/java/apache-maven-*/bin/mvn /usr/local/apache-maven-*/bin/mvn /opt/homebrew/bin/mvn; do
    if [ -x "$m" ]; then printf '%s' "$(dirname "$m")"; return 0; fi
  done
}
M="$(detect_mvn_dir)"
[ -n "$M" ] || { echo "[fatal] 未找到 Maven" >&2; exit 1; }
# JAVA_HOME 供 mvn spring-boot:run 派生 JVM（同一 JDK，避免 PATH 里的 JDK8 抢跑）
JH="$(cd "$(dirname "$JAVA")/.." && pwd)"
LOG=/tmp/bone-e2e
mkdir -p "$LOG"
echo "[env] ROOT=$ROOT"
echo "[env] JAVA=$JAVA"
echo "[env] MVN=$M"
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
      PATH="$M:$JH/bin:$PATH" JAVA_HOME="$JH" \
      mvn spring-boot:run \
      -Dspring-boot.run.useTestClasspath=false \
      -Dspring-boot.run.jvmArguments="-Xms96m -Xmx448m -XX:TieredStopAtLevel=1 -Dserver.port=$3" \
      > "$LOG/$2.log" 2>&1 < /dev/null & ) 2>/dev/null
  wait_up "$3" "$2"
}

up_jar() { # jar name port heap [extra spring args...]
  if lsof -nP -iTCP:"$3" -sTCP:LISTEN -t >/dev/null 2>&1; then echo "  · $2 (:$3) 已在运行"; return 0; fi
  echo "  [start] $2 → :$3 (java -jar)"
  ( cd "$ROOT" && nohup "$JAVA" -Xms96m -Xmx"${4:-448}m" -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=192m \
      -jar "$ROOT/$1" --server.port="$3" "${@:5}" \
      --spring.cloud.nacos.discovery.enabled=false --spring.cloud.nacos.config.enabled=false \
      > "$LOG/$2.log" 2>&1 < /dev/null & ) 2>/dev/null
  wait_up "$3" "$2"
}

echo "=== 阶段1：Nacos（config center；iam/system/masterdata/integration/file 的 spring.config.import 依赖它）==="
NACOS_HOME=""
for d in /usr/local/nacos-server-*/nacos /usr/local/nacos /opt/nacos "${HOME}/apps/nacos" "${HOME}/nacos"; do
  if [ -x "$d/bin/startup.sh" ]; then NACOS_HOME="$d"; break; fi
done
if [ -z "$NACOS_HOME" ]; then
  echo "  ! 未找到 Nacos 安装，依赖 config.import 的 5 个模块会启动失败"
else
  if curl -s -m 3 --noproxy '*' -o /dev/null "http://127.0.0.1:8848/nacos/" 2>/dev/null; then
    echo "  · nacos (:8848) 已在运行"
  else
    echo "  [start] nacos → :8848 ($NACOS_HOME)"
    ( cd "$NACOS_HOME" && env JAVA_HOME="$JH" bash bin/startup.sh -m standalone > /tmp/nacos-start.log 2>&1 )
    for _ in $(seq 1 40); do
      sleep 3
      curl -s -m 3 --noproxy '*' -o /dev/null "http://127.0.0.1:8848/nacos/" 2>/dev/null && break
    done
  fi
  echo "  nacos 8848 = $(curl -s -m 3 --noproxy '*' -o /dev/null -w '%{http_code}' http://127.0.0.1:8848/nacos/ 2>/dev/null)"
fi

echo "=== 阶段2：网关 ==="
up_jar bone-platform/bone-gateway/target/bone-gateway-1.0.0.jar gateway 8888 384

echo "=== 阶段3：依赖 Nacos（必须 mvn 通道）==="
up_mvn bone-platform/bone-iam         iam         8081
up_mvn bone-platform/bone-system      system      8083
up_mvn bone-platform/bone-masterdata  masterdata  8084
up_mvn bone-platform/bone-integration integration 8085
up_mvn bone-platform/bone-file        file        8107

echo "=== 阶段4：无 Nacos（jar 即可）==="
up_jar bone-blueprint/target/bone-blueprint-1.0.0.jar blueprint 8082 448
up_jar bone-engine/bone-metadata-server/target/bone-metadata-server-1.0.0-SNAPSHOT-exec.jar metadata 9001 512
up_jar bone-engine/studio-generator/target/bone-studio-generator-1.0.0.jar generator 8086 448
# extension-studio：基配置 datasource 是 H2 内存库，dev profile 又把持久化切到 metadata 模式，
# 两者叠加会去查一张空的 H2 库 ⇒ `Table "EXTS_EXTENSION_POINT" not found` 启动即退出。
# 真联调须叠加 metadata-mysql profile（读 .env 的 BONE_DB_*，表由 bone-init.sql 提供）。
# 另注：数据面上报共享密钥未注入时会打一条 ERROR 并按「失败关闭」拒绝上报（设计行为，非故障）。
up_jar bone-engine/bone-extension-engine/bone-extension-studio/target/bone-extension-studio-1.0.0.jar extension 8088 512 \
  --spring.profiles.active=dev,metadata-mysql
up_jar bone-apps/synap-claim/target/synap-claim-1.0.0.jar claim 8087 512
# ai-server：application.yaml 读的是 DB_URL/DB_USERNAME/DB_PASSWORD（不是 .env 的 BONE_DB_*），
# 直接 java -jar 会因密码为空、驱动类判定失败而 APPLICATION FAILED TO START，故显式下发数据源三件套。
up_jar bone-ai/bone-ai-server/target/bone-ai-server-1.0.0-SNAPSHOT.jar ai-server 8091 512 \
  --spring.datasource.url="${BONE_DB_URL}" \
  --spring.datasource.username="${BONE_DB_USERNAME}" \
  --spring.datasource.password="${BONE_DB_PASSWORD}"

echo "=== 终态 ==="
rc=0
for p in 8888 8081 8082 8083 8084 8085 8086 8087 8088 8091 8107 9001; do
  printf "  :%-5s %s\n" "$p" "$(health "$p")"
done
echo "=== 常驻保活：本脚本不退出，子进程不被回收 ==="
while true; do sleep 30; done
