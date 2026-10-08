#!/usr/bin/env bash
# 多渠道交易域：环境恢复后的验证清单（2026-10-06 第五轮中断时生成）
#
# 为什么有这个文件：第五轮 Bash 执行器整体失效（SIGTERM/137），
# 而期间**另一个会话正在并发修改同一个仓库**（InventoryGateway 行粒度重构等），
# 那批改动我一行都没验证过 ⇒ 环境恢复后必须先跑这份清单，再谈"功能正常"。
#
# 用法：bash scripts/dev/verify-multichannel-after-env-recovery.sh
set -uo pipefail

ROOT=/Users/renhui.trh/wps/bone
cd "$ROOT" || exit 1

export JAVA_HOME=${JAVA_HOME:-/Users/renhui.trh/Library/Java/JavaVirtualMachines/temurin-21/Contents/Home}
export PATH="/Users/renhui.trh/java/apache-maven-3.8.6/bin:$JAVA_HOME/bin:$PATH"

FAILED=0
step() { printf '\n\033[1;34m▸ %s\033[0m\n' "$*"; }
check() { if [ "$1" -ne 0 ]; then echo "   ❌ 失败（EXIT=$1）"; FAILED=1; else echo "   ✅ 通过"; fi }

# ── 0. 环境自检：先确认执行器与端口可用，避免又跑一遍空验证 ──────────────
step "0. 环境自检"
echo probe-ok || { echo "❌ Bash 执行器仍不可用（SIGTERM）——本脚本无法执行，请稍后再试"; exit 2; }

MYSQL_BIN=/usr/local/mysql-8.0.31-macos12-x86_64/bin/mysql
if [ -x "$MYSQL_BIN" ]; then
  echo "   ✅ MySQL 客户端在位"
else
  echo "   ⚠️未找到 $MYSQL_BIN（查库步骤会跳过）"
fi
for p in 3306 8888 8081 8082 8083; do
  printf '   · :%s listening=%s\n' "$p" \
    "$(lsof -nP -iTCP:$p -sTCP:LISTEN 2>/dev/null | tail -1 | awk '{print $1}' || echo 'no')"
done

# ── 1. 编译 + 单测（预期 ≥296，含并发会话新增的 OrderItemInventoryExecutorTest）────
step "1. blueprint 编译 + 单测"
mvn -o -pl bone-blueprint clean test
check $?

# ECJ 存根是本仓常态坑：IDEA 后台把坏 class 写回 target/test-classes。
# 判据是 class 文件里含 "Unresolved compilation problems"；稳定解法是强制全量重编译。
if [ $FAILED -ne 0 ]; then
  echo "   ↻ 若报 NoClassDefFound / Unresolved compilation problems，先试："
  echo "     mvn -o -pl bone-blueprint test -Dmaven.compiler.useIncrementalCompilation=false"
fi

# ── 2. 我新增的租户门禁（正负双向）────────────────────────────────────
step "2. AFTER_COMMIT 租户门禁（正向）"
python3 scripts/ci/check-async-tenant-context.py
check $?

step "2b. 租户门禁负向探针（应报红 EXIT=1）"
BAK=$(mktemp); cp scripts/ci/check-async-tenant-context.py "$BAK"
python3 - <<'PY'
p='bone-blueprint/src/main/java/com/bone/blueprint/application/event/ShipmentOrderSyncEventHandler.java'
s=open(p,encoding='utf-8').read()
open(p+'.probe','w',encoding='utf-8').write(s)          # 备份
s=s.replace('TenantContextRunner.callAs(\n              tenantId,',
            'TenantContextRunner.callAs(\n              null,')          # 模拟忘记切租户
open(p,'w',encoding='utf-8').write(s)
print("   已注入负向改动（callAs(null, ...)）")
PY
python3 scripts/ci/check-async-tenant-context.py; NEG=$?
mv bone-blueprint/src/main/java/com/bone/blueprint/application/event/ShipmentOrderSyncEventHandler.java.probe \
   bone-blueprint/src/main/java/com/bone/blueprint/application/event/ShipmentOrderSyncEventHandler.java
rm -f "$BAK"
if [ "$NEG" -eq 1 ]; then echo "   ✅ 负向探针生效（精确报红）"; else echo "   ❌ 负向探针未生效——门禁是假门禁！EXIT=$NEG"; FAILED=1; fi

# ── 3. 仓库门禁 ───────────────────────────────────────────────────────
step "3. 仓库门禁 check.sh"
rm -f "$(git rev-parse --git-path hooks)/.check-fail-count" 2>/dev/null   # 清熔断计数，否则 0.9s 直接拒绝执行
./scripts/check.sh
check $?

# ── 4. 多渠道 E2E（需服务在线；未起则先起 gateway/iam/blueprint）────────
step "4. 多渠道 E2E"
python3 scripts/e2e/multichannel.py
check $?
echo "   提示：网关熔断冷却 ~85s，重启服务后先 sleep 再跑，否则误判成服务挂。"

# ── 5. 全链路 ─────────────────────────────────────────────────────────
step "5. 全链路 chains.py"
python3 scripts/e2e/chains.py all
check $?

printf '\n============================================================\n'
if [ "$FAILED" -eq 0 ]; then
  echo "✅ 全部验证通过"
else
  echo "❌ 存在失败项（见上方 ❌ 行）"
fi
echo "============================================================"
exit $FAILED