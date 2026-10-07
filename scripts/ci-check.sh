#!/usr/bin/env bash
# 全量门禁的**唯一实现源**。三个入口各司其职，不要互相拷贝清单（历史上就是这么漂移到
# 断线的：ci.yml 有 14 个门禁、ci-check.sh 有 16 个、check.sh 只跑变更模块，谁也不知道
# 另外两个在跑什么）：
#   check.sh       pre-commit 用 —— 只跑「变更模块」的 ArchUnit + 全树文本门禁，快。
#   ci-local.sh    本地复现 CI —— 直接调本脚本（含 JaCoCo/Gitleaks 等重步骤）。
#   ci.yml         CI 阻断 —— 显式逐条列出每条判据，便于各自标注 blocking 与失败说明。
# 本脚本此前**零调用者**（2026-10-04 诊断 P0-2 实测：全仓 grep 命中全是注释与文档文本），
# 导致 application-constructs / ddl-doc-sync / ddl-required-columns / domain-model-layout /
# tenant-deletion-coverage / tenant-entity-declaration 六个维度完全没有回归保护。
# 现已接进 ci-local.sh；ci.yml 则显式逐条调用（不再是"调 ci-check.sh"这种藏起来的写法）。
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RESET='\033[0m'

echo "🚀 [CI GATE] 全量门禁审查..."

# 全仓文本扫描统一用 `git grep --untracked`，不用 `grep -r`。
# 原因（2026-09-17 实测，本机 I/O 每文件约 26ms）：`grep -r` 会走进
# bone-frontend/node_modules（单目录 643MB / 十万级文件），整树遍历实测 321s，
# 而 CPU 仅 2.6s —— 几乎全部耗在 I/O 等待上。`git grep` 扫同样内容只要 0.37s。
# 它只扫「已跟踪 + 未被忽略的未跟踪」文件，不会误入 node_modules/target/dist。
# 详见 scripts/check.sh [3/15] 与 [5/15] 的同源注释。

echo "🔍 [1/17] ORM 依赖阻断..."
if git grep --untracked -nE "<artifactId>(mybatis|mybatis-plus|spring-boot-starter-data-jpa|hibernate-core)" \
    -- '*pom.xml'; then
  echo -e "${RED}❌ 非法 ORM 依赖！${RESET}"; exit 1
fi

echo "🔍 [2/17] ArchUnit 全量架构检查..."
# -Dsurefire.failIfNoSpecifiedTests=false 是必需的（scripts/check.sh 第3步同款）：
# reactor 里除各业务模块外还有 **bone-architecture-test**（ArchUnit 共享规则的 test-jar，
# 目录本身不存在源码、由各模块以 test scope 引用）。它自己**没有任何测试类**，
# 而 -Dtest='*ArchitectureTest' 会让 surefire 在它上面也执行一次 ⇒
# 「No tests matching pattern」直接判失败（2026-10-06 实测，EXIT=1，2 秒就挂）。
# 加该参数后「模式无匹配」不算失败，但**真实匹配到的测试仍会跑**（不是关掉检查）。
mvn test -Dtest='*ArchitectureTest' -Dsurefire.failIfNoSpecifiedTests=false --batch-mode -q

echo "🔍 [3/17] Gitleaks 密钥扫描..."
# ci-local.sh 现在会调用本脚本，而本地未必装 gitleaks（CI 里是显式下载 8.18.4 二进制的）。
# 缺失时降级为告警而非失败：漏扫密钥是安全问题，必须在输出里**显式可见**，
# 不能像"静默跳过"那样让人以为扫过了。
if command -v gitleaks >/dev/null 2>&1; then
  gitleaks detect --source . --config .gitleaks.toml --verbose
else
  echo -e "${YELLOW}⚠ 未检测到 gitleaks，跳过密钥扫描（CI 侧由 backend-security job 显式安装后执行）。${RESET}"
fi

echo "🔍 [4/17] JaCoCo 覆盖率检查（仅对配置 jacoco 插件的模块生效）..."
# 现状：jacoco 门禁仅 bone-metadata-sdk（80% 行覆盖）配置；其余应用模块未接入，见 Bone-测试策略.md
# 目标口径：Bone-DDD G-1.7 的 HC-005（当前父 POM 门槛 10% 指令覆盖）；全模块铺开后收紧本步
#
# 两个必须写下来的原因（2026-10-06 实测，原命令必挂）：
#
# ① `goal@executionId` 不能省：门槛规则（<rules>）配在父 POM 那个id=check 的
#    <execution> 里，属于 **execution 级**配置；裸 `mvn jacoco:check` 走 default-cli、
#    拿不到它⇒ 报 "The parameters 'rules' for goal ... are missing or invalid"。
#    写成 `jacoco:check@check` 才是"按该 execution 的配置跑这个 goal"。
#
# ② 必须 -pl 限定到真正配了插件的模块：父 POM 把 jacoco 声明在 <pluginManagement> 里，
#    裸调用会对**全仓每个模块**执行 check —— 而未接入的模块没有有效覆盖率数据
#    （本例 bone-datasource 覆盖率不达标 ⇒ Coverage checks have not been met），
#    于是"覆盖率门禁"实际变成了"对未接入模块的误报"。
#    下方 JACOCO_MODULES 才是真源：全仓 grep jacoco-maven-plugin 后逐个核实。
JACOCO_MODULES="bone-engine/bone-metadata-sdk"
mvn -o -pl "$JACOCO_MODULES" jacoco:check@check --batch-mode -q

# 原[5/12]「OpenAPI 契约一致性」与 [6/12]「禁用 ORM import」曾是**装饰性步骤**：
# 只有 echo、没有任何命令，看着像在跑检查，实际什么都不做。这类步骤比没有步骤更糟——
# 它让人以为该维度已被覆盖。原 [6/12] 想做的事实际由原 [10/12] 末尾的 import 扫描兜着，
# 已合并到新的 [7/17]。OpenAPI 契约已由 scripts/check-openapi-contract.py 真正接管，
# 列为 [13/17]。

echo "🔍 [5/17] DDL 检查（表清单同步 + HC-008 必备列）..."
python3 scripts/check-ddl-doc-sync.py || {
  echo -e "${RED}❌ 表清单与 bone-init.sql 不一致（新增/删除表未同步文档）！${RESET}"
  exit 1
}
python3 scripts/check-ddl-required-columns.py --check || {
  echo -e "${RED}❌ 新增表缺必备列（HC-008：tenant_id/created_at/updated_at/deleted）！${RESET}"
  exit 1
}

echo "🔍 [6/17] 租户表 ↔ 实体声明（Bone-多租户规范 §4：DDL 有 tenant_id ≠ SDK 认租户表）..."
python3 scripts/check-tenant-entity-declaration.py --check || {
  echo -e "${RED}❌ 新增租户表未在实体上声明 tenantId（该实体查询不会注入租户条件）！${RESET}"
  exit 1
}

echo "🔍 [7/17] 禁用 ORM import 全量扫描（原 [6/12] 空壳 + 原 [10/12] 末尾合并至此）..."
if git grep --untracked -nE "import\s+org\.apache\.ibatis|import\s+(javax|jakarta)\.persistence|import\s+org\.hibernate|import\s+com\.baomidou" \
    -- '*.java'; then
  echo -e "${RED}❌ 残留禁用 ORM import！${RESET}"; exit 1
fi

echo "🔍 租户离场清除覆盖（R8①：含 tenant_id 且非平台/全局/样板表必须登记离场清退清单）..."
python3 scripts/check-tenant-deletion-coverage.py --check || {
  echo -e "${RED}❌ 存在含 tenant_id 但未登记租户离场清退的表（租户离场会残留数据）！${RESET}"
  exit 1
}

echo "🔍 [8/17] application 层构件白名单（E-10.2 / E-13.2 / ADR-0035：只放 ApplicationService + 契约端口 + support）..."
python3 scripts/check-application-constructs.py --check || {
  echo -e "${RED}❌ application 层出现白名单外的构件（第二类 service / 角色包 / ApplicationService 放错包）！${RESET}"
  exit 1
}

echo "🔍 [9/17] domain 分组布局（ADR-0036 D5：聚合必须包在 domain/model/{聚合}/ 下）..."
python3 scripts/check-domain-model-layout.py || {
  echo -e "${RED}❌ domain 分组布局违规（两套分组并存 / model 下平铺 / 空聚合包 / 根下疑似扁平聚合）！${RESET}"
  exit 1
}

echo "🔍 HC-006 绕过 SDK 的 JDBC/MyBatis 扫描..."
python3 scripts/check-sdk-persistence.py --check || {
  echo -e "${RED}❌ 新增文件直接使用 JDBC / MyBatis 会话（须走 bone-metadata-sdk）！${RESET}"
  exit 1
}

echo "🔍 [10/17] 分页入参命名族（Bone-API-规范 §5.1：新增端点只允许 page/size）..."
# 与 pre-commit 的 scripts/check.sh 是同一个门禁，但必须在 CI 也跑：
# §5.2 路线 ② 的核心是「存量不扩散」，若只挂在 pre-commit，
# 绕过本地提交路径（CI 直推 / 协作者未跑 check.sh）就等于没设防。
# 只阻断第三套命名（pageIndex/perPage）与族内混搭。
# 存量口径更正（2026-10-06 --report-only 实测）：pageNum/pageSize 存量已清零（§5.1/§13.2 同步更新）。
python3 scripts/check-paging-param-names.py || {
  echo -e "${RED}❌ 分页入参出现白名单外的第三套命名或族内混搭（Bone-API-规范 §5.1）！${RESET}"
  exit 1
}

echo "🔍 [11/17] 对外发布模块契约面（空壳 SDK 冒充 + 有业务类却零测试）..."
python3 scripts/check-sdk-contract-surface.py --check || {
  echo -e "${RED}❌ 对外发布模块契约面违规（详见上方处置建议）！${RESET}"
  exit 1
}

echo "🔍 [12/17] 软删声明（@Deleted / @PhysicalDelete 显式化）..."
# 骨核判定聚合可否软删的依据是**实体内有无带 @Deleted 的字段**，与 DDL 有无 deleted 列无关。
# 缺声明时`deleteById` 执行 `DELETE FROM`，行永久消失而调用方拿到 HTTP 200。
python3 scripts/check-soft-delete-declaration.py || {
  echo -e "${RED}❌ 删除语义未显式声明（新增缺口将导致 deleteById 物理删行且调用方无感）！${RESET}"
  exit 1
}

echo "🔍 [13/17] OpenAPI 契约结构（可解析 / 认证声明 / \$ref）..."
# 原 [5/12] 只是 echo 一行「由 ci.yml 的 openapi-diff job 执行」——装饰性步骤。
# 离线结构门禁能真正抓到「YAML 解析不了」「缺 securitySchemes」这类会让 SDK 生成器拿不到契约的缺陷。
python3 scripts/check-openapi-contract.py || {
  echo -e "${RED}❌ OpenAPI 契约违规（无法解析 / 缺 securitySchemes / 缺顶层 security / \$ref 断裂）！${RESET}"
  exit 1
}

echo "🔍 [14/17] 配置键契约（@Value 键名须在 yml 有定义）..."
# --strict 把 WARN 也计为失败：WARN = 键不在任何 yml 定义但给了默认值，实践中几乎都是笔误。
python3 scripts/ci/check-config-key-contract.py --strict || {
  echo -e "${RED}❌ 配置键名漂移（@Value 的键未在任何 yml 定义，Spring 会静默回落默认值）！${RESET}"
  exit 1
}

echo "🔍 [15/17] 文档类名漂移（doc/ 引用的类须真实存在）..."
python3 scripts/check-doc-code-symbols.py || {
  echo -e "${RED}❌ 文档引用了不存在的 Java 类（应修文档或登记基线并说明理由）！${RESET}"
  exit 1
}

echo "🔍 [16/17] 前端响应解包 + 分页 total 归一 + ID 类型契约..."
# 这三项只在前端代码存在时有意义；纯后端仓库/裁剪检出下跳过。
if [ -d bone-frontend/apps ]; then
  python3 scripts/check-frontend-response-contract.py || {
    echo -e "${RED}❌ 前端响应解包契约违规（res.status 当 HTTP 状态用 / 对已解包 body 再取 .data）！${RESET}"
    exit 1
  }
  python3 scripts/check-paging-total-normalize.py || {
    echo -e "${RED}❌ 分页 total 未归一（运行期是字符串，算术前须 normalizeTotal）！${RESET}"
    exit 1
  }
  python3 scripts/frontend/check-frontend-id-types.py --check || {
    echo -e "${RED}❌ 前端 ID 字段声明为 number（后端下发字符串，雪花 ID 会被静默截断）！${RESET}"
    exit 1
  }
else
  echo "  跳过（未找到 bone-frontend/apps）"
fi

echo "🔍 [17/17] freeze 债务台账（G-2：ledger 条目与 archunit_store 非空文件一一对应，violations 计数一致）..."
# G-2 台账首版（doc/architecture/freeze-ledger.yaml）随本门禁落地：台账口径
# 「CI 未消费前只能称人工治理」就此关闭 —— ledger 从创建当天起就有 CI 消费方。
# checker 防两类腐化：① 删条目但 store 仍有真实违规（隐瞒债务）；
# ② violations 计数与 store 文件实际违规行数漂移（数字失真）。
# 新增冻结债务的正确路径：先在 schema 补条目（owner/removalCondition 必填），再提交代码。
python3 scripts/check-freeze-ledger.py || {
  echo -e "${RED}❌ freeze-ledger.yaml 校验失败（隐瞒债务 / store 路径失配 / violations 与 store 实际不一致）！${RESET}"
  exit 1
}

echo "🔍 事件信封版本 / Topic 契约（消息与事件规范 §2/§3：schemaVersion + .v{major} + 载荷兼容性 ratchet）..."
# 载荷删/改名属不兼容变更，须伴随 schemaVersion 主版本递增（基线 diff）。
# 存量 ratchet：--baseline 固化、只可收缩；转阻断的载体即本步（ci-check.sh 全量源）。
python3 scripts/check-event-envelope-version.py --check || {
  echo -e "${RED}❌ 事件信封版本/Topic 契约违规（缺 schemaVersion / Topic 缺 .v{major} / 载荷删改未升主版本）！${RESET}"
  exit 1
}

echo -e "${GREEN}✅ CI 全量门禁通过！（17 项）${RESET}"
