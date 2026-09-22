#!/usr/bin/env bash
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; RESET='\033[0m'

echo "🚀 [CI GATE] 全量门禁审查..."

echo "🔍 [1/7] ORM 依赖阻断..."
if grep -rnE "<artifactId>(mybatis|mybatis-plus|spring-boot-starter-data-jpa|hibernate-core)" \
    --include="pom.xml" --exclude-dir={.git,target} .; then
  echo -e "${RED}❌ 非法 ORM 依赖！${RESET}"; exit 1
fi

echo "🔍 [2/7] ArchUnit 全量架构检查..."
mvn test -Dtest='*ArchitectureTest' --batch-mode -q

echo "🔍 [3/7] Gitleaks 密钥扫描..."
gitleaks detect --source . --config .gitleaks.toml --verbose

echo "🔍 [4/7] JaCoCo 覆盖率检查（仅对配置 jacoco 插件的模块生效）..."
# 现状：jacoco 门禁仅 bone-metadata-sdk（80% 行覆盖）配置；其余应用模块未接入，见 Bone-测试策略.md
# 目标口径：Bone-DDD G-1.7 的 HC-005（当前父 POM 门槛 10% 指令覆盖）；全模块铺开后收紧本步
mvn jacoco:check --batch-mode -q

echo "🔍 [5/7] OpenAPI 契约一致性..."
echo "  （由 ci.yml 的 openapi-diff job 执行 oasdiff）"

echo "🔍 [6/7] 禁用 ORM import 全量扫描..."

echo "🔍 [7/9] DDL 检查（表清单同步 + HC-008 必备列）..."
python3 scripts/check-ddl-doc-sync.py || {
  echo -e "${RED}❌ 表清单与 bone-init.sql 不一致（新增/删除表未同步文档）！${RESET}"
  exit 1
}
python3 scripts/check-ddl-required-columns.py --check || {
  echo -e "${RED}❌ 新增表缺必备列（HC-008：tenant_id/created_at/updated_at/deleted）！${RESET}"
  exit 1
}

echo "🔍 [8/9] 租户表 ↔ 实体声明（Bone-多租户规范 §4：DDL 有 tenant_id ≠ SDK 认租户表）..."
python3 scripts/check-tenant-entity-declaration.py --check || {
  echo -e "${RED}❌ 新增租户表未在实体上声明 tenantId（该实体查询不会注入租户条件）！${RESET}"
  exit 1
}

echo "🔍 [9/9] application 层构件白名单（E-10.2 / E-13.2 / ADR-0035：只放 ApplicationService + 契约端口 + support）..."
python3 scripts/check-application-constructs.py --check || {
  echo -e "${RED}❌ application 层出现白名单外的构件（第二类 service / 角色包 / ApplicationService 放错包）！${RESET}"
  exit 1
}
if grep -rnE "import\s+org\.apache\.ibatis|import\s+(javax|jakarta)\.persistence|import\s+org\.hibernate|import\s+com\.baomidou" \
    --include="*.java" --exclude-dir={.git,target,node_modules} .; then
  echo -e "${RED}❌ 残留禁用 ORM import！${RESET}"; exit 1
fi

echo "🔍 HC-006 绕过 SDK 的 JDBC/MyBatis 扫描..."
python3 scripts/check-sdk-persistence.py --check || {
  echo -e "${RED}❌ 新增文件直接使用 JDBC / MyBatis 会话（须走 bone-metadata-sdk）！${RESET}"
  exit 1
}

echo -e "${GREEN}✅ CI 全量门禁通过！${RESET}"
