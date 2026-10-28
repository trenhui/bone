#!/usr/bin/env bash
# scripts/e2e/run.sh —— 统一入口（让任意 agent / CI 无需记住各脚本名即可跑 e2e）
#
# 分层设计（按「要不要起服务」分层，是整个 e2e 目录的使用骨架）：
#
#   离线层（不需要任何服务，秒级）：lint → selftest
#   环境层（确认栈可用，2 秒）：     preflight
#   业务层（需要完整栈，分钟级）：   py-core / py-sweep / py-chain / node
#
# 用法：
#   ./run.sh list                 # 列出全部分组与成员（agent 先跑这个了解有什么）
#   ./run.sh lint                 # 语法门禁（py_compile + node --check，零依赖）
#   ./run.sh selftest             # 共享层离线自证（fixtures/chain_runner/ui.cjs/JUnit）
#   ./run.sh preflight [--ui|--deep|--all]   # 环境快检（端口 + 可选登录探活）
#   ./run.sh py-core              # 全链路三样板（two-scenario / app-to-data / ui-schema-samples）
#   ./run.sh py-sweep             # 扫描类（api-sweep / real-id-sweep / write-sweep / write_coverage）
#   ./run.sh py-chain             # 链路类（chains / multichannel / channel-buyer / integration-flow）
#   ./run.sh py                   # 全部 Python（core + sweep + chain，共 11 个）
#   ./run.sh node                 # ui/probe-*.mjs 核心 UI 探针
#   ./run.sh all                  # lint + selftest + py（默认；CI 口径）
#   ./run.sh <脚本相对路径或裸名>  # 跑指定脚本，如 ./run.sh two-scenario-full-chain-e2e.py
#
# 环境变量：
#   BONE_E2E_SKIP_PREFLIGHT=1     # 跳过快检（明知栈已备好 / 快速重跑时用）
#   BONE_E2E_JUNIT_DIR=/dir/      # 各脚本把断言写成 <dir>/<套件名>.xml（每套件一个文件，互不覆盖）
#   PYTHON=/path/to/python3       # 指定解释器
#
# 退出码：任一子项失败即非零（供 CI / 编排使用）。
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
PY="${PYTHON:-python3}"
NODE_BIN="$(command -v node || ls /Users/renhui.trh/.workbuddy/binaries/node/versions/*/bin/node 2>/dev/null | head -1)"

# 业务层分组：只在这里声明一次，list / 执行 / 文档共用同一份真相
PY_CORE=(two-scenario-full-chain-e2e.py app-to-data-full-chain-e2e.py ui-schema-samples-e2e.py)
PY_SWEEP=(api-sweep.py real-id-sweep.py write-sweep.py write_coverage.py)
PY_CHAIN=(chains.py multichannel.py channel-buyer-broadcast.py integration-flow-exec-chain.py)

usage() {
  # 取文件头注释块（到第一行非注释实现为止），去掉 '#' 前缀；按行号切会随编辑错位
  sed -n '1,/^set /p' "$0" | grep '^#' | sed 's/^# \{0,1\}//'
}

# 环境快检：业务层分组默认先过它，避免「栈没起 → 11 个脚本各等 40s 超时」
preflight() {
  [ "${BONE_E2E_SKIP_PREFLIGHT:-0}" = "1" ] && {
    echo ">> 跳过 preflight（BONE_E2E_SKIP_PREFLIGHT=1）"
    return 0
  }
  "$PY" "$DIR/preflight.py" "$@"
}

run_py_group() {
  local group_name="$1"; shift
  local files=("$@")
  local failed=0
  for f in "${files[@]}"; do
    echo ">> [$group_name] $f"
    "$PY" "$DIR/$f" || { failed=1; echo "!! 失败: $f"; }
  done
  return "$failed"
}

run_selftest() {
  "$PY" "$DIR/selftest.py" || return 1
  [ -n "$NODE_BIN" ] || { echo "node 未找到，跳过 Node 侧自证"; return 0; }
  "$NODE_BIN" "$DIR/selftest.cjs"
}

MODE="${1:-all}"

case "$MODE" in
  list)
    echo "离线层（无需服务，秒级）"
    echo "  lint        py_compile + node --check 全量语法门禁"
    echo "  selftest    共享层契约自证（selftest.py + selftest.cjs）"
    echo
    echo "环境层"
    echo "  preflight   端口快检（--ui 加前端 / --deep 加登录探活 / --all）"
    echo
    echo "业务层（需完整栈；默认先过 preflight）"
    echo "  py-core     ${PY_CORE[*]}"
    echo "  py-sweep    ${PY_SWEEP[*]}"
    echo "  py-chain    ${PY_CHAIN[*]}"
    echo "  py          上述三组全部"
    echo "  node        ui/probe-*.mjs 核心 UI 探针（需前端 :3000）"
    echo
    echo "辅助（不进分组）"
    echo "  cleanup-residue.py --suffix <s> [--apply]   定点回收残留"
    echo "  channel_simulator.py                        渠道回调模拟器"
    echo "  n7-mutation-verify.sh                       变异验证（证明断言有牙）"
    ;;
  lint)     "$DIR/lint.sh" ;;
  selftest) run_selftest ;;
  preflight) shift; "$PY" "$DIR/preflight.py" "$@" ;;
  py-core)
    preflight || exit $?
    run_py_group py-core "${PY_CORE[@]}"
    ;;
  py-sweep)
    preflight || exit $?
    run_py_group py-sweep "${PY_SWEEP[@]}"
    ;;
  py-chain)
    preflight || exit $?
    run_py_group py-chain "${PY_CHAIN[@]}"
    ;;
  py)
    preflight || exit $?
    rc=0
    run_py_group py-core  "${PY_CORE[@]}"  || rc=1
    run_py_group py-sweep "${PY_SWEEP[@]}" || rc=1
    run_py_group py-chain "${PY_CHAIN[@]}" || rc=1
    exit "$rc"
    ;;
  node)
    [ -n "$NODE_BIN" ] || { echo "node 未找到"; exit 2; }
    preflight --ui || exit $?
    rc=0
    for f in "$DIR"/ui/probe-*.mjs; do
      echo ">> $(basename "$f")"
      "$NODE_BIN" "$f" || { rc=1; echo "!! 失败: $(basename "$f")"; }
    done
    exit "$rc"
    ;;
  all)
    # CI 口径：先离线层（快、无依赖），再业务层（慢、有依赖）
    "$DIR/lint.sh" || exit $?
    run_selftest || exit $?
    preflight || exit $?
    rc=0
    run_py_group py-core  "${PY_CORE[@]}"  || rc=1
    run_py_group py-sweep "${PY_SWEEP[@]}" || rc=1
    run_py_group py-chain "${PY_CHAIN[@]}" || rc=1
    exit "$rc"
    ;;
  -h|--help|help) usage ;;
  *)
    target="$DIR/$MODE"
    [ -f "$target" ] || target="$MODE"
    [ -f "$target" ] || { echo "未知目标: $MODE （用 ./run.sh list 看全部分组）"; exit 2; }
    case "$target" in
      *.py)        "$PY" "$target" ;;
      *.mjs|*.cjs)
        [ -n "$NODE_BIN" ] || { echo "node 未找到"; exit 2; }
        "$NODE_BIN" "$target" ;;
      *.sh)        bash "$target" ;;
      *) echo "不支持的脚本类型: $target"; exit 2 ;;
    esac
    ;;
esac
