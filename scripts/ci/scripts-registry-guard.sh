#!/usr/bin/env bash
# 脚本治理守卫的 CI 单行入口（本地与 CI 跑同一条命令）。
#
# 接线方式（L3：CI 配置修改，需架构师审批后粘贴到 .github/workflows/ci.yml）：
#   - name: Scripts Registry & Catalog Guard (blocking)
#     run: bash scripts/ci/scripts-registry-guard.sh
#
# 为什么单独成脚本：此前「编目守卫」只在 pre-commit 的 unittest discover 里被顺带发现，
# CI（ci.yml）不跑 scripts/tests，--no-verify 或 CI-only 提交可以让守卫整体失效。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

# CI 的 python3 默认只预装了部分包（ci.yml 后段才 pip install pyyaml），而本守卫
# 位于那段之前；doctor 会 fail-fast 检查 PyYAML / PyMySQL，故这里按需装一次。
# 本地已装则完全跳过，不联网、不改本机环境。
if ! python3 -c "import yaml, pymysql" >/dev/null 2>&1; then
  echo "[guard] 按 scripts/requirements-dev.txt 补齐 Python 依赖"
  python3 -m pip install --quiet -r scripts/requirements-dev.txt
fi

# 全部自检收敛为一条命令：sync 对账 / 豁免台账到期 / 59 个判据 / 库外 shadow 实跑 /
# 全量 unittest（含编目守卫与各契约测试）/ doctor。此前这里是 6 行各自为政的命令，
# 既容易漏跑，结果格式也各异；现在本地与 CI 执行的是同一个 bone.py verify。
# 其中 external 用 --report-only --ratchet：只豁免已登记的既有欠债，新增失败仍判红；
# 观察期稳定后去掉 --report-only 即可升级为全量 blocking。
python3 scripts/bone.py verify --format json
