#!/usr/bin/env bash
set -euo pipefail

YELLOW='\033[1;33m'; GREEN='\033[0;32m'; RED='\033[0;31m'; RESET='\033[0m'
FAIL_COUNT_FILE=".git/hooks/.check-fail-count"
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
  MODULE_PATHS=$(echo "$CHANGED_FILES" | grep 'src/main/java' | sed 's|/src/main/java/.*||' | sort -u)

  if [ -n "$MODULE_PATHS" ]; then
    PL_ARGS=$(echo "$MODULE_PATHS" | tr '\n' ',' | sed 's/,$//')
    echo "  变更模块: $PL_ARGS"
    echo -e "${YELLOW}[2/5] 就近 ArchUnit 架构检查...${RESET}"
    mvn test -Dtest='*ArchitectureTest' -pl "$PL_ARGS" --batch-mode -q || exit_code=$?
  fi
fi

echo -e "${YELLOW}[3/5] ORM 框架拦截...${RESET}"
if grep -rnE "import\s+org\.apache\.ibatis|import\s+(javax|jakarta)\.persistence|import\s+org\.hibernate|import\s+com\.baomidou" \
    --include="*.java" --exclude-dir={.git,target,node_modules} . 2>/dev/null; then
  echo -e "${RED}❌ 检测到禁用的 ORM 框架 import！${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[4/5] 密钥泄露扫描 (Gitleaks)...${RESET}"
if command -v gitleaks &>/dev/null; then
  gitleaks protect --staged --config .gitleaks.toml --verbose || exit_code=$?
else
  echo "  gitleaks 未安装，跳过（CI 会执行）"
fi

echo -e "${YELLOW}[5/5] pom.xml 依赖检查...${RESET}"
if grep -rnE "<artifactId>(mybatis|mybatis-plus|spring-boot-starter-data-jpa|hibernate-core)" \
    --include="pom.xml" --exclude-dir={.git,target} . 2>/dev/null; then
  echo -e "${RED}❌ pom.xml 中检测到禁用的 ORM 依赖！${RESET}"
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
