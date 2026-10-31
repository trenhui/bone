#!/usr/bin/env bash
# 服务「功能性就绪」闸门：端口监听 → health 200 → 业务 smoke。
#
# 为什么要三层：2026-10-31 排查 IAM 时踩到的假绿是
#   /actuator/health 秒回 200，而 `/`、404、`/api/v1/iam/login` 全部挂起 6s 超时
#   （进程活着、端口在听、health 绿，但业务链路已坏死）。
# 只看 health 会得出「服务正常」的错误结论，因此就绪必须验到业务路径。
#
# 用法：
#   bash scripts/dev/svc-ready.sh iam            # 最多等 120s
#   bash scripts/dev/svc-ready.sh gateway 60     # 自定义超时
#   bash scripts/dev/svc-ready.sh all            # 批量（gateway iam claim）
#
# 退出码：0 就绪 / 2 端口始终无监听 / 3 health 未 200 / 4 业务 smoke 失败
#
# 兼容 macOS 自带 bash 3.2（不使用关联数组）。不打印任何密钥。
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-${0}}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# 本地 http 一律绕代理，否则 corporate proxy 会把 127.0.0.1 劫走
CURL=(curl -s --noproxy '*' -m 5 -o /dev/null -w '%{http_code}')

port_of() {
  case "$1" in
    gateway) echo 8888 ;;
    iam) echo 8081 ;;
    claim) echo 8087 ;;
    system) echo 8083 ;;
    masterdata) echo 8084 ;;
    integration) echo 8085 ;;
    metadata) echo 9001 ;;
    generator) echo 8086 ;;
    extension) echo 8088 ;;
    file) echo 8107 ;;
    *) echo "" ;;
  esac
}

# 是否有专属业务 smoke（没有的服务只验 health，不能谎称「smoke 通过」）
has_smoke() {
  case "$1" in
    iam) return 0 ;;
    *) return 1 ;;
  esac
}

# 业务 smoke：返回空串表示无专属 smoke（只验 health）
# 有 smoke 的服务必须真的调到业务接口并拿到 200，否则不算就绪。
smoke_check() {
  local svc="$1" port="$2"
  case "${svc}" in
    iam)
      # 登录是全链路（DB + 密码校验 + 令牌签发）的第一跳，最容易暴露「health 绿但业务挂」
      local user="${BONE_SMOKE_USER:-admin}"
      local pass="${BONE_SMOKE_PASS:-123456}"
      local body code
      body="$(curl -s --noproxy '*' -m 8 -X POST "http://127.0.0.1:${port}/api/v1/iam/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"${user}\",\"password\":\"${pass}\"}" 2>/dev/null)"
      code="$?"
      if [ "${code}" -ne 0 ]; then
        echo "登录请求未完成（curl exit=${code}，超时/断连）；health 绿但业务链路挂起"
        return 1
      fi
      case "${body}" in
        *token* | *Token*) return 0 ;;
        *)
          echo "登录未返回令牌：$(printf '%s' "${body}" | head -c 120)"
          return 1
          ;;
      esac
      ;;
    *) return 0 ;;
  esac
}

wait_ready() {
  local svc="$1" timeout="$2"
  local port deadline health_code
  # BONE_READY_PORT 用于非标端口/自测（例如验证「无监听」分支）
  port="${BONE_READY_PORT:-$(port_of "${svc}")}"
  if [ -z "${port}" ]; then
    echo "[svc-ready] ${svc}: 未知服务（无端口映射）"
    return 2
  fi

  deadline=$(( $(date +%s) + timeout ))
  health_code=000
  while [ "$(date +%s)" -lt "${deadline}" ]; do
    if ! lsof -nP -iTCP:"${port}" -sTCP:LISTEN -t >/dev/null 2>&1; then
      sleep 2
      continue
    fi
    health_code="$("${CURL[@]}" "http://127.0.0.1:${port}/actuator/health" 2>/dev/null)"
    if [ "${health_code}" = "200" ]; then
      if ! has_smoke "${svc}"; then
        echo "[svc-ready] ${svc}:${port} 就绪（health 200；该服务无专属 smoke）"
        return 0
      fi
      if smoke_check "${svc}" "${port}"; then
        echo "[svc-ready] ${svc}:${port} 就绪（health 200 + smoke 通过）"
        return 0
      fi
      # health 绿但 smoke 红 —— 不再空等，直接判失败（等下去也不会自愈）
      echo "[svc-ready] ${svc}:${port} health 200 但业务 smoke 失败"
      return 4
    fi
    sleep 2
  done

  if ! lsof -nP -iTCP:"${port}" -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "[svc-ready] ${svc}:${port} ${timeout}s 内始终无监听"
    return 2
  fi
  echo "[svc-ready] ${svc}:${port} ${timeout}s 内 health 未就绪（最后 http=${health_code}）"
  return 3
}

main() {
  local target="${1:-}" timeout="${2:-120}"
  if [ -z "${target}" ]; then
    echo "用法: bash scripts/dev/svc-ready.sh <gateway|iam|claim|all|...> [超时秒数]"
    exit 1
  fi
  if [ "${target}" = "all" ]; then
    local svc rc worst=0
    for svc in gateway iam claim; do
      wait_ready "${svc}" "${timeout}"
      rc="$?"
      [ "${rc}" -ne 0 ] && worst="${rc}"
    done
    exit "${worst}"
  fi
  wait_ready "${target}" "${timeout}"
}

main "$@"
