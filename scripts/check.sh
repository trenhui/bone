#!/usr/bin/env bash
set -euo pipefail

# DIM 此前**未定义**，而 [13/42] 的跳过分支引用了 ${DIM}；本脚本 `set -u` ⇒ 一旦走到
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

# 为变更模块解析「最近聚合器」目录：从模块目录逐级上跳，找第一个在 <modules> 里列出该模块的 pom。
#
# 背景（2026-10-24 实证，本类问题已复发三次）：[3/42]/[4/42] 原实现是 `mvn -f <模块>/pom.xml test`
# 单模块构建——模块间依赖从 **~/.m2 已安装 jar** 解析。当提交改了 core 的公共接口、而本地仓的 core jar
# 还是旧的（没跑过 install），starter 对 core 的依赖命中旧 jar ⇒ 编译假错
# （"未覆盖抽象方法 saveTaskIfAbsent"），与本次提交内容无关，却拦下提交；更隐蔽的是它伪装成
# 「代码缺陷」，排查方向被带偏。mvn 的 `-f <模块>` 语义下 `-am` 加不进任何上游（reactor 只含该模块），
# 必须改用「聚合器 + -pl + -am」：同聚合器的上游模块从 reactor **现场编译**，不再受本地仓新鲜度影响。
#
# 找不到聚合器时（独立模块）退回原 `-f <模块>/pom.xml` 行为，保持兼容。
# bash 3.2 兼容：不用 mapfile / 关联数组，纯 while + dirname。
resolve_aggregator() {
  local mod="$1" dir base
  while :; do
    dir=$(dirname "$mod")
    if [ -f "$dir/pom.xml" ]; then
      base=$(basename "$mod")
      # <modules> 均为无条件书写（2026-10-24 全仓 8 处已核实无 profile 条件化）
      if grep -q "<module>$base</module>" "$dir/pom.xml" 2>/dev/null; then
        echo "$dir"
        return 0
      fi
    fi
    if [ "$dir" = "." ]; then
      return 1
    fi
    mod="$dir"
  done
}

echo -e "${YELLOW}[1/42] 代码风格校验 (Spotless)...${RESET}"
# 2026-10-17 解除"全树连坐"：spotless 原扫全仓，他人在途/未提交（甚至未跟踪）的脏模块
# 会让所有人的提交失败（本次巡检前已因此被卡两次）。改为只校验本次提交涉及的模块
# （与 [3/42] 就近 ArchUnit 同一套"变更模块"口径，逐模块 -f 跑，避开 reactor 路径不稳）；
# CI 仍跑全量兜底，故未放松格式约束，只是把"谁脏谁负责"落到责任人。
SPOTLESS_FILES=$(git diff --cached --name-only --diff-filter=ACD 2>/dev/null | grep -E '\.java$|\.properties$' || true)
# doc/ 下的独立示例工程不参与平台构建（自带 pom、自成一体），不套平台 Spotless 格式，
# 否则会把平台格式强加给示例、并把他人的示例代码改得面目全非。见 [11/42] 同款 doc/ 豁免口径。
# 同 [11/42]：只放行已知的具体示例路径 doc/ai-product/synapClaim/reference-impl/（默认拒绝原则），
# 未来 doc/ 下新增任何绕过 SDK 的平台式代码仍会被门禁拦下。
SPOTLESS_MODULES=$(echo "$SPOTLESS_FILES" | grep -E '/src/' | grep -v '^doc/ai-product/synapClaim/reference-impl/' | sed -E 's|/src/.*||' | sort -u || true)
if [ -n "$SPOTLESS_MODULES" ]; then
  while IFS= read -r MODULE_PATH; do
    [ -z "$MODULE_PATH" ] && continue
    POM="$MODULE_PATH/pom.xml"
    [ -f "$POM" ] || continue
    echo "  Spotless: $MODULE_PATH"
    mvn -f "$POM" spotless:check --batch-mode -q || exit_code=$?
  done <<< "$SPOTLESS_MODULES"
else
  echo "  无已 stage 的 .java/.properties 变更，[1/42] Spotless 跳过（非违规）；CI 全量兜底。"
fi

echo -e "${YELLOW}[2/42] 检测变更模块...${RESET}"
# 2026-10-04 修复 P1-6：原判据 `--diff-filter=ACM` + `grep '\.java$'` 有两个静默盲区：
#   ① **删除**的 .java（D 被过滤）→ 删除一个 Controller / 聚合根后，依赖它的架构规则
#      实际已失效，却因为"没有变更模块"而完全不跑 ArchUnit；
#   ② 只改 pom / 前端 / 文档的提交 → CHANGED_FILES 为空 ⇒ [3/42][4/42] 整段不执行，
#      而 `set -e` 下的 `|| true` 会吞掉一切，**连一行说明都没有**。
# 故纳入 D，并在下面 MODULE_PATHS 为空时**显式打印未执行的原因**（静默跳过比跑错更危险）。
CHANGED_FILES=$(git diff --cached --name-only --diff-filter=ACD 2>/dev/null | grep -E '\.java$|\.properties$' || true)
if [ -n "$CHANGED_FILES" ]; then
  # 注意：`|| true` 必须放在整条管道末尾。管道优先级高于 `||`，若写成 `grep ... || true | sed ...`
  # 会被解析成 `(grep) || (true | sed)`，sed 拿不到输入，MODULE_PATHS 变成文件全路径，
  # 导致下面 $MODULE_PATH/pom.xml 恒不存在、ArchUnit 门禁被静默跳过。
  # 同 [1/42]/[11/42]：只放行已知的具体示例路径（默认拒绝原则），doc/ 下其他路径仍受平台架构规则约束。
  MODULE_PATHS=$(echo "$CHANGED_FILES" | grep 'src/main/java' | grep -v '^doc/ai-product/synapClaim/reference-impl/' | sed 's|/src/main/java/.*||' | sort -u || true)

  if [ -n "$MODULE_PATHS" ]; then
    echo "  变更模块:"
    echo "$MODULE_PATHS" | sed 's/^/    - /'
    echo -e "${YELLOW}[3/42] 就近 ArchUnit 架构检查...${RESET}"
    # 逐模块运行：优先「最近聚合器 + -pl + -am」（同聚合器上游模块现场编译，不解析 ~/.m2 旧 jar，
    # 2026-10-24 stale-jar 编译假错实证）；找不到聚合器时退回 -f 单模块（独立模块兼容）。
    while IFS= read -r MODULE_PATH; do
      [ -z "$MODULE_PATH" ] && continue
      POM="$MODULE_PATH/pom.xml"
      [ -f "$POM" ] || continue
      echo "  ArchUnit: $MODULE_PATH"
      AGG=$(resolve_aggregator "$MODULE_PATH" || true)
      if [ -n "$AGG" ]; then
        if [ "$AGG" = "." ]; then REL="$MODULE_PATH"; else REL="${MODULE_PATH#$AGG/}"; fi
        mvn -f "$AGG/pom.xml" -pl "$REL" -am test -Dtest='*ArchitectureTest' -Dsurefire.failIfNoSpecifiedTests=false --batch-mode -q || exit_code=$?
      else
        mvn -f "$POM" test -Dtest='*ArchitectureTest' -Dsurefire.failIfNoSpecifiedTests=false --batch-mode -q || exit_code=$?
      fi
    done <<< "$MODULE_PATHS"
  else
    # 消除了原本的静默：只改 pom / 前端 / 文档（无 src/main/java）时，
    # [3/42] 不跑是**正确**的，但必须说清为什么，否则读者会以为门禁漏了。
    echo "  无 src/main/java 变更（仅 ${CHANGED_FILES%%$'\n'*} 等），[3/42] 就近 ArchUnit 跳过；"
    echo "  若本次引入了新依赖/新架构层，请确认 [4/42] 或全量 ArchUnit 已覆盖。"
  fi
else
  echo "  无已 stage 的 .java/.properties 变更，[3/42][4/42] ArchUnit 未执行（非违规）。"
fi

# P1-16：（2026-09-24 新增）共享门禁库变更 → 全量 ArchUnit 回归
# 改动 bone-architecture-test 的共享规则会同时影响所有模块的 ArchitectureTest，
# 仅跑变更模块无法发现「别处模块回归」，故扫描全部含 ArchitectureTest 的模块逐個 -f 运行
# （沿用 [2/42] 的 -f 逐模块机制，避开 reactor 路径解析不稳与前端 node 模块）。
if echo "$CHANGED_FILES" | grep -q 'bone-framework/bone-architecture-test/'; then
  echo -e "${YELLOW}[4/42] 共享门禁库变更 → 全量 ArchUnit 回归...${RESET}"
  # 用 while-read 收集，避免 mapfile（bash ≥4.0 才有；macOS 默认 /bin/bash 3.2 无该内建）
  ARCH_MODULES=()
  # bash 3.2 兼容：跨行 `<` + `<(...)` 组合会解析失败，保持单行进程替换
  while IFS= read -r line; do
    [ -n "$line" ] && ARCH_MODULES+=("$line")
  done < <(find . -path '*/src/test/java/*/ArchitectureTest.java' -not -path '*/node_modules/*' -not -path '*/target/*' 2>/dev/null | sed 's|/src/test/java/.*||' | sort -u)
  for MODULE_PATH in "${ARCH_MODULES[@]:-}"; do
    [ -z "$MODULE_PATH" ] && continue
    POM="$MODULE_PATH/pom.xml"
    [ -f "$POM" ] || continue
    echo "  ArchUnit: $MODULE_PATH"
    # 同 [3/42]：聚合器 + -pl + -am，避免 ~/.m2 旧 jar 造成跨模块编译假错
    AGG=$(resolve_aggregator "$MODULE_PATH" || true)
    if [ -n "$AGG" ]; then
      if [ "$AGG" = "." ]; then REL="$MODULE_PATH"; else REL="${MODULE_PATH#$AGG/}"; fi
      mvn -o -f "$AGG/pom.xml" -pl "$REL" -am test -Dtest='*ArchitectureTest' -Dsurefire.failIfNoSpecifiedTests=false --batch-mode -q || exit_code=$?
    else
      mvn -o -f "$POM" test -Dtest='*ArchitectureTest' -Dsurefire.failIfNoSpecifiedTests=false --batch-mode -q || exit_code=$?
    fi
  done
fi

echo -e "${YELLOW}[5/42] ORM 框架拦截 (HC-001)...${RESET}"
# 2026-10-06：改为独立脚本，两处原因——
#  ① 【覆盖度】原判据只列 4 个 artifactId（mybatis / mybatis-plus /
#     spring-boot-starter-data-jpa / hibernate-core），漏掉 mybatis-spring-boot-starter、
#     tk.mybatis:mapper、ibatis-*-starter 等常见写法；负向探针实测该漏网坐标能溜过。
#  ② 【扫描盲区】原用 `git grep --untracked`，但本仓 .gitignore 含 `!**/src/main/**`
#     **反向忽略**规则 ⇒ 未跟踪的 src/main 文件被整片跳过。实测：新建一个含
#     `import org.apache.ibatis` 的未跟踪文件放在 src/main 下，git grep 退出码=1（零命中）。
#脚本改用文件系统遍历兜住，并把 [7/42] 的 pom 扫描并入同一脚本，保证两个面口径一致。
if ! python3 scripts/check-orm-banned.py; then
  exit_code=1
fi

echo -e "${YELLOW}[6/42] 密钥泄露扫描 (Gitleaks)...${RESET}"
if command -v gitleaks &>/dev/null; then
  gitleaks protect --staged --config .gitleaks.toml --verbose || exit_code=$?
else
  echo "  gitleaks 未安装，本地跳过。注意：CI 侧（.github/workflows/ci.yml 的 backend-security"
  echo "  job）已接入 gitleaks 并阻断，本地跳过**不代表**提交可绕过密钥扫描——"
  echo "  真正生效的是 CI 那一道（HC-004 实测状态为 Active，见 Bone-DDD-最终实践方案 G-1.7）。"
  echo "  本机可执行 brew install gitleaks 以在提交前自查。"
fi

# [7/42] pom 依赖检查已并入 [5/42] 的 scripts/check-orm-banned.py（R2 pom 面）。
# 原实现另有性能问题：未排除 node_modules（bone-frontend/node_modules 单目录 643MB /
# 十万级文件），实测 14 分钟以上跑不完，是 pre-commit 最大耗时项。新脚本按源码根目录
# 定向遍历（SOURCE_ROOTS），不经过 node_modules。
echo -e "${YELLOW}[7/42] pom.xml 依赖检查（已并入 [5/42] ORM 门禁的 R2 面）...${RESET}"

echo -e "${YELLOW}[8/42] i18n 同步校验（errorCode ↔ 台账 ↔ 语言包）...${RESET}"
# 与既有 python3 门禁同形态；存量漂移走显式白名单 config/i18n/errorcode-baseline.json，
# 新增码一律不豁免。详见 doc/design/modules/国际化设计方案.md §8.2。
if ! python3 scripts/check-i18n-sync.py; then
  echo -e "${RED}❌ i18n 校验失败（缺译 / 新码未登记 / 两份语言包不对称）${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[9/42] 分页入参命名族（Bone-API-规范 §5.1：新增端点只允许 page/size）...${RESET}"
# 该门禁此前完全游离于所有门禁之外 ⇒ 规范 §5.1「存量收敛完成即禁止 pageNum/pageSize」
# 从未真正生效，这正是 pageNum 族长期存活的根因（脚本自身诊断即如此措辞）。
# 只做**第三套命名**（pageIndex/perPage 等）与族内混搭的阻断，不动 22 个 pageNum 存量类
# ——存量收敛属 L3，规范 §5.2 明确「待架构师裁定，AI 不得自行执行」。
if ! python3 scripts/check-paging-param-names.py; then
  echo -e "${RED}❌ 分页入参出现白名单外的第三套命名或族内混搭（Bone-API-规范 §5.1）${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[9b/42] 分页入参单一真源（分页字段必须继承 bone-core 分页真源）...${RESET}"
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

echo -e "${YELLOW}[9c/42] 业务异常必须携带稳定业务码（errorCode 不得为 null）...${RESET}"
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

echo -e "${YELLOW}[9d/42] 跨服务异常落点一致性（同一异常 ⇒ 同一 status+errorCode）...${RESET}"
# 错误码是跨系统契约（登记 §2「可聚合」「可i18n」）。同一个异常在不同服务返回不同
# errorCode ⇒ 前端 errors['COMMON_VALIDATION_FAILED'] 在部分服务取不到翻译、监控会裂成两个
# 聚合口径。2026-10-07 实测：DomainException 在 bone-web 是 400+COMMON_CONFLICT，而
# masterdata/system/iam/generator 四个服务是 400+COMMON_VALIDATION_FAILED；且
# COMMON_CONFLICT 在台账里定义的是 409，与 400 属语义错配。
# 存量 7 类已冻结（逐条消债），本门禁只阻断**新增**不一致。
if ! python3 scripts/check-cross-service-exception-mapping.py; then
  echo -e "${RED}❌ 同一异常类型在不同服务映射到不同 status+errorCode（契约已裂，前端 i18n 与监控聚合都会失真）${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[9e/42] 主键注解落位（@Id 必须标在 id 字段上）...${RESET}"
# @Id 落到哪个字段，直接决定 SDK 所有按主键 SQL 的 where 条件。
# 2026-10-07 实测：9 个实体（integration×4 / masterdata×3 / system×2）的 @Id 被一段
# javadoc 顶到了 `deleted` 字段上 ⇒ findById 恒 404、update/软删静默失效，
# 而列表接口照常返回数据（findPage 不带主键谓词），症状极易误判成前端或权限问题。
# 这种错位 javac 与 spotless 都不报错，只能靠静态判据拦。
if ! python3 scripts/check-id-annotation.py; then
  echo -e "${RED}❌ @Id 未标注在 id 字段上（findById 恒 404、update/软删静默失效）${RESET}"
  echo -e "${YELLOW}   把 @Id 移到 @GeneratedValue 之上、紧贴 private Long id; 之前${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[10/42] 前端 ID 字段类型（Long→String 契约）...${RESET}"
# 后端 MetadataAutoConfiguration.boneLongToStringCustomizer() 全局把 Long 序列化为
# JSON 字符串（雪花 ID 超 JS Number 上限 2^53，以 number 返回会被静默截断且无 JS 报错）。
# 前端把 ID 字段声明为 number 即type lie：===、Map key、Tree node.key 全不可预期。
# 2026-10-03 已把存量 330 处全部改为 string，本门禁防其重新引入。
if ! python3 scripts/frontend/check-frontend-id-types.py --check; then
  echo -e "${RED}❌ 前端 ID 字段声明为 number（后端下发的是字符串，雪花 ID 会被静默截断）${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[11/42] HC-006 绕过 SDK 的 JDBC/MyBatis 扫描...${RESET}"
if ! python3 scripts/check-sdk-persistence.py --check; then
  echo -e "${RED}❌ 新增文件直接使用 JDBC / MyBatis 会话（须走 bone-metadata-sdk，存量见 sdk-persistence-bypass-baseline.json）${RESET}"
  exit_code=1
fi

# 前端响应解包契约：createApiClient 的响应拦截器是 `(response) => response.data`，
# 业务代码拿到的已经是 ApiResponse 本体。照 AxiosResponse 的形状写（读 res.status、
# 对 .data 再取信封字段）会静默出错：删除失败被吞成成功、Blob 下载抛 TypeError。
# 详见 doc/architecture/bone-前端架构.md §6.1.1 响应解包契约。
if [ -d bone-frontend/apps ]; then
  echo -e "${YELLOW}[12/42] 前端响应解包契约校验...${RESET}"
  if ! python3 scripts/check-frontend-response-contract.py; then
    echo -e "${RED}❌ 前端响应解包契约违规（res.status 当 HTTP 状态用 / 对已解包 body 再取 .data）${RESET}"
    exit_code=1
  fi

  # 分页 total 归一：后端 PageResult.total 是 java.lang.Long，被骨核全局 Long→String
  # 序列化器输出为字符串（保护雪花 ID 精度）。直接参与算术会抛 TypeError
  # （'1055' / 10），表现为「删除末页最后一条后翻页回退」整页崩溃。
  # 详见 doc/architecture/Bone-API-规范.md §5.3。
  echo -e "${YELLOW}[13/42] 分页 total 归一校验...${RESET}"
  if ! python3 scripts/check-paging-total-normalize.py; then
    echo -e "${RED}❌ 分页 total未归一（运行期是字符串，算术前须 normalizeTotal）${RESET}"
    exit_code=1
  fi
  # 分页当前页字段：后端 PageResult.records 是权威键，list 是 getList() 这个
  # @Deprecated 兼容 getter 的产物。收敛分两步且顺序不可颠倒 ——
  # 先前端全改读 records（2026-10-03 已完成），才能给后端 getter 加 @JsonIgnore。
  # 本门禁是「前端不回退」的护栏：跳过前端迁移直接收敛后端，那批页面直接白屏。
  # 详见 doc/architecture/Bone-API-规范.md §3.3 / §5.3。
  echo -e "${YELLOW}[14/42] 分页当前页字段（禁读 list）...${RESET}"
  if ! python3 scripts/check-paging-current-field.py; then
    echo -e "${RED}❌ 前端读取了分页废弃字段 list（权威字段是 records）${RESET}"
    exit_code=1
  fi
else
  echo -e "${YELLOW}[13/42]+[14/42]+[15/42] 前端契约校验...${RESET} ${DIM}跳过（未找到 bone-frontend/apps）${RESET}"
fi

# OpenAPI 契约结构：8 份规范是对外 SDK 的唯一契约真源（ADR-0040）。
# 2026-10-03 实测两类既存缺陷 —— 3 份规范的 flow mapping 值未加引号导致
# **YAML 根本无法解析**（实测 redocly lint EXIT=1，SDK 生成器拿不到契约）；
# 5 份规范完全没有 securitySchemes，且 redocly 的 security-defined 被显式关掉。
# 本门禁离线可跑（标准库 + PyYAML）；npx redocly 需联网，仅作 CI 独立 job。
echo -e "${YELLOW}[15/42] OpenAPI 契约结构（可解析 / 认证声明 / \$ref）...${RESET}"
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
echo -e "${YELLOW}[16/42] 软删声明（@Deleted / @PhysicalDelete 显式化）...${RESET}"
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
echo -e "${YELLOW}[17/42] 配置键契约（@Value 键名须在 yml 有定义）...${RESET}"
if ! python3 scripts/ci/check-config-key-contract.py --strict; then
  echo -e "${RED}❌ 配置键名漂移（@Value 的键未在任何 yml 定义，Spring 会静默回落默认值）${RESET}"
  exit_code=1
fi

# 文档引用的类名漂移：doc/ 里写的 Java 类名若在仓库中不存在，读者按图索骥必然落空。
# 判据是「后缀白名单 + 反引号包裹」，故只抓 Controller / Repository / Handler /
# ApplicationService / Gateway / Adapter 这类构件名，不会误伤普通英文词。
# 基线 doc/architecture/doc-code-symbols-baseline.json 只可收缩；登记时必须写明
# reason（历史快照 / 目标态设计 / 流程示意 / Spring 通用词误报），不能无脑豁免。
echo -e "${YELLOW}[18/42] 文档类名漂移（doc/ 引用的类须真实存在）...${RESET}"
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
echo -e "${YELLOW}[19/42] OpenAPI int64 ↔ Long→String 契约...${RESET}"
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
echo -e "${YELLOW}[20/42] 控制器写端点细粒度授权...${RESET}"
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
echo -e "${YELLOW}[21/42] URL 层 permitAll 覆盖写端点前缀...${RESET}"
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
echo -e "${YELLOW}[22/42] OpenAPI 生成器前置条件...${RESET}"
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
echo -e "${YELLOW}[23/42] 控制面匿名放行默认拒绝...${RESET}"
if ! python3 scripts/check-security-fail-open-default.py --strict; then
  echo -e "${RED}❌ 控制面匿名放行开关非失败关闭（放行须由可审计的环境变量通道显式达成，不得靠默认值）${RESET}"
  exit_code=1
fi

# COMMON_* 公共错误码重复定义：跨模块共用的语义必须集中在 bone-core 的 CommonErrorCodes，
# 否则「修复真源时漏改副本」会让同一语义对外出现两个码。存量 6 组已登记为只可收缩的基线，
# 本门禁拦的是新增重复（判据与 check-i18n-sync 的 CODE_RE 一致：只认字面量声明）。
echo -e "${YELLOW}[24/42] COMMON_* 错误码重复定义...${RESET}"
if ! python3 scripts/check-error-code-duplication.py --check; then
  echo -e "${RED}❌ COMMON_* 码值在多个模块各自字面量重定义（真源唯一，见 bone-core CommonErrorCodes）${RESET}"
  exit_code=1
fi

# ArchUnit 冻结基线开关必须落盘且入版本控制。冻结开关（allowStoreUpdate）
# 默认 true ⇒ 架构违规在**首次运行时被自动写进基线、此后永久豁免**；配上
# .gitignore 的 *.properties 把 archunit.properties 全吞，就得到"看起来冻结、
# 实际每次都放行"的假冻结（2026-10-04 实测：20 个含 ArchitectureTest 的模块
# 只有 masterdata 一份被跟踪）。本门禁同时查「存在 + 内容正确 + 已被 git 跟踪」。
echo -e "${YELLOW}[25/42] ArchUnit 冻结基线开关...${RESET}"
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
echo -e "${YELLOW}[26/42] SecurityConfig /error 白名单...${RESET}"
if ! python3 scripts/ci/check-error-endpoint-permit-all.py; then
  echo -e "${RED}❌ SecurityConfig 未放行 /error（业务异常会被误报成 401，真实错误码全被掩盖）${RESET}"
  exit_code=1
fi

# AFTER_COMMIT 处理器必须显式切租户（ADR-0031 D3）。
# 缺租户时 SDK 写路径失败关闭，跨聚合联动会**静默失效**（编译通过、单测全绿、
# 功能从未生效）。2026-10-06 已在 ShipmentOrderSyncEventHandler 上用负向探针实测复现。
echo -e "${YELLOW}[27/42] AFTER_COMMIT 处理器显式声明租户...${RESET}"
if ! python3 scripts/ci/check-async-tenant-context.py; then
  echo -e "${RED}❌ AFTER_COMMIT 处理器未显式声明租户（跨聚合联动会静默失效）${RESET}"
  exit_code=1
fi

# HC-003：Controller 端点必须返回统一响应信封。
# 该规则此前是Planned（gate-state.json 自述"controllerMustReturnApiResponse 不在共享规则库"），
# 2026-10-06 补齐实现。判定含三类必须排除的假阳性：嵌套 record（DTO 定义）、
# 无映射注解的 public 辅助方法、以及"名为 Controller 但零 Spring 注解"的纯 POJO
# （实测 MetadataVersionController即属此类，其 public 方法不是 HTTP 端点）。
echo -e "${YELLOW}[28/42] Controller 统一响应信封 (HC-003)...${RESET}"
if ! python3 scripts/check-controller-response-envelope.py; then
  echo -e "${RED}❌ Controller 端点未返回 ApiResponse/PageResult（HC-003）${RESET}"
  exit_code=1
fi

# ── 自动配置注册一致性（Boot 3唯一机制）──────────────────────────────
# 2026-10-07 新增。实测两个真实案例：
#· starter 把自动配置只写在旧式 spring.factories ⇒ 整个 starter 长期静默失效；
#   · SDK 旧式文件列的类与新式完全不同，其中一个源码里已不存在。
# 这类漂移编译与单测都发现不了，必须机器化拦截。
echo -e "${YELLOW}[29/42] 自动配置注册（Boot 3 .imports 机制）...${RESET}"
if ! python3 scripts/check-autoconfig-registration.py; then
  echo -e "${RED}❌ 自动配置注册不一致（死条目 / 旧式注册键 / 末尾缺换行）${RESET}"
  exit_code=1
fi

# ── 确定性缓存哨兵（锁住 95e4779a 的语义决策）────────────────────────
# 2026-10-08 新增。背景：95e4779a 摘除的概率性结果缓存被 78515a39 整批静默回退——
# 该修复当时没有任何机器化载体，只存在于人脑与提交历史，换一个工作会话就被旧工作区覆盖。
# 四类判据（R1a hashCode 结果缓存 / R1b Spring Cache 通道 / R2 RuleEngine 求值入口 /
# R3 ValidationEngine @Cacheable / R4 MetadataEngine 死配置）均经注入-恢复负向探针实测。
echo -e "${YELLOW}[30/42] 确定性缓存哨兵（禁止概率性结果缓存回归）...${RESET}"
if ! python3 scripts/check-deterministic-cache.py; then
  echo -e "${RED}❌ 概率性结果缓存回归（hashCode 键 / Spring Cache 通道 / 错位求值入口）${RESET}"
  exit_code=1
fi

# ── 前端元数据工作区融合方案门禁（PRD v0.33 §6 · G31/G32/G33）────────
# 2026-10-09 新增。此前没有任何一步校验「菜单 ↔ 路由」「数据 Tab 路由 :id 绑定」
# 「列投影截断」，导致「关系管理点了跳模型管理」「/data 静默看错实体」「第 9 列起消失」
# 这类跨文件漂移长期无人拦截。三条均为静态分析，零依赖、秒级。
echo -e "${YELLOW}[31/42] 前端路由 :id 绑定（数据 Tab 防静默串实体 · G31）...${RESET}"
if ! python3 scripts/check-frontend-route-param.py; then
  echo -e "${RED}❌ RuntimeDataManagement 未绑定路由 :id（静默看错实体数据回归）${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[32/42] 菜单↔路由可达性 + 图标白名单（G32/G37）...${RESET}"
if ! python3 scripts/check-menu-route-consistency.py; then
  echo -e "${RED}❌ 菜单 seed 存在无对应真实路由的死链项${RESET}"
  exit_code=1
fi
# 2026-10-30 新增（G37）。G32 只管「点进去有没有页面」，管不到「图标画不画得出来」：
# shell 的 MENU_ICON_MAP 是白名单映射（动态取会把整个图标包打进产物），
# 数据里写了未登记的图标名不报错、不告警，只被 resolveMenuIcon 静默降级成默认图标。
# 实证：0078 初审工作台初版写 SolutionOutlined，不在 38 项白名单内。
if ! python3 scripts/check-menu-icon-whitelist.py; then
  echo -e "${RED}❌ 菜单种子使用了 MENU_ICON_MAP 未登记的图标（会被静默降级为默认图标）${RESET}"
  exit_code=1
fi

echo -e "${YELLOW}[33/42] 列投影截断禁令 + deliveryMode 默认值唯一性（G33/G34）...${RESET}"
if ! python3 scripts/check-slice-truncation.py; then
  echo -e "${RED}❌ 字段/列投影代码含 .slice(0,N) 截断，或 deliveryMode 默认值不一致${RESET}"
  exit_code=1
fi

# 2026-10-10 新增（G35）。此前 G32 只校验「菜单 seed 的 hash 段在对应微应用里有真实路由」，
# 覆盖不到**跨微应用层级的入口重复**：2026-10-09 用户实测发现顶级「元数据建模」`/metadata`
# 与「业务建模 → 建模工作台」`/metadata#/apps` 点进去是同一个页面（容器根路径 ⇒ 落
# `<Route path="/">` ⇒ Navigate 到 /apps），用户看到两扇门却不知道该走哪。
# G35 按 SQL 语句判**净效果**（`SET deleted=1` 的软删语句是修复手段，不算违规），
# 规则1 = 禁裸容器根路径菜单，规则2 = 菜单落点必须在 shell 合法落点集合内（防「点了跳首页」）。
# 变异验证已做：植入 `/metadata` ⇒ 红；植入 `/dashboard` ⇒ 红；撤掉 ⇒ 绿。
echo -e "${YELLOW}[34/42] 菜单入口唯一性（同一扇门只留一个入口 · G35）...${RESET}"
if ! python3 scripts/check-menu-single-entry.py; then
  echo -e "${RED}❌ 存在重复入口（裸容器根路径）或死链菜单（落点非法，点击静默回首页）${RESET}"
  exit_code=1
fi

# 2026-10-12 新增（G36 · 孤儿运行时物理表）。元数据平台的核心承诺是「定义即真相」：
# 发布即建表（RUNTIME align）、删除即软删定义。此前删除实体只软删 meta_entity 与动作定义，
# **不 DROP 物理表** ⇒ 库里积压 36 张无主表（多轮 E2E 实测），承诺失物证。
# 判据（为什么不查「无在用实体引用」）：那会把 iam_*/gen_*/bp_* 等其它模块自建表全部误报
# （实测 160+ 假阳性）；本门禁只查「物理表 ∩ 已软删实体 table_name − 在用实体 table_name」，
# 即表的唯一建模来源已软删且无同名实体顶替。零跨模块误报、36/36 实测命中。
# 只报告不删除：DROP 是 L3，留给 DBA 显式执行（报告附清理命令）。
# 环境降级：无 pymysql 且无 mysql CLI、或连不上库时显式 SKIP 退出 0（门禁守护「有库时不许积压」）。
echo -e "${YELLOW}[35/42] 孤儿运行时物理表（定义即真相 · G36）...${RESET}"
if ! python3 scripts/check-orphan-tables.py; then
  echo -e "${RED}❌ 存在孤儿物理表（实体已软删但表未清理）；先人工确认无历史价值，再按报告里的 DROP 清单执行${RESET}"
  exit_code=1
fi

# 2026-10-16 接入（G-2 · ArchUnit 冻结债务台账）。此前 check-freeze-ledger.py 只存在于
# 零调用者的 scripts/ci-check.sh —— 台账条目失漂（已修复债务未移除 / violations 计数失真）
# 没有任何门禁能发现，本次接入后本地自检即可拦截。CI 接入属 L3，另行走审批。
echo -e "${YELLOW}[36/42] ArchUnit 冻结债务台账（G-2：ledger 与 archunit_store 双源一致）...${RESET}"
if ! python3 scripts/check-freeze-ledger.py --check; then
  echo -e "${RED}❌ freeze-ledger.yaml 校验失败（隐瞒债务 / store 路径失配 / violations 与 store 实际不一致）${RESET}"
  exit_code=1
fi

# 2026-10-13 新增（G37 · 业务建模三入口职责边界）。三入口职责定稿（综述文档）：
# 模型设计=待办工作台（无实体清单）、业务对象=清单唯一权威、业务数据=数据面（显式选择进入）。
# 「一份数据只允许一个列表真源」：工作台再渲染实体清单 = 用户点两处得两个结果；
# 业务数据全局路由静默选中首个实体 = 用户以为看自己选的、实际看列表首项（静默串实体）。
# 判据按净效果（剥离注释），规则1 = 工作台禁引用 metadataEntityApi，
# 规则2 = 禁 setEntityCode(...[0]...) 默认选中形状。
# 变异验证已做：①植入 import ⇒ 红 ②植入 [0] 选中 ⇒ 红 ③撤掉 ⇒ 绿。
echo -e "${YELLOW}[37/42] 三入口职责边界（工作台无清单 / 数据面无默认选中 · G37）...${RESET}"
if ! python3 scripts/check-three-entry-boundaries.py; then
  echo -e "${RED}❌ 三入口职责边界漂移：工作台出现实体清单，或业务数据默认选中实体${RESET}"
  exit_code=1
fi

# 2026-10-30 新增（G38）。迁移目录的文件名序号是「变更号」而不是执行序：0008/0020 各带
# README/RUNBOOK 附件，而 0052 被两个**不同变更**共用（mdm_entity_workflow 与 rename），
# 0067/0068/0069/0070 同理。于是「该跑哪些、什么顺序」只能靠人按文件名猜——猜错的代价不是报错
# 而是静默少跑：0072 头部记着一次实证，理赔 6 张表「代码已上线、DDL 没跟上」，缺表时
# ClaimCaseTrackRecorder 打 ERROR 后吞异常，审计流水全丢而页面照常返回 200。
# 本步把顺序变成显式数据（migration-manifest.json 的 order），并钉死「新增只能追加在末尾」。
echo -e "${YELLOW}[38/42] 迁移清单完整性（存在即登记 / 顺序冻结 / 重号必登记 · G38）...${RESET}"
if ! python3 scripts/check-migration-manifest.py; then
  echo -e "${RED}❌ 迁移清单与磁盘不一致（新增脚本须登记 order，重号须登记 ordinalConflicts）${RESET}"
  exit_code=1
fi

# 2026-10-30 新增（G39）。本仓有两个 DDL 真源：bone-init.sql（新环境唯一入口）与
# scripts/migration/*.sql（存量环境增量），而此前**没有任何门禁在看两者的等价性**。
# 实测把 init 灌进空库逐表比对：init 121 张、dev 183 张，差值里 25 张是迁移建的结构表；
# 取 6 张关键表双证（wf_task / wf_instance / wf_status_outbox / t_claim_case_track /
# t_claim_inspection / meta_runtime_record_audit）——init 命中 0、dev 命中 6。
# 即新环境灌完 init，工作流引擎与理赔轨迹直接缺表，而缺表不报错（0072 同一种事故）。
# 存量 31 项缺口登记在 init-coverage-baseline.json（只可收缩），新增迁移建表未并入全量直接红。
echo -e "${YELLOW}[39/42] 全量基线覆盖增量建表（init ⊇ 迁移建表终态 · G39）...${RESET}"
if ! python3 scripts/check-migration-init-coverage.py; then
  echo -e "${RED}❌ 迁移建表未并入 bone-init.sql（新环境会缺表；确需暂缓须登记 init-coverage-baseline.json 并写理由）${RESET}"
  exit_code=1
fi

# 2026-10-30 接入：并入区块与活库的一致性。G39 只保证「迁移建过的表在 init 里」，
# 但 init 里那张表的**列与索引**可能与活库已经不是一回事（有人本地 ALTER 过、或并入时抄错）。
# 该区块由 scripts/sync-init-from-live.py 从活库导出，本步逐字节比对；无库时打印 SKIP 并退 0
# （门禁守护「有库可查时不许漂移」，不是「每台机器都必须有库」；跳过必须可见）。
echo -e "${YELLOW}[40/42] 迁移并入区块与活库一致（sync-init-from-live --check）...${RESET}"
if ! python3 scripts/sync-init-from-live.py --check; then
  echo -e "${RED}❌ bone-init.sql 的「迁移并入」区块与活库不一致（跑 python3 scripts/sync-init-from-live.py 重生成）${RESET}"
  exit_code=1
fi


# 2026-10-30 新增（G40）。菜单的 permission 码若在 iam_permission 里找不到对应 code，
# 会在运行时被 MenuApplicationService#current 按 scope 静默剪掉——菜单不报错、接口可见、名字也对，
# 但侧栏里就是没有它（与 G37 图标静默降级同一类「配了不生效」失效，只是后果更重：整菜单消失）。
# 种子由 seed-bootstrap.py 在生成时排除这类幽灵菜单（dev 实测 52 个，引用 19 个从不存在的权限码），
# 本步对产物做静态一致性复核：iam_menu.permission ⊆ iam_permission.code。
echo -e "${YELLOW}[41/42] 种子菜单 permission 自洽（menu.permission ⊆ permission.code · G40）...${RESET}"
if ! python3 scripts/check-seed-menu-permission.py; then
  echo -e "${RED}❌ 种子里存在 permission 指向不存在权限点的幽灵菜单（运行时被静默剪掉）${RESET}"
  exit_code=1
fi

# 2026-10-17 接入：门禁脚本自身单元测试（防门禁回归）。此前 check-archunit-freeze-baseline.py
# 与 check-freeze-ledger.py 零单测，其判据一旦回归无人知晓；scripts/tests/ 下表征测试用
# importlib 加载真实门禁脚本、monkeypatch 模块级 REPO/LEDGER 到临时目录，锁定 R1/R2/R3 与
# R4 判据。接进 pre-commit 后，任何门禁脚本回归都会在提交时被本地自检拦截——门禁本身不再是
# "无人看护的守卫"。
echo -e "${YELLOW}[42/42] 门禁脚本自身单元测试（防门禁回归 · scripts/tests）...${RESET}"
if ! python3 -m unittest discover -s scripts/tests -p 'test_*.py' --verbose 2>&1; then
  echo -e "${RED}❌ 门禁自身单元测试失败（门禁脚本可能已回归，详见上方 traceback）${RESET}"
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
