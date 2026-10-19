#!/usr/bin/env bash
# N7 真实变异验证：改实现 → 跑对应用例 → 必须红 → 还原。
# 「跑通即验收」的测试抓不住这类回归；变异是唯一证明。
set -uo pipefail
# 脚本位于 <repo>/scripts/e2e/，vitest 与源码在 <repo>/bone-frontend/
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO/bone-frontend" || exit 1
APP=apps/bone-metadata-app
SRC=$APP/src/utils/uiSchemaEngine.ts
TEST=$APP/src/utils/uiSchemaEngine.test.ts
VITEST=./node_modules/.bin/vitest
PY="${PYTHON:-python3}"

[ -f "$SRC" ] || { echo "FATAL: 源码不存在 $PWD/$SRC"; exit 2; }

# ── 为什么不用 `cp` ──────────────────────────────────────────────────────
# 1) 本机沙箱下 `cp` 偶发 `ENOTSUP: operation not supported on socket`，
#    失败时**直接把目标文件留在不存在状态**（实测把uiSchemaEngine.ts 删了）。
#    改用 Python 读写 + 写前校验，失败即中止而非静默破坏工作树。
# 2)备份路径必须进程唯一：两个脚本实例并发跑（曾发生过：会话重试 + 后台任务）
#    会各自把对方的**变异体**当作"原始备份"写回，导致变异体永久泄漏进源码。
# ──────────────────────────────────────────────────────────────────────
BAK="$("$PY" -c 'import tempfile,os;print(tempfile.mkstemp(prefix="n7_engine_backup.",suffix=".ts")[1])')" || exit 2
LOCK="$BAK.lock"

restore() { # 还原实现；任何失败都必须响亮地失败，不许悄悄留下变异体
  "$PY" - "$BAK" "$SRC" <<'PY'
import io, sys
bak, src = sys.argv[1], sys.argv[2]
data = io.open(bak, encoding='utf-8').read()
io.open(src, 'w', encoding='utf-8').write(data)
# 自证：还原后必须与备份逐字节一致，且源码存在
back = io.open(src, encoding='utf-8').read()
if back != data:
    sys.exit('FATAL: 还原后内容与备份不一致')
if 'MUTATED' in back:
    sys.exit('FATAL: 还原后源码仍含 MUTATED 标记')
PY
}
trap 'restore' EXIT INT TERM

"$PY" -c "import io,sys; io.open(sys.argv[2],'w',encoding='utf-8').write(io.open(sys.argv[1],encoding='utf-8').read())" "$SRC" "$BAK" || exit 2

# 互斥：同类脚本不允许并发（并发正是变异体泄漏与文件互毁的根因）
if ! "$PY" - "$LOCK" <<'PY'
import os, sys
try:
    fd = os.open(sys.argv[1], os.O_CREAT | os.O_EXCL | os.O_WRONLY)
    os.write(fd, str(os.getpid()).encode()); os.close(fd)
except FileExistsError:
    sys.exit(1)
PY
then echo "FATAL: 已有实例在跑（锁 $LOCK 存在）——并发会导致变异体泄漏"; exit 2; fi

# 自证起点是「干净实现」
if grep -q 'MUTATED' "$SRC"; then
  echo "FATAL: 源码已含 MUTATED 标记，说明先前变异泄漏未清理，先人工核对再跑"; exit 2
fi

pass=0; fail=0

mutate() { # $1=描述  $2=测试名过滤  $3=perl 表达式
  local desc="$1" filter="$2" expr="$3"
  restore || { echo "❌ 还原失败，中止：$desc"; fail=$((fail+1)); return; }
  perl -0pi -e "$expr" "$SRC"
  if diff -q "$BAK" "$SRC" >/dev/null; then
    echo "❌ 变异未生效（表达式无匹配）：$desc"; fail=$((fail+1)); restore; return
  fi
  local out
  out=$($VITEST run "$TEST" -t "$filter" 2>&1 | tail -20)
  if echo "$out" | grep -qE "Tests +[0-9]+ failed"; then
    echo "✅ 变异被捕获：$desc"; pass=$((pass+1))
  else
    echo "❌ 变异未被捕获（假绿）：$desc"; fail=$((fail+1))
    echo "$out" | grep -E "Tests +[0-9]+" | head -2
  fi
  restore || { echo "❌ 还原失败，中止"; fail=$((fail+1)); return; }
}

echo "=== N7 变异验证开始 ==="

mutate "MUT-1 rolesVisible fail-closed → fail-open" \
  "ctx.roles 缺失" \
  's/const mine = ctx\.roles \?\? \[\];\n  return roles\.some/const mine = ctx.roles ?? roles;\n  return roles.some/'

mutate "MUT-2 fieldVisible 移除角色门禁（联动可覆盖安全约束）" \
  "联动不得放宽角色门禁" \
  's/  if \(!rolesVisible\(st\.roleRequired, ctx\)\) return false;\n//'

mutate "MUT-3 角色名不再 trim 归一化" \
  "带空格" \
  "s/r\.trim\(\) !== ''\)\.map\(\(r\) => r\.trim\(\)\)/r !== '')/"

mutate "MUT-4 空串/空格角色不过滤（脏数据致控件被整体隐藏）" \
  "含空串" \
  "s/r\.trim\(\) !== ''\)/r !== '')/g"

mutate "MUT-5 roleRequired 取并集而非交集（放宽）" \
  "取交集" \
  's/next\.roleRequired = prevRoles \? prevRoles\.filter\(\(r\) => propsSt\.roleRequired!\.includes\(r\)\)/next.roleRequired = prevRoles ? [...new Set([...prevRoles, ...propsSt.roleRequired!])]/'

mutate "MUT-6 baseStates 不再裁剪默认键（热路径性能回归）" \
  "只保留有内容的键" \
  's/  return Object\.fromEntries\(Object\.entries\(out\)\.filter\(\(\[, v\]\) =>\s*\n\s*v\.visible === false \|\| v\.readonly === true \|\| !!v\.roleRequired\?\.length \|\| !!v\.tier\?\.length\)\);/  return out;/'

echo "=== 结果：通过 $pass/失败 $fail ==="
restore || exit 2
rm -f "$LOCK" 2>/dev/null
[ "$fail" -eq 0 ]
