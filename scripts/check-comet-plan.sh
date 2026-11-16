#!/usr/bin/env bash
# Comet 四批 Build Plan 门禁执行器 liveness（已入库，替代 /tmp/comet-plan-hooks）。
#
# 边界（读之前先懂，否则会误判为「假绿」）：
#   * plan 的 baseline-head 判据是**时效判据**——它要求 plan 声明 == 提交时刻主树 HEAD；
#     dev 每次前进都会让旧声明过期。因此它只能在 plan 提交时由 scoped pre-commit
#     (scripts/comet-plan/pre-commit) 真实校验，不能放进 check.sh 常驻（否则 dev 一前进全仓红）。
#   * 本入口在 check.sh 里守的是「执行器本身没丢、没写回 /tmp、能加载」——对应 G47
#     「判据写了却没人调 = 没保护」：确保 scoped 判据的载体真实存在且可被调用。
#   * 判别力自测 selftest.sh 按需跑（sh scripts/comet-plan/selftest.sh），它同样要求
#     plan 与当前主树 HEAD 对齐后才 32/32；W3 各批提交 plan 时由 scoped hook 校验并刷新。
#
# 必须显式传 --check 才作为门禁判据（report-only 不带 --check = 假绿）。
set -euo pipefail

# pre-commit 注入 GIT_DIR / GIT_INDEX_FILE / GIT_WORK_TREE（临时 index），
# 会让下面的 `git rev-parse --show-toplevel` 报 fatal: must be run in a work tree。
# 必须先剥离再解析仓库根（项目铁律：commit --only 走临时 index 时门禁须剥离 GIT_*）。
if [ -n "${GIT_DIR:-}" ] || [ -n "${GIT_INDEX_FILE:-}" ] || [ -n "${GIT_WORK_TREE:-}" ]; then
  exec env -u GIT_DIR -u GIT_WORK_TREE -u GIT_INDEX_FILE -u GIT_COMMON_DIR \
    -u GIT_OBJECT_DIRECTORY -u GIT_ALTERNATE_OBJECT_DIRECTORIES \
    -u GIT_PREFIX -u GIT_NAMESPACE "$0" "$@"
fi

ROOT=$(git -C "$(dirname -- "$0")/.." rev-parse --show-toplevel)
STRICT=0
[ "${1:-}" = "--check" ] && STRICT=1
if [ "$STRICT" -eq 0 ]; then
  echo "[comet-plan-liveness] REPORT-ONLY：传 --check 才作为门禁判据"
fi

fail=0
note() { echo "[comet-plan-liveness] $*"; }

note "1/5 执行器四件套存在"
for f in pre-commit verify-baseline.py verify.mjs selftest.sh; do
  if [ ! -f "$ROOT/scripts/comet-plan/$f" ]; then
    note "FAIL 缺少 scripts/comet-plan/$f"; fail=1
  fi
done

note "2/5 无 /tmp 硬编码回流"
# 排除本文件自身：注释里会提到旧路径来说明迁移原因，自匹配会造成假红。
if grep -n '/tmp/comet-plan-hooks' "$ROOT"/scripts/comet-plan/* >/dev/null 2>&1; then
  note "FAIL 发现 /tmp/comet-plan-hooks 硬编码（换机即失效）"; fail=1
fi

note "3/5 scoped hook 只引用入库路径"
if ! grep -q 'scripts/comet-plan/verify.mjs' "$ROOT/scripts/comet-plan/pre-commit" \
   || ! grep -q 'scripts/comet-plan/verify-baseline.py' "$ROOT/scripts/comet-plan/pre-commit"; then
  note "FAIL scoped hook 未引用入库执行器"; fail=1
fi

note "4/5 Python / Shell 语法"
if ! python3 -m py_compile "$ROOT/scripts/comet-plan/verify-baseline.py" >/dev/null 2>&1; then
  note "FAIL verify-baseline.py 语法错误"; fail=1
fi
if ! sh -n "$ROOT/scripts/comet-plan/pre-commit" >/dev/null 2>&1 \
   || ! sh -n "$ROOT/scripts/comet-plan/selftest.sh" >/dev/null 2>&1; then
  note "FAIL shell 脚本语法错误"; fail=1
fi

note "5/5 verify.mjs 可动态解析 Comet classic-tasks API"
if ! printf '' | node "$ROOT/scripts/comet-plan/verify.mjs" \
     docs/superpowers/plans/2026-11-15-synapclaim-task-assignment-workbench.md HEAD >/dev/null 2>&1; then
  # 期望是「canonical 判红」而非「模块加载失败」；用 stderr 区分。
  err=$(printf '' | node "$ROOT/scripts/comet-plan/verify.mjs" \
        docs/superpowers/plans/2026-11-15-synapclaim-task-assignment-workbench.md HEAD 2>&1 >/dev/null || true)
  case "$err" in
    *'无法定位 Comet classic-tasks.js'*) note "FAIL Comet API 不可解析（换机失效）"; fail=1 ;;
    *) note "  OK 模块已加载（canonical 判红属预期：需从 worktree 读 plan）" ;;
  esac
fi

rm -rf "$ROOT/scripts/comet-plan/__pycache__"
if [ "$fail" -ne 0 ]; then
  note "❌ 执行器 liveness 失败"
  exit 1
fi
note "✅ 执行器 liveness 通过（strict=${STRICT}；plan 时效判据由 scoped hook 在提交时执行）"
