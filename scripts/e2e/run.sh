#!/usr/bin/env bash
# scripts/e2e/run.sh —— 统一入口（让任意 agent / CI 无需记住各脚本名即可跑 e2e）
#
# 用法：
#   ./run.sh lint                 # 语法自检门禁（零依赖；等价于 lint.sh）
#   ./run.sh py                   # 跑全部 Python 全链路（two-scenario / app-to-data / ui-schema-samples）
#   ./run.sh node                 # 跑 ui/ 下核心 UI 探针（需先起前端 + 服务，见 ui/README.md）
#   ./run.sh all                  # lint + py（默认）
#   ./run.sh <脚本相对路径或裸名>  # 跑指定脚本，如 ./run.sh two-scenario-full-chain-e2e.py
#
# 退出码：任一子项失败即非零（供 CI / 编排使用）。
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
PY="${PYTHON:-python3}"
NODE_BIN="$(command -v node || ls /Users/renhui.trh/.workbuddy/binaries/node/versions/*/bin/node 2>/dev/null | head -1)"
MODE="${1:-all}"

run_py() {
  "$PY" "$DIR/two-scenario-full-chain-e2e.py" && \
  "$PY" "$DIR/app-to-data-full-chain-e2e.py" && \
  "$PY" "$DIR/ui-schema-samples-e2e.py"
}

case "$MODE" in
  lint) "$DIR/lint.sh" ;;
  py)   run_py ;;
  node)
    [ -n "$NODE_BIN" ] || { echo "node 未找到"; exit 2; }
    for f in "$DIR"/ui/probe-*.mjs; do
      echo ">> $f"; "$NODE_BIN" "$f" || exit 1
    done
    ;;
  all)
    "$DIR/lint.sh" && run_py
    ;;
  *)
    target="$DIR/$MODE"
    [ -f "$target" ] || target="$MODE"
    [ -f "$target" ] || { echo "未知目标: $MODE （用 ./run.sh lint|py|node|all 或 <脚本名>）"; exit 2; }
    case "$target" in
      *.py)        "$PY" "$target" ;;
      *.mjs|*.cjs)
        [ -n "$NODE_BIN" ] || { echo "node 未找到"; exit 2; }
        "$NODE_BIN" "$target" ;;
      *) echo "不支持的脚本类型: $target"; exit 2 ;;
    esac
    ;;
esac
