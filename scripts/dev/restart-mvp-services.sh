#!/usr/bin/env bash
# 重启 MVP 涉及的后端服务（用于 SDK / bone-core 更新后让运行时拿到新 jar）。
#
# 背景：服务以 `mvn spring-boot:run` 启动，JVM 持有启动时刻的 jar 句柄；
# 之后 `mvn install` 更新了 bone-metadata-sdk / bone-core，运行中的进程仍加载旧类，
# 表现为 NoClassDefFoundError（如 QueryContext$Order）。重启是唯一修复手段。
#
# 用法：
#   bash scripts/dev/restart-mvp-services.sh            # 重启全部 MVP 服务
#   bash scripts/dev/restart-mvp-services.sh iam system # 只重启指定服务
#
# 兼容 macOS 自带 bash 3.2（不使用关联数组）。
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-$HOME/Library/Java/JavaVirtualMachines/temurin-21/Contents/Home}"
export PATH="/Users/renhui.trh/java/apache-maven-3.8.6/bin:$JAVA_HOME/bin:$PATH"

# 统一本地启动契约：加载仓库根 .env（DB/Redis/密钥）+ 固定 dev profile。
# 反面教训（2026-10-31）：不带 dev profile 起的 IAM 会出现
# 「/actuator/health 秒回 200，而 / 、404、/api/v1/iam/login 全部挂起」——
# 进程在听、health 绿，业务全死。重启必须收敛到与 local-up.sh 相同的一套环境。
if [ -f "${ROOT}/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "${ROOT}/.env"
  set +a
  echo "[env] 已加载 ${ROOT}/.env"
else
  echo "[env][warn] 未找到 ${ROOT}/.env，沿用当前环境变量"
fi
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
echo "[env] SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}"

module_dir() {
  case "$1" in
    gateway)     echo "${ROOT}/bone-platform/bone-gateway" ;;
    iam)         echo "${ROOT}/bone-platform/bone-iam" ;;
    system)      echo "${ROOT}/bone-platform/bone-system" ;;
    masterdata)  echo "${ROOT}/bone-platform/bone-masterdata" ;;
    integration) echo "${ROOT}/bone-platform/bone-integration" ;;
    metadata)    echo "${ROOT}/bone-engine/bone-metadata-server" ;;
    generator)   echo "${ROOT}/bone-engine/studio-generator" ;;
    extension)   echo "${ROOT}/bone-engine/bone-extension-studio" ;;
    file)        echo "${ROOT}/bone-platform/bone-file" ;;
    *)           echo "" ;;
  esac
}

module_port() {
  case "$1" in
    gateway) echo 8888 ;; iam) echo 8081 ;; system) echo 8083 ;; masterdata) echo 8084 ;;
    integration) echo 8085 ;; metadata) echo 9001 ;; generator) echo 8086 ;;
    extension) echo 8088 ;; file) echo 8107 ;; *) echo "" ;;
  esac
}

# gateway 以 `java -jar` 长期运行（不是 spring-boot:run），且必须先 package
start_command() {
  case "$1" in
    gateway)
      echo "mvn -o package -DskipTests -Djacoco.skip=true -Dspotbugs.skip=true && java -jar target/bone-gateway-1.0.0.jar"
      ;;
    file)
      # bone-file 的 secret-key 读取 ${BONE_IAM_JWT_SECRET_KEY}（无默认），需显式注入；
      # 回退到统一的 BONE_JWT_SECRET，确保与其他服务 JWT 验签一致。
      echo "BONE_IAM_JWT_SECRET_KEY=\"\${BONE_IAM_JWT_SECRET_KEY:-\${BONE_JWT_SECRET:-dev-only-secret-key-minimum-32-bytes-long}}\" BONE_SERVER_PORT=8107 mvn -o spring-boot:run"
      ;;
    *) echo "mvn -o spring-boot:run" ;;
  esac
}

if [ "$#" -gt 0 ]; then
  targets=("$@")
else
  targets=(iam system masterdata integration metadata generator extension file)
fi

for svc in "${targets[@]}"; do
  dir="$(module_dir "${svc}")"
  port="$(module_port "${svc}")"
  if [ -z "${dir}" ] || [ ! -d "${dir}" ]; then
    echo "[skip] 未知服务或无目录：${svc}"
    continue
  fi
  pid="$(lsof -nP -iTCP:"${port}" -sTCP:LISTEN -t 2>/dev/null | head -1)"
  if [ -n "${pid}" ]; then
    echo "[stop] ${svc} (pid=${pid}, port=${port})"
    # 记录被杀者的启动命令：多会话共享本机时，只有留下这条才能事后分辨
    # 「谁起的、带没带 dev profile」，否则只剩一个无法追溯的 SIGTERM。
    if [ -x "${JAVA_HOME}/bin/jcmd" ]; then
      "${JAVA_HOME}/bin/jcmd" "${pid}" VM.command_line 2>/dev/null \
        | grep -m1 "java_command" | cut -c1-160 | sed 's/^/[stop]   /' || true
    fi
    kill "${pid}" 2>/dev/null
    for _ in $(seq 1 40); do
      sleep 1
      if ! lsof -nP -iTCP:"${port}" -sTCP:LISTEN -t >/dev/null 2>&1; then break; fi
    done
  fi
  echo "[start] ${svc} → ${port}"
  ( cd "${dir}" && nohup bash -c "$(start_command "${svc}")" >"/tmp/bone-${svc}-restart.log" 2>&1 </dev/null & )
done

echo "已在后台启动：${targets[*]}；日志 /tmp/bone-<svc>-restart.log"
echo "就绪校验（端口→health→业务 smoke，health 绿但业务挂会判红）："
echo "  bash scripts/dev/svc-ready.sh ${targets[*]} 180"
