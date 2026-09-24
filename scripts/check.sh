#!/usr/bin/env bash
set -euo pipefail

YELLOW='\033[1;33m'; GREEN='\033[0;32m'; RED='\033[0;31m'; RESET='\033[0m'
# worktree 安全：在 `git worktree` 里 `.git` 是**文件**（gitdir 指针），`.git/hooks/...`
# 会被解析成「Not a directory」，收尾的 `rm -f` 因而失败并触发 set -e 中断提交。
# 用 git 自己解析 hooks 目录（worktree 下返回共享 hooks 目录的绝对路径），
# 顺带让熔断计数跨 worktree 共享——它守卫的是人，不是某个检出。
FAIL_COUNT_FILE="$(git rev-parse --git-path hooks 2>/dev/null || echo .git/hooks)/.check-fail-count"
MAX_RETRIES=3

# 熔断检查
if [ -f "$FAIL_COUNT_FILE" ]; then
  count=$(cat "$FAIL_COUNT_FILE" 2>/dev/null || echo 0)
  if [ "$count" -ge "$MAX_RETRIES" ]; then
    echo -e "${RED}[MELTDOWN] 架构约束连续失败 ${count} 次，需人工介入。${RESET}"
    echo "清除计数：rm $FAIL_COUNT_FILE"
    exit 1
  fi
fi

exit_code=0

echo -e "${YELLOW}[1/5] 代码风格校验 (Spotless)...${RESET}"
mvn spotless:check --batch-mode -q || exit_code=$?

echo -e "${YELLOW}[2/5] 检测变更模块...${RESET}"
CHANGED_FILES=$(git diff --cached --name-only --diff-filter=ACM 2>/dev/null | grep '\.java$' || true)
if [ -n "$CHANGED_FILES" ]; then
  # 注意：`|| true` 必须放在整条管道末尾。管道优先级高于 `||`，若写成 `grep ... || true | sed ...`
  # 会被解析成 `(grep) || (true | sed)`，sed 拿不到输入，MODULE_PATHS 变成文件全路径，
  # 导致下面 $MODULE_PATH/pom.xml 恒不存在、ArchUnit 门禁被静默跳过。
  MODULE_PATHS=$(echo "$CHANGED_FILES" | grep 'src/main/java' | sed 's|/src/main/java/.*||' | sort -u || true)

  if [ -n "$MODULE_PATHS" ]; then
    echo "  变更模块:"
    echo "$MODULE_PATHS" | sed 's/^/    - /'
    echo -e "${YELLOW}[2/5] 就近 ArchUnit 架构检查...${RESET}"
    # 逐模块用 -f 指定 pom 运行（避免 -pl 在多模块/嵌套模块下 reactor 路径解析不稳）
    while IFS= read -r MODULE_PATH; do
      [ -z "$MODULE_PATH" ] && continue
      POM="$MODULE_PATH/pom.xml"
      if [ -f "$POM" ]; then
        echo "  ArchUnit: $MODULE_PATH"
        mvn -f "$POM" test -Dtest='*ArchitectureTest' -Dsurefire.failIfNoSpecifiedTests=false --batch-mode -q || exit_code=$?
      fi
    done <<< "$MODULE_PATHS"
  fi
fi

# P1-16：（2026-09-24 新增）共享门禁库变更 → 全量 ArchUnit 回归
# 改动 bone-architecture-test 的共享规则会同时影响所有模块的 ArchitectureTest，
# 仅跑变更模块无法发现「别处模块回归」，故扫描全部含 ArchitectureTest 的模块逐個 -f 运行
# （沿用 [2/5] 的 -f 逐模块机制，避开 reactor 路径解析不稳与前端 node 模块）。
if echo "$CHANGED_FILES" | grep -q 'bone-framework/bone-architecture-test/'; then
  echo -e "${YELLOW}[2/5] 共享门禁库变更 → 全量 ArchUnit 回归...${RESET}"
  # 用 while-read 收集，避免 mapfile（bash ≥4.0 才有；macOS 默认 /bin/bash 3.2 无该内建）
  ARCH_MODULES=()
  while IFS= read -r line; do
    [ -n "$line" ] && ARCH_MODULES+=("$line")
  done < <(find . -path '*/src/test/java/*/ArchitectureTest.java' \
    -not -path '*/node_modules/*' -not -path '*/target/*' 2>/dev/null \
    | sed 's|/src/test/java/.*||' | sort -u)
  for MODULE_PATH in "${ARCH_MODULES[@]:-}"; do
    [ -z "$MODULE_PATH" ] && continue
    POM="$MODULE_PATH/pom.xml"
    [ -f "$POM" ] || continue
    echo "  ArchUnit: $MODULE_PATH"
    mvn -o -f "$POM" test -Dtest='*ArchitectureTest' -Dsurefire.failIfNoSpecifiedTests=false --batch-mode -q || exit_code=$?
  done
fi

echo -e "${YELLOW}[3/5] ORM 框架拦截...${RESET}"
# 用 `git grep` 而非 `grep -r`：只扫「已跟踪 + 未被忽略的未跟踪」文件，不整树遍历。
# 2026-09-17 实测（本机 I/O 每文件约 26ms）：`grep -r` 走过 12,153 个文件耗 321s，
# 而其中 CPU 仅 2s（user 0.43s + sys 1.61s）——几乎全部是 I/O 等待；
# `git grep` 扫同样内容只要 0.37s（2,053 个 .java）。语义不变，且比 --exclude-dir
# 逐个猜忽略目录更可靠（原实现漏了 dist/、build/ 等）。
if git grep --untracked -nE "import\s+org\.apache\.ibatis|import\s+(javax|jakarta)\.persistence|import\s+org\.hibernate|import\s+com\.baomidou" \
    -- '*.java'; then
  echo -e "${RED}❌ 检测到禁用的 ORM 框架 import！${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[4/5] 密钥泄露扫描 (Gitleaks)...${RESET}"
if command -v gitleaks &>/dev/null; then
  gitleaks protect --staged --config .gitleaks.toml --verbose || exit_code=$?
else
  echo "  gitleaks 未安装，跳过。注意：GitHub Actions 也【不】执行 gitleaks——密钥扫描目前只在"
  echo "  本机装了 gitleaks 时才生效（HC-004 实测状态为 Manual，见 Bone-DDD-最终实践方案 G-1.7）。"
fi

echo -e "${YELLOW}[5/5] pom.xml 依赖检查...${RESET}"
# 同 [3/5] 改用 `git grep`。原实现**未**排除 node_modules（bone-frontend/node_modules
# 单目录 643MB / 十万级文件），实测 14 分钟以上跑不完，是 pre-commit 最大耗时项。
if git grep --untracked -nE "<artifactId>(mybatis|mybatis-plus|spring-boot-starter-data-jpa|hibernate-core)" \
    -- '*pom.xml'; then
  echo -e "${RED}❌ pom.xml 中检测到禁用的 ORM 依赖！${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[6/6] HC-006 绕过 SDK 的 JDBC/MyBatis 扫描...${RESET}"
if ! python3 scripts/check-sdk-persistence.py --check; then
  echo -e "${RED}❌ 新增文件直接使用 JDBC / MyBatis 会话（须走 bone-metadata-sdk，存量见 sdk-persistence-bypass-baseline.json）${RESET}"
  exit_code=1
fi

# 结果处理 + 熔断计数
if [ "$exit_code" -ne 0 ]; then
  count=$(cat "$FAIL_COUNT_FILE" 2>/dev/null || echo 0)
  count=$((count + 1))
  echo "$count" > "$FAIL_COUNT_FILE"
  echo -e "${RED}❌ 自检失败（第 ${count}/${MAX_RETRIES} 次）${RESET}"
  if [ "$count" -ge "$MAX_RETRIES" ]; then
    echo -e "${RED}[MELTDOWN] 已达熔断阈值，后续提交将被阻断。${RESET}"
  fi
  exit 1
else
  rm -f "$FAIL_COUNT_FILE"
  echo -e "${GREEN}✅ 本地自检通过！${RESET}"
fi
