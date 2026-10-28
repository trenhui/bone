#!/usr/bin/env bash
# scripts/e2e/lint.sh —— 语法自检门禁（零依赖，CI / 各 agent 可复用）
#
# 对所有 .py 跑 `python3 -m py_compile`，对所有 .mjs/.cjs 跑 `node --check`。
# 任一文件语法错误即返回非零退出码，供 CI 与 agent 编排判定。
# 注意：只验「能解析」，不验「能跑」——业务联通需起服务后用 run.sh 的 py/node 模式。
set -uo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
PY="${PYTHON:-python3}"
NODE_BIN="$(command -v node || ls /Users/renhui.trh/.workbuddy/binaries/node/versions/*/bin/node 2>/dev/null | head -1)"
rc=0

echo "== py_compile =="
while IFS= read -r f; do
  if ! "$PY" -m py_compile "$f" 2>/tmp/e2e_lint_py.err; then
    echo "FAIL: $f"; sed 's/^/    /' /tmp/e2e_lint_py.err; rc=1
  fi
done < <(find "$DIR" -name '*.py' -not -path '*/__pycache__/*')
[ "$rc" -eq 0 ] && echo "py OK"

echo "== node --check =="
if [ -z "$NODE_BIN" ]; then
  echo "SKIP: node 未找到（不影响 py 结果，rc 保持）"
else
  nrc=0
  while IFS= read -r f; do
    if ! "$NODE_BIN" --check "$f" 2>/tmp/e2e_lint_js.err; then
      echo "FAIL: $f"; sed 's/^/    /' /tmp/e2e_lint_js.err; nrc=1
    fi
  done < <(find "$DIR" \( -name '*.mjs' -o -name '*.cjs' \) -not -path '*/__pycache__/*')
  [ "$nrc" -eq 0 ] && echo "node OK"
  [ "$nrc" -ne 0 ] && rc=1
fi

exit $rc
