#!/usr/bin/env bash
set -euo pipefail

# DIM 此前**未定义**，而 [13/29] 的跳过分支引用了 ${DIM}；本脚本 `set -u` ⇒ 一旦走到
# 「未找到 bone-frontend/apps」这个分支（裁剪检出/纯后端仓库很常见），
# 整个 pre-commit 会因 unbound variable **异常终止**，而不是正常跳过。2026-10-04 补齐。
YELLOW='\033[1;33m'; GREEN='\033[0;32m'; RED='\033[0;31m'; DIM='\033[2m'; RESET='\033[0m'
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

echo -e "${YELLOW}[1/29] 代码风格校验 (Spotless)...${RESET}"
mvn spotless:check --batch-mode -q || exit_code=$?

echo -e "${YELLOW}[2/29] 检测变更模块...${RESET}"
# 2026-10-04 修复 P1-6：原判据 `--diff-filter=ACM` + `grep '\.java$'` 有两个静默盲区：
#   ① **删除**的 .java（D 被过滤）→ 删除一个 Controller / 聚合根后，依赖它的架构规则
#      实际已失效，却因为"没有变更模块"而完全不跑 ArchUnit；
#   ② 只改 pom / 前端 / 文档的提交 → CHANGED_FILES 为空 ⇒ [3/29][4/29] 整段不执行，
#      而 `set -e` 下的 `|| true` 会吞掉一切，**连一行说明都没有**。
# 故纳入 D，并在下面 MODULE_PATHS 为空时**显式打印未执行的原因**（静默跳过比跑错更危险）。
CHANGED_FILES=$(git diff --cached --name-only --diff-filter=ACD 2>/dev/null | grep -E '\.java$|\.properties$' || true)
if [ -n "$CHANGED_FILES" ]; then
  # 注意：`|| true` 必须放在整条管道末尾。管道优先级高于 `||`，若写成 `grep ... || true | sed ...`
  # 会被解析成 `(grep) || (true | sed)`，sed 拿不到输入，MODULE_PATHS 变成文件全路径，
  # 导致下面 $MODULE_PATH/pom.xml 恒不存在、ArchUnit 门禁被静默跳过。
  MODULE_PATHS=$(echo "$CHANGED_FILES" | grep 'src/main/java' | sed 's|/src/main/java/.*||' | sort -u || true)

  if [ -n "$MODULE_PATHS" ]; then
    echo "  变更模块:"
    echo "$MODULE_PATHS" | sed 's/^/    - /'
    echo -e "${YELLOW}[3/29] 就近 ArchUnit 架构检查...${RESET}"
    # 逐模块用 -f 指定 pom 运行（避免 -pl 在多模块/嵌套模块下 reactor 路径解析不稳）
    while IFS= read -r MODULE_PATH; do
      [ -z "$MODULE_PATH" ] && continue
      POM="$MODULE_PATH/pom.xml"
      if [ -f "$POM" ]; then
        echo "  ArchUnit: $MODULE_PATH"
        mvn -f "$POM" test -Dtest='*ArchitectureTest' -Dsurefire.failIfNoSpecifiedTests=false --batch-mode -q || exit_code=$?
      fi
    done <<< "$MODULE_PATHS"
  else
    # 消除了原本的静默：只改 pom / 前端 / 文档（无 src/main/java）时，
    # [3/29] 不跑是**正确**的，但必须说清为什么，否则读者会以为门禁漏了。
    echo "  无 src/main/java 变更（仅 ${CHANGED_FILES%%$'\n'*} 等），[3/29] 就近 ArchUnit 跳过；"
    echo "  若本次引入了新依赖/新架构层，请确认 [4/29] 或全量 ArchUnit 已覆盖。"
  fi
else
  echo "  无已 stage 的 .java/.properties 变更，[3/29][4/29] ArchUnit 未执行（非违规）。"
fi

# P1-16：（2026-09-24 新增）共享门禁库变更 → 全量 ArchUnit 回归
# 改动 bone-architecture-test 的共享规则会同时影响所有模块的 ArchitectureTest，
# 仅跑变更模块无法发现「别处模块回归」，故扫描全部含 ArchitectureTest 的模块逐個 -f 运行
# （沿用 [2/29] 的 -f 逐模块机制，避开 reactor 路径解析不稳与前端 node 模块）。
if echo "$CHANGED_FILES" | grep -q 'bone-framework/bone-architecture-test/'; then
  echo -e "${YELLOW}[4/29] 共享门禁库变更 → 全量 ArchUnit 回归...${RESET}"
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

echo -e "${YELLOW}[5/29] ORM 框架拦截 (HC-001)...${RESET}"
# 2026-10-06：改为独立脚本，两处原因——
#  ① 【覆盖度】原判据只列 4 个 artifactId（mybatis / mybatis-plus /
#     spring-boot-starter-data-jpa / hibernate-core），漏掉 mybatis-spring-boot-starter、
#     tk.mybatis:mapper、ibatis-*-starter 等常见写法；负向探针实测该漏网坐标能溜过。
#  ② 【扫描盲区】原用 `git grep --untracked`，但本仓 .gitignore 含 `!**/src/main/**`
#     **反向忽略**规则 ⇒ 未跟踪的 src/main 文件被整片跳过。实测：新建一个含
#     `import org.apache.ibatis` 的未跟踪文件放在 src/main 下，git grep 退出码=1（零命中）。
#脚本改用文件系统遍历兜住，并把 [7/29] 的 pom 扫描并入同一脚本，保证两个面口径一致。
if ! python3 scripts/check-orm-banned.py; then
  exit_code=1
fi

echo -e "${YELLOW}[6/29] 密钥泄露扫描 (Gitleaks)...${RESET}"
if command -v gitleaks &>/dev/null; then
  gitleaks protect --staged --config .gitleaks.toml --verbose || exit_code=$?
else
  echo "  gitleaks 未安装，本地跳过。注意：CI 侧（.github/workflows/ci.yml 的 backend-security"
  echo "  job）已接入 gitleaks 并阻断，本地跳过**不代表**提交可绕过密钥扫描——"
  echo "  真正生效的是 CI 那一道（HC-004 实测状态为 Active，见 Bone-DDD-最终实践方案 G-1.7）。"
  echo "  本机可执行 brew install gitleaks 以在提交前自查。"
fi

# [7/29] pom 依赖检查已并入 [5/29] 的 scripts/check-orm-banned.py（R2 pom 面）。
# 原实现另有性能问题：未排除 node_modules（bone-frontend/node_modules 单目录 643MB /
# 十万级文件），实测 14 分钟以上跑不完，是 pre-commit 最大耗时项。新脚本按源码根目录
# 定向遍历（SOURCE_ROOTS），不经过 node_modules。
echo -e "${YELLOW}[7/29] pom.xml 依赖检查（已并入 [5/29] ORM 门禁的 R2 面）...${RESET}"

echo -e "${YELLOW}[8/29] i18n 同步校验（errorCode ↔ 台账 ↔ 语言包）...${RESET}"
# 与既有 python3 门禁同形态；存量漂移走显式白名单 config/i18n/errorcode-baseline.json，
# 新增码一律不豁免。详见 doc/design/国际化设计方案.md §8.2。
if ! python3 scripts/check-i18n-sync.py; then
  echo -e "${RED}❌ i18n 校验失败（缺译 / 新码未登记 / 两份语言包不对称）${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[9/29] 分页入参命名族（Bone-API-规范 §5.1：新增端点只允许 page/size）...${RESET}"
# 该门禁此前完全游离于所有门禁之外 ⇒ 规范 §5.1「存量收敛完成即禁止 pageNum/pageSize」
# 从未真正生效，这正是 pageNum 族长期存活的根因（脚本自身诊断即如此措辞）。
# 只做**第三套命名**（pageIndex/perPage 等）与族内混搭的阻断，不动 22 个 pageNum 存量类
# ——存量收敛属 L3，规范 §5.2 明确「待架构师裁定，AI 不得自行执行」。
if ! python3 scripts/check-paging-param-names.py; then
  echo -e "${RED}❌ 分页入参出现白名单外的第三套命名或族内混搭（Bone-API-规范 §5.1）${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[9b/29] 分页入参单一真源（分页字段必须继承 bone-core 分页真源）...${RESET}"
# 分页是传输关注点，不该每层重声明。实测曾有 30 个入参类内联 page/size，其中 OrderPageRequest 带
# @Max(100) 而 PageParam 无上限 ⇒ 两份定义需手工同步，上限约束必然漂移。
# 已把 @Max(100) 收口进 PageParam.MAX_PAGE_SIZE，此门禁防止裸 class 复活。
# 两范式各有真源：offset→PageParam/SortablePageParam，cursor→CursorPageParam（2026-10-07 落地）。
# ⚠️ 判据（2026-10-07 修正过一次）：**只有 cursor 字段才算游标范式**；仅 limit 的
# （LogExportQuery 导出行数上限、LoadTablesQuery 结果集截断）**不是分页范式**，不判违规。
if ! python3 scripts/check-paging-param-ssot.py; then
  echo -e "${RED}❌ 分页入参类自带 page/size 却未继承分页真源（分页真源漂移）${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[9c/29] 业务异常必须携带稳定业务码（errorCode 不得为 null）...${RESET}"
# BizException 的 9 个静态 of() 重载无一携带 errorCode（把 errorCode 硬置 null），连注释标为
# 「使用枚举类（推荐）」的 of(ErrorCode) 也一样⇒ ProblemDetail.errorCode 恒空 ⇒ 前端拿不到 i18n 键、
# 监控只能按中文文案聚合。9 个重载已标 @Deprecated；本门禁防止新代码再走无码路径。
# 逃生舱：domain/ 与 infrastructure/ 不阻断、只报存量（域层抛裸异常是正确分层；
# infrastructure 的第三方SDK 包装与「内部信号协议」式异常改了反而绕过既有翻译）。
if ! python3 scripts/check-error-code-landing.py; then
  echo -e "${RED}❌ application/ 或 adapter/web/ 内的业务异常缺少稳定业务码${RESET}"
  echo -e "${YELLOW}   改用本模块 {Module}Errors.of({Module}ErrorCodes.XXX, 上下文[, cause])${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[10/29] 前端 ID 字段类型（Long→String 契约）...${RESET}"
# 后端 MetadataAutoConfiguration.boneLongToStringCustomizer() 全局把 Long 序列化为
# JSON 字符串（雪花 ID 超 JS Number 上限 2^53，以 number 返回会被静默截断且无 JS 报错）。
# 前端把 ID 字段声明为 number 即type lie：===、Map key、Tree node.key 全不可预期。
# 2026-10-03 已把存量 330 处全部改为 string，本门禁防其重新引入。
if ! python3 scripts/frontend/check-frontend-id-types.py --check; then
  echo -e "${RED}❌ 前端 ID 字段声明为 number（后端下发的是字符串，雪花 ID 会被静默截断）${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[11/29] HC-006 绕过 SDK 的 JDBC/MyBatis 扫描...${RESET}"
if ! python3 scripts/check-sdk-persistence.py --check; then
  echo -e "${RED}❌ 新增文件直接使用 JDBC / MyBatis 会话（须走 bone-metadata-sdk，存量见 sdk-persistence-bypass-baseline.json）${RESET}"
  exit_code=1
fi

# 前端响应解包契约：createApiClient 的响应拦截器是 `(response) => response.data`，
# 业务代码拿到的已经是 ApiResponse 本体。照 AxiosResponse 的形状写（读 res.status、
# 对 .data 再取信封字段）会静默出错：删除失败被吞成成功、Blob 下载抛 TypeError。
# 详见 doc/architecture/bone-前端架构.md §6.1.1 响应解包契约。
if [ -d bone-frontend/apps ]; then
  echo -e "${YELLOW}[12/29] 前端响应解包契约校验...${RESET}"
  if ! python3 scripts/check-frontend-response-contract.py; then
    echo -e "${RED}❌ 前端响应解包契约违规（res.status 当 HTTP 状态用 / 对已解包 body 再取 .data）${RESET}"
    exit_code=1
  fi

  # 分页 total 归一：后端 PageResult.total 是 java.lang.Long，被骨核全局 Long→String
  # 序列化器输出为字符串（保护雪花 ID 精度）。直接参与算术会抛 TypeError
  # （'1055' / 10），表现为「删除末页最后一条后翻页回退」整页崩溃。
  # 详见 doc/architecture/Bone-API-规范.md §5.3。
  echo -e "${YELLOW}[13/29] 分页 total 归一校验...${RESET}"
  if ! python3 scripts/check-paging-total-normalize.py; then
    echo -e "${RED}❌ 分页 total未归一（运行期是字符串，算术前须 normalizeTotal）${RESET}"
    exit_code=1
  fi
  # 分页当前页字段：后端 PageResult.records 是权威键，list 是 getList() 这个
  # @Deprecated 兼容 getter 的产物。收敛分两步且顺序不可颠倒 ——
  # 先前端全改读 records（2026-10-03 已完成），才能给后端 getter 加 @JsonIgnore。
  # 本门禁是「前端不回退」的护栏：跳过前端迁移直接收敛后端，那批页面直接白屏。
  # 详见 doc/architecture/Bone-API-规范.md §3.3 / §5.3。
  echo -e "${YELLOW}[14/29] 分页当前页字段（禁读 list）...${RESET}"
  if ! python3 scripts/check-paging-current-field.py; then
    echo -e "${RED}❌ 前端读取了分页废弃字段 list（权威字段是 records）${RESET}"
    exit_code=1
  fi
else
  echo -e "${YELLOW}[13/29]+[14/29]+[15/29] 前端契约校验...${RESET} ${DIM}跳过（未找到 bone-frontend/apps）${RESET}"
fi

# OpenAPI 契约结构：8 份规范是对外 SDK 的唯一契约真源（ADR-0040）。
# 2026-10-03 实测两类既存缺陷 —— 3 份规范的 flow mapping 值未加引号导致
# **YAML 根本无法解析**（实测 redocly lint EXIT=1，SDK 生成器拿不到契约）；
# 5 份规范完全没有 securitySchemes，且 redocly 的 security-defined 被显式关掉。
# 本门禁离线可跑（标准库 + PyYAML）；npx redocly 需联网，仅作 CI 独立 job。
echo -e "${YELLOW}[15/29] OpenAPI 契约结构（可解析 / 认证声明 / \$ref）...${RESET}"
if ! python3 scripts/check-openapi-contract.py; then
  echo -e "${RED}❌ OpenAPI 契约违规（无法解析 / 缺 securitySchemes / 缺顶层 security / \$ref 断裂）${RESET}"
  exit_code=1
fi

# 软删声明：骨核判定聚合可否软删的依据是**实体内有无带 @Deleted 的字段**，
# 与 DDL 有无 deleted 列无关 —— 表有列、实体没声明，`deleteById` 照常执行
# `DELETE FROM`，行永久消失而调用方拿到 HTTP200。extends AggregateRoot /
# TenantAggregateRoot 的实体一律中招（基类不提供该字段）。
# 本门禁 2026-10-03 建立时**从未接入任何流水线**，等于没有门禁：
# 存量缺口一旦被新增表复制扩大，无人阻断。故此处补齐 pre-commit 侧。
# 基线语义只可收缩（doc/architecture/soft-delete-declaration-baseline.json）。
echo -e "${YELLOW}[16/29] 软删声明（@Deleted / @PhysicalDelete 显式化）...${RESET}"
if ! python3 scripts/check-soft-delete-declaration.py; then
  echo -e "${RED}❌ 删除语义未显式声明（新增缺口将导致 deleteById 物理删行且调用方无感）${RESET}"
  exit_code=1
fi

# 配置键契约：`@Value("${some.key:default}")` 的键名写错时 Spring **静默回落**到默认值
# —— 不报错不打日志，症状出现在离故障点很远的地方（曾表现为全链路 401 而日志只有
# 「验签失败」）。单测与 ArchUnit 都抓不到。
# 用 --strict 把 WARN 也计为失败：WARN 的定义是「键不在任何 yml 里定义、但给了默认值」，
# 这在实践中几乎都是笔误（如 ${generator.encryption.key}）。存量若确有合法例外，
# 应在yml 中显式定义该键，而不是靠默认值蒙混。
echo -e "${YELLOW}[17/29] 配置键契约（@Value 键名须在 yml 有定义）...${RESET}"
if ! python3 scripts/ci/check-config-key-contract.py --strict; then
  echo -e "${RED}❌ 配置键名漂移（@Value 的键未在任何 yml 定义，Spring 会静默回落默认值）${RESET}"
  exit_code=1
fi

# 文档引用的类名漂移：doc/ 里写的 Java 类名若在仓库中不存在，读者按图索骥必然落空。
# 判据是「后缀白名单 + 反引号包裹」，故只抓 Controller / Repository / Handler /
# ApplicationService / Gateway / Adapter 这类构件名，不会误伤普通英文词。
# 基线 doc/architecture/doc-code-symbols-baseline.json 只可收缩；登记时必须写明
# reason（历史快照 / 目标态设计 / 流程示意 / Spring 通用词误报），不能无脑豁免。
echo -e "${YELLOW}[18/29] 文档类名漂移（doc/ 引用的类须真实存在）...${RESET}"
if ! python3 scripts/check-doc-code-symbols.py; then
  echo -e "${RED}❌ 文档引用了不存在的 Java 类（读者按图索骥必然落空，应修文档或登记基线并说明理由）${RESET}"
  exit_code=1
fi

# OpenAPI int64 ↔ 后端 Long→String 契约（ADR-0040 A2，本次落地）。
# 判据不是"见 int64 就改"：全局 ToStringSerializer 只对**包装类型 Long** 生效，
# 原生 long/double 仍是 JSON number。核对 Java 侧后确认：
#   · 60 处 ID / 外键 / PageResult.total（包装 Long）⇒ 已改为 type: string；
#   · console 的 11 处指标（KeyMetrics/ResourceUsage/ServiceStatus 全是原生 long）⇒ 保持 int64。
# 白名单在脚本的 PRIMITIVE_LONG_FIELDS，每条附Java 类型依据，只可收缩。
echo -e "${YELLOW}[19/29] OpenAPI int64 ↔ Long→String 契约...${RESET}"
if ! python3 scripts/check-openapi-int64-contract.py; then
  echo -e "${RED}❌ OpenAPI 声明 integer/int64 但后端下发字符串（包装 Long 被全局序列化器转换）${RESET}"
  exit_code=1
fi

# 控制器写端点必须声明 @PreAuthorize。各模块 SecurityConfig 统一是
# anyRequest().authenticated() —— 认证强制、授权不强制，缺方法级授权
# 意味着「任何登录用户」都能写。
# 存量缺口已归零（doc/architecture/controller-authorization-baseline.json 的 entries 为 0 条），
# 另有类级豁免 5 个 Controller + 方法级豁免 5 条，每条都写明理由；门禁只拦新增与基线外存量。
# 2026-10-05 修正：本注释此前写「存量 68 条」，与基线实际条数矛盾 —— 注释里的数字会变成
# 手工维护的第四份真源，正是数字漂移的源头，故改为只陈述可核对的事实。
# 运行期那一半（注解是否真在 HTTP 层生效）由 bone-iam 的 IamWriteEndpointAuthorizationTest 兜住。
echo -e "${YELLOW}[20/29] 控制器写端点细粒度授权...${RESET}"
if ! python3 scripts/check-controller-authorization.py; then
  echo -e "${RED}❌ 控制器写端点缺 @PreAuthorize（任何登录用户均可写，存量见 controller-authorization-baseline.json）${RESET}"
  exit_code=1
fi

# URL 层 permitAll 不得覆盖含写端点的业务路径前缀。两层授权各管一件事：
# URL 层（AuthorizationFilter）决定"要不要认证"，方法层 @PreAuthorize（MethodInterceptor）
# 决定"这次调用准不准"。@PreAuthorize 漏写在普通路径上只是"任何登录用户可写"，
# 而被permitAll 覆盖的路径漏注解是**"任何人可写"** —— 爆炸半径差一个量级。
# 本条 2026-10-04 建成但**长期未接入任何流水线**（= 假门禁），
# 2026-10-06 孤儿审计（对每个 check-*.py 查 hooks 引用数）发现并补上。
echo -e "${YELLOW}[21/29] URL 层 permitAll 覆盖写端点前缀...${RESET}"
if ! python3 scripts/check-controller-authorization-url-bypass.py; then
  echo -e "${RED}❌ URL 层 permitAll 覆盖了含写端点的业务路径前缀（该前缀漏 @PreAuthorize 即"任何人可写"）${RESET}"
  exit_code=1
fi

# OpenAPI 规范的「生成器前置条件」（ADR-0040 第 11 项的配套门禁）。
# redocly lint 与 check-openapi-contract.py 只管 YAML 可解析 / 认证声明 / $ref 可解析，
# 下面两类缺陷在它们下面全绿，却会让 openapi-generator 直接拒绝或产出不可用的 SDK：
#   ① path 模板变量未声明为 path 参数 ⇒ 抛 SpecValidationException，整份规范生成失败；
#   ② 缺 operationId / tags ⇒ 所有操作塞进单个 DefaultApi，方法名退化为 path 拼接。
# 另核对 generate-sdk.sh 的 SPECS 清单与磁盘规范一一对应、包名片段等于推导值。
echo -e "${YELLOW}[22/29] OpenAPI 生成器前置条件...${RESET}"
if ! python3 scripts/check-openapi-generation-readiness.py; then
  echo -e "${RED}❌ 规范不满足 SDK 生成前置条件（缺 operationId/tags/path 参数声明，或 generate-sdk.sh 清单与规范不一致）${RESET}"
  exit_code=1
fi

# 控制面匿名放行开关必须默认拒绝。`permit-unauthenticated` 打开等于整个
# Studio 控制面匿名可写（插件上传 / 部署 / 版本切换全部免凭证），与权限码缺失同级。
# 原基配置硬编码 `true`（连环境变量都覆盖不了），且 dev/in-memory 靠继承拿到放行
# ⇒ "忘记配置"＝一次静默的全站匿名降级。三条判据：R1 放行不得裸字面量、
# R2 基配置占位符默认值须 false、R3 生产 profile 恒为字面量 false。
# 负向探针 scripts/ci/probe-security-fail-open-default.py 用 HEAD 真实历史样本
# 逐条证伪 R1/R2/R3，并验证合法形态不被误报（6 条探针，改门禁后必须跑）。
echo -e "${YELLOW}[23/29] 控制面匿名放行默认拒绝...${RESET}"
if ! python3 scripts/check-security-fail-open-default.py --strict; then
  echo -e "${RED}❌ 控制面匿名放行开关非失败关闭（放行须由可审计的环境变量通道显式达成，不得靠默认值）${RESET}"
  exit_code=1
fi

# COMMON_* 公共错误码重复定义：跨模块共用的语义必须集中在 bone-core 的 CommonErrorCodes，
# 否则「修复真源时漏改副本」会让同一语义对外出现两个码。存量 6 组已登记为只可收缩的基线，
# 本门禁拦的是新增重复（判据与 check-i18n-sync 的 CODE_RE 一致：只认字面量声明）。
echo -e "${YELLOW}[24/29] COMMON_* 错误码重复定义...${RESET}"
if ! python3 scripts/check-error-code-duplication.py --check; then
  echo -e "${RED}❌ COMMON_* 码值在多个模块各自字面量重定义（真源唯一，见 bone-core CommonErrorCodes）${RESET}"
  exit_code=1
fi

# ArchUnit 冻结基线开关必须落盘且入版本控制。冻结开关（allowStoreUpdate）
# 默认 true ⇒ 架构违规在**首次运行时被自动写进基线、此后永久豁免**；配上
# .gitignore 的 *.properties 把 archunit.properties 全吞，就得到"看起来冻结、
# 实际每次都放行"的假冻结（2026-10-04 实测：20 个含 ArchitectureTest 的模块
# 只有 masterdata 一份被跟踪）。本门禁同时查「存在 + 内容正确 + 已被 git 跟踪」。
echo -e "${YELLOW}[25/29] ArchUnit 冻结基线开关...${RESET}"
if ! python3 scripts/check-archunit-freeze-baseline.py --check; then
  echo -e "${RED}❌ 冻结基线开关未生效（缺失 / 未关闭 allowStoreUpdate / 文件被 gitignore 吞掉 ⇒ 架构违规首次运行即被写进基线并永久豁免）${RESET}"
  exit_code=1
fi

# Spring Security 的 /error 白名单。Spring Boot 在 Controller 抛异常后会把请求
# FORWARD 到 /error；若它不在 permitAll 里，这条 FORWARD 会被 AuthorizationFilter
# 拦下 ⇒ AuthorizationDeniedException ⇒ 对外表现为 **401「未认证」**，
# 把真实的 404/403/500 统统掩盖。2026-10-06 在 bone-file 上实测确诊：
# download 一个不存在的对象返回 401，排查时被误导到「密钥不一致 / 权限码缺失」，
# 最终靠 TRACE 日志（already authenticated 紧跟 Securing GET /error）才定位真因。
# 其余模块当时未暴露，只因有 @RestControllerAdvice 兜底 —— 任何新增的直写响应体
# 端点都会立刻暴露它，故对全仓一律要求。
echo -e "${YELLOW}[26/29] SecurityConfig /error 白名单...${RESET}"
if ! python3 scripts/ci/check-error-endpoint-permit-all.py; then
  echo -e "${RED}❌ SecurityConfig 未放行 /error（业务异常会被误报成 401，真实错误码全被掩盖）${RESET}"
  exit_code=1
fi

# AFTER_COMMIT 处理器必须显式切租户（ADR-0031 D3）。
# 缺租户时 SDK 写路径失败关闭，跨聚合联动会**静默失效**（编译通过、单测全绿、
# 功能从未生效）。2026-10-06 已在 ShipmentOrderSyncEventHandler 上用负向探针实测复现。
echo -e "${YELLOW}[27/29] AFTER_COMMIT 处理器显式声明租户...${RESET}"
if ! python3 scripts/ci/check-async-tenant-context.py; then
  echo -e "${RED}❌ AFTER_COMMIT 处理器未显式声明租户（跨聚合联动会静默失效）${RESET}"
  exit_code=1
fi

# HC-003：Controller 端点必须返回统一响应信封。
# 该规则此前是Planned（gate-state.json 自述"controllerMustReturnApiResponse 不在共享规则库"），
# 2026-10-06 补齐实现。判定含三类必须排除的假阳性：嵌套 record（DTO 定义）、
# 无映射注解的 public 辅助方法、以及"名为 Controller 但零 Spring 注解"的纯 POJO
# （实测 MetadataVersionController即属此类，其 public 方法不是 HTTP 端点）。
echo -e "${YELLOW}[28/29] Controller 统一响应信封 (HC-003)...${RESET}"
if ! python3 scripts/check-controller-response-envelope.py; then
  echo -e "${RED}❌ Controller 端点未返回 ApiResponse/PageResult（HC-003）${RESET}"
  exit_code=1
fi

# ── 自动配置注册一致性（Boot 3唯一机制）──────────────────────────────
# 2026-10-07 新增。实测两个真实案例：
#· starter 把自动配置只写在旧式 spring.factories ⇒ 整个 starter 长期静默失效；
#   · SDK 旧式文件列的类与新式完全不同，其中一个源码里已不存在。
# 这类漂移编译与单测都发现不了，必须机器化拦截。
echo -e "${YELLOW}[29/29] 自动配置注册（Boot 3 .imports 机制）...${RESET}"
if ! python3 scripts/check-autoconfig-registration.py; then
  echo -e "${RED}❌ 自动配置注册不一致（死条目 / 旧式注册键 / 末尾缺换行）${RESET}"
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
