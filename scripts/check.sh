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
  echo "  gitleaks 未安装，跳过。注意：GitHub Actions 也【不】执行 gitleaks——密钥扫描目前只在"
  echo "  本机装了 gitleaks 时才生效（HC-004 实测状态为 Manual，见 Bone-DDD-最终实践方案 G-1.7）。"
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
