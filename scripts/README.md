# scripts/ —— 工程脚本总目录（唯一索引）

> **编目铁律**：目录里每个手写脚本必须能在本文件查到；查不到 = 不存在 = 会被重复造轮子。
> 新增 / 合并 / 归档脚本时**同步维护本表**（`scripts/tests/test_scripts_catalog.py` 会机械校验）。
>
> 分域细则不在此重复，按目录跳转：
> - 端到端测试（怎么跑 / 怎么写 / 数据规范 / 基线）→ [`e2e/README.md`](e2e/README.md)
> - 浏览器探针纪律 → [`e2e/ui/README.md`](e2e/ui/README.md)
> - 归档说明 → [`archive/README.md`](archive/README.md)
> - 迁移与种子清单真源 → `migration/migration-manifest.json`、`seed/uat/`
> - 方案口径（架构层沉淀）→ [doc/architecture/Bone-测试策略.md §7](../doc/architecture/Bone-测试策略.md)
> - 门禁状态真源 → [Bone-DDD-最终实践方案 §G-1.7](../doc/architecture/Bone-DDD-最终实践方案.md#hc-hard-constraints)

## 0. 先看这四条（决定你该跑哪个脚本）

1. **agent / 新机器先自检** → `python3 scripts/bone.py verify --format json`（全量统一健康度，与 CI 同一条命令）；只要秒级体检用 `verify --quick`；`doctor` 只查环境，能力目录用 `list --format json`。
2. **改完 Java 要提交** → `./scripts/check.sh`（pre-commit 入口，只跑变更模块 + 全树文本门禁）。
3. **要复现 CI / 判断「是不是我改坏的」** → `./scripts/ci-local.sh`（调 `ci-check.sh` 全量）；CI 侧由 `.github/workflows/ci.yml` 逐条阻断。
4. **要跑端到端联调** → `./scripts/e2e/run.sh list`，先 `preflight` 再分组；根目录 `verify_*` 里有 3 个是 `e2e/` 未覆盖的唯一能力，见 §5，不要一概当成一次性脚本。

> **执行真源只有一处**：`check.sh` 的 `python3 scripts/**/check-*.py` 调用行。`registry/checks.json` 是它的派生物，
> 由 `python3 scripts/bone.py sync` 生成、`sync --check` 双向对账（接线改了没同步 / 判据加了没接线都会判红）。
> `ci-check.sh`（本地全量 17 步，由 `ci-local.sh` 调用）与 `ci.yml`（CI 显式逐条、`blocking` 单条标注）是**两份消费视图**，
> 不各自实现判据；历史上三者清单漂移，故不再手写第四份清单，能力目录由注册表派生。

---

## 1. 门禁与 CI（Gate）

| 脚本 | 作用 |
|---|---|
| `check.sh` | pre-commit 主入口：`[1/59]`…逐项跑变更模块 ArchUnit + 全树文本门禁 + 门禁自身单测；熔断计数防连续失败 |
| `ci-check.sh` | 本地全量复现入口，17 步；由 `ci-local.sh` 调用。`ci.yml` **不调它**，而是显式逐条列出以便单条标注 blocking |
| `ci-local.sh` | 本地复现 CI（含 JaCoCo / Gitleaks 等重步骤） |
| `check-readme-facts.sh` | README 事实反漂移：端口表 ↔ `application.yml`、看板 done 项 ↔ README 措辞 |
| `check-gate-liveness.py` | 孤儿门禁检测（G47）：`check-*.py` 必须出现在活入口的**调用行**上；豁免只可收缩 |
| `gate-liveness-allowlist.json` | 上面这道门禁的豁免清单（唯一真源）；`unwired` / `partial_wire` 两类，理由与 owner 必填 |
| `ci/` | 补充 CI 判据与各域一致性采集（见 §1.1） |
| `ci/scripts-registry-guard.sh` | CI 单行守卫：只调 `bone.py verify --format json`（本地与 CI 同一条命令）；接入 `.github/workflows/ci.yml` 属 L3，待架构师审批 |
| `lib/e2e-jwt.sh` | JWT 取用助手，全仓唯一消费者是 `verify-three-modules-e2e.sh`（不与 CI 共用） |
| `lib/gatekit/__init__.py` | 门禁共享框架导出面（`gate` / `Report` / `repo_root`） |
| `lib/gatekit/core.py` | 框架实现：判据收集 → 统一输出 → 退出码契约 → 仓库根定位 |
| `lib/gatekit/example_gate.py` | **新门禁模板**（复制改造，见 §9）；不是生产门禁 |
| `bone.py` | **统一入口**：`list`（能力目录）/ `check`（库内判据结构化结果）/ `external`（库外判据，默认 dry-run）/ `data`（**造数唯一入口**，见 §9）/ `waivers`（豁免台账到期体检）/ **`verify`（一条命令跑完全部自检）** / `sync`（注册表对账 + 调用行形态守卫）/ `selftest` / `doctor` |
| `check-feature-data-impact.py` | 后端功能/DDL/Entity 变更 → 造数资产与 E2E 用例同步义务（C1 清单 / C2 场景不变量 / C3 同步义务，见 `e2e/README.md` §3.4） |
| `lib/__init__.py` | 共享层包声明（供 `from lib import gate, registry`） |
| `lib/gate.py` | gatekit 扩展层，**不是第二套框架**：git 文件枚举 / baseline 棘轮 / JUnit 与结构化结果；退出码与根定位委托 gatekit |
| `lib/registry.py` | 注册表解析、生成、双向对账与库外门禁校验 |
| `lib/waivers.py` | 豁免台账治理：到期硬失败、`owner`/`reason` 必填、新增台账必须登记、存量只告警不编造 |
| `lib/data_assets.py` | 造数能力清单共享层：静态清单校验 / 全场景不变量实跑 / `coverage` 派生；动态能力一律委托 `datagen.py` 与 `scenarios.py` |
| `registry/checks.json` | 由 `check.sh` 调用行生成的判据注册表；**派生物，勿手改** |
| `registry/external.json` | 库外判据能力目录（13 条，登记调用者、`cwd` 假设、`migration_blocker` 与处置建议，不物理搬迁） |
| `registry/external-known-failures.txt` | 库外判据实跑的**只可收缩欠债基线**（当前 1 条既有漂移）；`--ratchet` 保证新增失败判红、已修复条目必须删行 |
| `registry/waivers.json` | 全仓 24 份 baseline/allowlist/ledger 的**唯一元数据台账**：1 条 `active`（有 owner/到期）+ 23 条 `legacy`（待认领）；可手工维护但须过 `bone.py waivers` |
| `registry/data-assets.json` | **造数能力唯一清单**：9 项资产 / 5 个功能域同步义务 / `acks` 例外 / `coverage` 派生段；AI 造数前先 `bone.py data list` |
| `requirements-dev.txt` | Python 判据第三方依赖声明（PyYAML / PyMySQL），新机器先 `pip install -r` |
| `hooks/pre-commit` / `hooks/pre-push` | 入库版 git hooks，由 `setup-hooks.sh` 安装到 `.git/hooks/`；提交/推送时自动刷新派生映射并校验造数资产 |

### 1.1 `ci/` 子目录

| 脚本 | 作用 |
|---|---|
| `ci/check-async-tenant-context.py` | 异步链路租户上下文传递检查 |
| `ci/check-config-key-contract.py` | 配置键契约（键名 ↔ 消费方） |
| `ci/check-ddd-doc-code-sync.py` | DDD 文档 ↔ 代码同步 |
| `ci/check-error-endpoint-permit-all.py` | 错误端点 `permitAll` 面收敛 |
| `ci/check-iam-init-demo-password.sh` | IAM 初始化演示口令检查 |
| `ci/probe-security-fail-open-default.py` | 安全默认失败（fail-open）探测 |
| `ci/collect-all-compliance.sh` | 全域合规采集总入口 |
| `ci/collect-blueprint-compliance.sh` / `collect-console-compliance.sh` / `collect-extension-compliance.sh` / `collect-iam-compliance.sh` / `collect-metadata-compliance.sh` | 各域合规采集 |
| `ci/validate-console-openapi.sh` / `validate-extension-openapi.sh` / `validate-generator-openapi.sh` / `validate-iam-openapi.sh` | 各域 OpenAPI 契约校验 |
| `ci/test-console-security.sh` | 控制台安全用例 |

---

## 2. `check-*.py` 判据族

> **依赖**：多数只用标准库，但 `check-openapi-contract.py`、`check-freeze-ledger.py`、`check-openapi-int64-contract.py`、`check-openapi-generation-readiness.py`、`check-migration-init-coverage.py`、`check-orphan-tables.py` 依赖 PyYAML / PyMySQL；声明在 `requirements-dev.txt`，新机器必须先安装，缺包不得「自动跳过」。
> **退出码**：新增判据按 §9 契约 `0` 合规 / `1` 违规 / `2` 运行错误；存量脚本的**默认语义并不统一**（`check-sdk-persistence.py` 无参是 report-only 返 0，须显式 `--check`），调用时以 `registry/checks.json` 记录的实参为准，不要凭文件名猜。
> 接线状态以 `gate-liveness-allowlist.json` 为准；**单测缺口**由 `scripts/tests/` 托管（新增门禁必须带 `test_<同名>.py`，见 §8）。

### 2.1 架构与分层（DDD / 模块边界）

| 脚本 | 判据 |
|---|---|
| `check-application-constructs.py` | 应用层构件形态（E-10.2 / E-13.2 / E-3.2） |
| `check-autoconfig-registration.py` | Spring 自动配置注册完整性 |
| `check-domain-model-layout.py` | 领域模型唯一形态 `domain/model/{聚合}/` |
| `check-three-entry-boundaries.py` | 业务建模三入口页职责边界 |
| `check-synapclaim-architecture.py` | 理赔 20 子域 → 7 上下文归属完整互斥 |
| `check-synapclaim-module-boundaries.py` | 理赔模块边界 |
| `check-ddd-doc-drift.py` | DDD 文档 ↔ 实现漂移（四项） |
| `check-ddd-gate-state.py` | HC 硬约束表状态 ↔ 实测（Planned 不得写成 Active） |
| `check-archunit-freeze-baseline.py` | ArchUnit 冻结基线只可收缩 |
| `check-freeze-ledger.py` | 冻结债务台账（只登记有真实违规的条目） |
| `check-java-import-integrity.py` | Java 引用方入库 ⇒ 被引用方必须也入库（G48） |
| `ddd-archtest-template.py` | 新模块 ArchUnit 测试模板（非门禁，复制用） |

### 2.2 持久化 / DDL / 租户

| 脚本 | 判据 |
|---|---|
| `check-orm-banned.py` | HC-001：禁 MyBatis / MyBatis-Plus / JPA / Hibernate（import + pom 双面） |
| `check-sdk-persistence.py` | 持久化只走 `bone-metadata-sdk` |
| `check-sql-entity-column-coverage.py` | SQL 列 ↔ 实体字段一致（G49） |
| `check-sql-template.py` | SQL 模板占位符与参数绑定 |
| `check-id-annotation.py` | 主键注解规范 |
| `check-soft-delete-declaration.py` | 软删声明基线一致 |
| `check-ddl-doc-sync.py` | DDL ↔ 文档同步 |
| `check-ddl-duplicate-column.py` | 同表同列 / 同表重复建表（ERROR 1060/1050，G44） |
| `check-ddl-required-columns.py` | 必填列（审计 / 租户）声明 |
| `check-orphan-tables.py` | 孤儿表（无实体 / 无迁移来源） |
| `check-migration-manifest.py` | `migration/` 每个文件必须在 manifest 登记 |
| `check-migration-init-coverage.py` | 迁移终态存活表 ↔ `bone-init.sql` 覆盖 |
| `check-tenant-entity-declaration.py` | DDL 有 `tenant_id` ⇒ 实体必须声明 `tenantId`（ADR-0034） |
| `check-tenant-deletion-coverage.py` | 租户表必须被清退清单覆盖（R8①） |

### 2.3 对外契约 / API / 错误码

| 脚本 | 判据 |
|---|---|
| `check-controller-authorization.py` | Controller 鉴权注解齐备 |
| `check-controller-authorization-url-bypass.py` | URL 绕过鉴权（如 `;.json` 后缀） |
| `check-controller-response-envelope.py` | HC-003：统一 `ApiResponse` / `PageResult` |
| `check-cross-service-exception-mapping.py` | 跨服务异常映射 |
| `check-openapi-contract.py` | OpenAPI 契约漂移 |
| `check-openapi-generation-readiness.py` | 契约是否可生成客户端 |
| `check-openapi-int64-contract.py` | int64 精度契约（序列化为字符串） |
| `check-sdk-contract-surface.py` | SDK 导出面 ↔ 契约 |
| `check-error-code-duplication.py` | 错误码重复定义 |
| `check-error-code-landing.py` | 错误码落地（定义必有抛出点、抛出点必有定义） |
| `check-event-envelope-version.py` | 事件信封 / Topic 版本契约 |
| `check-paging-param-names.py` | 分页入参命名族（禁第三套） |
| `check-paging-param-ssot.py` | 分页入参唯一真源 |
| `check-paging-current-field.py` | 分页 `current` 字段口径 |
| `check-paging-total-normalize.py` | 分页 total 归一 |
| `check-slice-truncation.py` | 字段/列投影禁止 `.slice(0, N)`（G33） |

### 2.4 前端 / 界面协议 / 菜单

| 脚本 | 判据 |
|---|---|
| `check-frontend-response-contract.py` | 前端消费的响应契约 |
| `check-frontend-route-param.py` | `:id` 参数必须被真实消费 |
| `check-ui-schema-samples.py` | `ui-schema-samples/*.json` 合规（**已在 allowlist，待架构师评审接线**） |
| `check-ui-schema-sync.py` | doc 真源 / 后端 classpath / 前端白名单三方一致（**同上**） |
| `check-menu-icon-whitelist.py` | 菜单图标名白名单 |
| `check-menu-route-consistency.py` | 菜单 `path#hash` ↔ 微应用路由 |
| `check-menu-single-entry.py` | 菜单信息架构三条硬规则 |
| `check-seed-menu-permission.py` | 种子菜单 `permission` 必有对应权限码 |
| `check-i18n-sync.py` | 国际化 key 同步 |

### 2.5 安全 / 运行时 / 文档事实 / AI 基线

| 脚本 | 判据 |
|---|---|
| `check-security-fail-open-default.py` | 安全开关默认失败（禁 fail-open） |
| `check-approver-reference-not-exposed.py` | 审批人引用不外泄（ADR-0044 决策 3） |
| `check-iam-runtime-baseline.py` | `bone-init.sql` 必须带可登录的运行时数据 |
| `check-deterministic-cache.py` | 禁概率性 / 错位缓存 |
| `check-runtime-topology.py` | 运行时拓扑图端口 ⊆ 代码真源（G41） |
| `check-doc-code-symbols.py` | doc 引用类名 ↔ 仓库 `.java`；baseline 只可收缩 |
| `check-eval-baseline.py` | G0 基线真源（冻结集 / 指标字典 / wired 符号） |
| `check-claim-ai-evalset.py` | 理赔 AI 评测集基线 |
| `check-feature-data-impact.py` | 功能 / DDL / Entity 变更 → 造数资产与 E2E 用例同步义务（C1 清单 / C2 场景不变量 / C3 同步义务，规范见 `e2e/README.md` §3.4） |

---

## 3. 端到端与联调（E2E）

**权威入口只有一个**：`e2e/`。能力目录、执行入口、数据规范、基线见 [`e2e/README.md`](e2e/README.md)。

| 入口 | 作用 |
|---|---|
| `e2e/run.sh` | `list / lint / selftest / preflight / datagen / impact / py-core / py-sweep / py-chain / py-domain / node / all / <脚本名>` |
| `e2e/lint.sh` | 零依赖语法门禁（`py_compile` + `node --check` + 造数真实性 R1–R5） |
| `e2e/datagen.py` | **造数唯一入口**：企业场景/原子值 → `json/env/md`（`run.sh datagen`，规范见 `e2e/README.md` §3.1） |
| `e2e/impact.py` | 接口变更 ↔ 用例映射：`--staged/--rev --strict` 阻断、`--print-case-files` 供 pre-push 实跑（§3.2） |
| `e2e/check-data-realism.py` | 造数真实性门禁（R1–R5，基线只可收缩，§3.3） |
| `e2e/e2e_lib/scenarios.py` | 企业业务场景造数（订单→理赔 / 采购三单 / 租户 IAM / 主数据 / AI 建议回流，带不变量） |
| `e2e/selftest.py` / `e2e/selftest.cjs` | 共享层离线自证（守恒 / 判据 / JUnit / 登录棘轮） |
| `e2e/preflight.py` | 环境快检（端口 + 可选登录探活），失败直接给「起哪个服务」 |
| `e2e/e2e_lib/` | 公共层唯一真源（Python 14 模块 + `client.cjs` / `fixtures.cjs` / `ui.cjs`） |
| `e2e/ui/` | 浏览器探针单族（20 个 `.mjs`） |
| `e2e/templates/` | 新用例模板 |
| `e2e/cleanup-residue.py` | 残留定点回收执行体（`teardown.register` 的落点） |
| `e2e/lowcode-closed-loop.cjs` | 低代码最小闭环（非浏览器探针族） |
| `e2e/logs/` | 运行日志（git 忽略） |

### 3.1 字典 v1.1 专项探针（尚未并入 `e2e/`）

| 脚本 | 作用 |
|---|---|
| `probe-dict-v11-e2e.py` | 经网关全链路：三态迁移 / `/options` 口径 / 物化状态 / 漂移四类 / 引用方清单（19 项） |
| `probe-dict-v11-version.py` | 版本协商与 SWR（10 项） |
| `probe-dict-v11-governance.py` | 灰度开关 / 三指标 / 迁移向导（29 项） |
| `probe-dict-v11-ui.cjs` | UI 层：三态交互 / 引用方抽屉 / 深链 / 面板漂移 / 灰度禁用态（50 项） |

> 在 `doc/architecture/adr/0043` 与设计文档中被引用为联调证据；并入 `e2e/` 需先补 `run.sh` 分组与 teardown，属后续工作。

---

## 4. 迁移与种子（Migration / Seed）

清单与顺序真源是 `migration/migration-manifest.json`（由 `check-migration-manifest.py` 托管），**不要**在本文件重复清单。

| 脚本 | 作用 |
|---|---|
| `migration/` | 有序迁移 SQL（131 个），每个必须登记 manifest |
| `migrate.py` | 迁移执行器（按 manifest 顺序、记录 journal） |
| `sync-init-from-live.py` | 从活库反向生成 `bone-init.sql`（禁止手工粘贴） |
| `seed/` | 种子数据；`seed/uat/` 拟真数据集（`gen.py` + `wordlist.json` 词表单源 + 生成产物） |
| `seed-bootstrap.py` | 引导种子灌入（空库可用性实测配套） |
| `seed-system-dicts.py` | 通过 API 注入系统管理场景字典数据 |

### 4.1 一次性迁移 codemod（历史执行完毕，保留供追溯）

| 脚本 | 作用 |
|---|---|
| `migrate-usecase-to-handler.py` | UseCase → Handler 批量改写 |
| `migrate-extension-studio-service-to-application.py` | extension-studio Service → ApplicationService |
| `migrate-extension-studio-store-to-repository.py` | extension-studio Store → Repository |
| `patch-extension-studio-controller.py` | extension-studio Controller 补丁 |
| `thin-extension-studio-controller.py` | extension-studio Controller 瘦身 |
| `plan-c-rename.py` | Plan C 命名批量改写 |
| `ddd-rename-cmd-qry.py` | `*Command` / `*Query` 包路径改名 |
| `repair-entity-names.sh` | 存量 `custom_entity_name` 驼峰收敛（运维一次性） |
| `biz_identity_align_dry_run.py` | 业务身份对齐 dry-run |

---

## 5. 老联调脚本（历史资产，**不要新增同类**）

> 这些是 `scripts/e2e/` 整合前的手写 curl / 断言脚本。**新脚本一律进 `e2e/`**；
> 下表只用于「看到它们时知道是什么」，其中部分仍在文档或专项 CI 中被引用，故保留。

| 脚本 | 状态 |
|---|---|
| `verify-three-modules-e2e.sh` | 三模块联调；被 `docs-compliance.yml` 引用 |
| `start-three-modules-e2e.sh` | 配套启动器 |
| `verify-claim-e2e.sh` / `verify-claim-funding-e2e.sh` | 理赔 / 理赔资金链路（ADR 与历史报告引用） |
| `verify-workflow-designer.sh` | 工作流设计器联调（设计文档引用） |
| `verify-mvp-e2e.py` | MVP 范围联调（wiki / 设计文档引用） |
| `verify_tenant_isolation_e2e.py` | 租户隔离 E2E（`tenant-isolation.yml` 调用，**活入口**） |
| `verify_tenant_isolation.py` | 租户隔离（被多租户方案文档引用） |
| `verify-reserved-column-e2e.py` | **保留（唯一能力）**：机制 B 预留列池验收——发布不 ALTER、`ext_str_*`/`ext_text_*` 回填、`field_metadata`/`column_allocation` 落库、63 个池列完整；`e2e/` 无对应覆盖 |
| `verify-system-app-integration.py` | **保留（唯一能力）**：`bone-system-app` ↔ `bone-system` 联调——配置中心分页/CRUD 闭环、字典两级模型、定时任务闭环；`e2e/` 只覆盖 metadata 链路，不覆盖 system 配置中心 |
| `verify_masterdata_tenant_isolation.py` | **保留（唯一能力）**：主数据两租户隔离——平台全局只读/不可篡改、跨租户 IDOR 直读 404、overlay 私有写；`verify_tenant_isolation_e2e.py` 验的是 IAM 侧租户，**不重叠** |
| `verify-synapclaim-case360.sh` | **change 级构建期校验**（`synapclaim-case360-object-cohesion`）：shell 分组渲染/类型、claim-app 内容组件与 URL 契约、文档族门禁；只验本 change 交付物，不跑全仓 monorepo 测试。非通用联调，不应并入 `e2e/` |

> 上面三个「**保留（唯一能力）**」是 2026-11-11 逐份读判据后的结论，不是「没人管所以留着」。
> **2026-11-12 已接入统一入口**：`./scripts/e2e/run.sh py-domain`（实跑 23/23 + 19/19 + 54/54）。
> 单列一组而不并入 `py` / `all`，是因为拓扑要求不同（预留列直连 :9001、system-app 需 `bone-system` 在跑），
> 混进 CI 口径会让「只起了网关」的环境整组假红。
> 后续若迁进 `e2e/` 目录本身，须同时复用 `e2e_lib`（login/call/check/teardown），见 §11 S1。


---

## 6. 开发环境与运维

| 脚本 | 作用 |
|---|---|
| `dev/up-all-services-e2e.sh` | 起全部后端服务（E2E 用） |
| `dev/up-all-services-v2.sh` | 起服务 v2 编排 |
| `dev/up-all-frontend-e2e.sh` | 起前端微应用（E2E 用） |
| `dev/restart-mvp-services.sh` | 重启 MVP 服务 |
| `dev/svc-ready.sh` | 服务就绪探活 |
| `dev/db-init.sh` / `dev/db-verify.sh` | 库初始化 / 校验 |
| `dev/load-env.sh` | 环境变量加载 |
| `dev/up-blueprint-multichannel.sh` | 蓝图多渠道环境拉起 |
| `dev/verify-multichannel-after-env-recovery.sh` | 环境恢复后多渠道校验 |
| `dev-frontend-matrix.sh` | 前端微应用矩阵构建 |
| `local-up.sh` | 本机一键起（固化了两个环境坑） |
| `mysql-84-upgrade.sh` | MySQL 8.4 升级助手 |
| `setup-hooks.sh` | 把入库版 `hooks/pre-commit` 安装到 `.git/hooks/`（内容不同时先备份 `.bak`）；不改 git config |
| `update-checkpoint.sh` | 更新检查点记录 |

---

## 7. 安全 / 契约 / 供应链

| 脚本 | 作用 |
|---|---|
| `scan-secrets.sh` | 密钥扫描（Gitleaks 封装） |
| `security-check.sh` | 安全门禁汇总 |
| `contract-lock-check.sh` | 契约锁校验 |
| `validate-contract.sh` | 契约校验（按 feature 参数） |
| `generate-sdk.sh` | SDK 生成 |
| `sdk-contract-smoke-test.sh` | SDK 契约冒烟 |
| `sdk-contract-smoke-manifest.py` | 冒烟清单（规范真源的派生物） |
| `sdk/SdkContractSmoke.java` | 冒烟测试 Java 载体 |
| `gen-sbom.sh` | 生成 CycloneDX 物料清单 |
| `git-filter-secrets.sh` / `git-filter-secrets-replacements.txt` | 历史密钥清洗（高危，需审批） |
| `fix-account-role-data.sql` / `fix-iam-data.sql` | IAM 存量数据修复（一次性 SQL） |

---

## 8. 分析与辅助

| 脚本 | 作用 |
|---|---|
| `analyze-impact.py` | 变更影响面报告（方案 §11.7.1） |
| `analyze-metrics.py` / `analyze-metrics.sh` | 质量度量口径（方案 §11.7.2） |
| `audit-org-coverage.py` | 组织主管覆盖率审计 |
| `ddd-compliance-scan.sh` | DDD 合规扫描汇总 |
| `clean-archunit-store-orphans.py` | 清理 ArchUnit store 孤儿条目 |
| `gen-controller-response-baseline.py` | 生成 Controller 响应基线（须实测端点） |
| `auto-format.sh` | 格式化助手 |
| `auto-commit.sh` | 自动提交助手（**谨慎**：受分支纪律约束） |
| `synapclaim-domain-context-map.json` | 理赔上下文映射真源 |
| `iam-runtime-baseline.json` | IAM 运行时基线（`check-iam-runtime-baseline.py` 真源） |
| `frontend/check-frontend-id-types.py` / `frontend/fix-id-types.py` | 前端 ID 类型检查 / 修复 |
| `ops/verify_comet_install.py` | Comet 安装校验 |
| `ops/org-coverage-backfill.sql` | 组织覆盖回填 SQL |
| `prompts/01-global-anchor.md` / `prompts/02-nightly-design-review.md` | AI 协作提示词锚点 |

---

## 9. 新增脚本规范（写之前先读完本节）

1. **先判归属**：门禁 → 根 `check-*.py`；端到端 → `e2e/`（并接 `run.sh` 分组）；环境 → `dev/`；迁移 → `migration/` + manifest。
2. **必须可查**：落地同一次改动里更新本文件（新门禁还要更新 §2 分组）；否则 `test_scripts_catalog.py` 判红。
3. **命名表达生命周期**：`check-*`（门禁，常驻）/ `gen-*`（生成基线）/ `verify-*`（历史联调，**不再新增**）/ `_*` 与 `tmp-*`（一次性，**禁止留在根目录**）。
4. **退出码即契约**：`0` 合规 / `1` 违规，禁止「有产出即 0」。只观察不阻断的用 `--report-only` 显式声明。
5. **新门禁必须带单测**：`scripts/tests/test_<脚本名下划线>.py`，用 `importlib` 加载真实脚本 + 临时夹具（照抄 `test_check_gate_liveness.py`）；缺单测由 `test_scripts_catalog.py` 棘轮判红。
6. **判据必须写清真源**：docstring 里写「判据 / 规则真源 / 实测背景」，否则下一个人只能靠猜。
7. **接线后再交付**：写完没人调的门禁等于没写（G47）；确需暂缓的登记 `gate-liveness-allowlist.json` 并写理由与 owner。
8. **接线后同步注册表**：调用行写进 `check.sh` 后跑 `python3 scripts/bone.py sync`，否则 `sync --check` / CI 守卫判红；不要在 `registry/checks.json` 里手改任何条目。
9. **退出码三段式**：`0` 合规 / `1` 违规 / `2` 运行错误（缺依赖、缺环境变量、注册表损坏）；缺依赖一律 fail-fast，禁止「自动跳过」造成假绿。
10. **结构化输出**：需要被 agent 或 CI 解析时用 `bone.py check --format json|junit`，或基于 `lib/gate.py` 的 `CheckResult` / `render_junit`；不要再造一套纯文本汇总。
11. **单测必须有牙**：新门禁与共享层的测试要含负向变异（注入违规后必须判红），参照 `test_gate_lib.py` 与 `test_bone_registry.py`。

### 统一入口（agent 复用契约）

本地与 CI 只认一条命令：`python3 scripts/bone.py verify --format json`；秒级体检用 `verify --quick`。
它按固定顺序聚合 6 步，每步仍调用既有实现，不复制判据：

| 步骤 | 执行 | 覆盖 |
|---|---|---|
| `sync` | `bone.py sync --check` | 注册表双向对账 / 调用行形态 / 豁免台账 |
| `waivers` | `bone.py waivers --format json` | 豁免台账到期与 owner |
| `checks` | `bone.py check --format json` | 全部库内判据（由 `check.sh` 调用行派生，当前 61 条） |
| `external` | `bone.py external --run --report-only --ratchet --format json` | 库外 shadow 观察态 |
| `tests` | `unittest discover -s scripts/tests` | 门禁自身单测与编目守卫 |
| `doctor` | `bone.py doctor --format json` | 环境与依赖 |

`--quick` 只跑 `sync` / `waivers` / `doctor`；`--fail-fast` 首败即停；`--timeout` 单步超时（默认 600s）。
JSON 输出 schema `bone.verify.v1`，每步带 `rc` / `duration_ms` / `summary` / `failures`（失败判据的 id / path / 输出）/ 失败时的 stdout 与 stderr 尾部；顶层 `summary.observed_debt` 单独计 shadow 观察态的已登记欠债（`failed=0` 不等于无欠债）。
退出码 `0` 全绿 / `1` 判据违规 / `2` 运行错误；结构化步骤退出 0 却无 JSON 结果按运行错误判红，防「跑通了但什么都没验」。

其余命令按用途：

- `bone.py list --format json`：能力目录（全部库内 + 13 库外）；`--external` 附库外登记。
- `bone.py data list --format json`：**造数唯一入口**——AI 造数前先查这里（`registry/data-assets.json` 静态资产 + `datagen.py` 动态场景/原子值 + 全场景不变量 + 健康度）；`bone.py data scenario sales-claim --seed demo-a --format md` 直接取企业场景数据；`bone.py data fixture person --count 5` 取原子值；`bone.py data validate` 体检清单与功能变更同步义务。禁止另写一套造数脚本，规范见 `e2e/README.md` §3.1 / §3.4。
- `bone.py check --only <id> --format json` / `--format junit`：跑指定判据并返回统一 finding 汇总；`--group` / `--changed` / `--dry-run` 为同族过滤。
- `bone.py external --format json`：列出 9 个可调用库外判据（**默认 dry-run**，`--run` 才实跑）；脚本对启动目录的假设登记在 `external.json` 的 `cwd`——`check-contrast.py` 用裸文件名读同目录 HTML，切换 cwd 时入口会把脚本路径绝对化。
- `bone.py external --run --report-only --ratchet`：**shadow 观察态**；既有欠债登记在 `external-known-failures.txt` 可观察、不阻断，**新出现的失败一律判红**，已修复条目报 `stale_entries` 要求收缩基线；观察期稳定后去掉 `--report-only` 即升级为 blocking。
- `bone.py waivers --format json`：**豁免台账体检**；24 份 baseline/allowlist/ledger 统一登记在 `registry/waivers.json`；`active` 必须有 `owner` / `reason` / ISO `expires`，**过期硬失败**；`legacy` 如实列「待认领」不编造负责人；新增台账未登记即判红。
- `bone.py sync --check`：守注册表一致性与**调用行形态**——环境变量前缀、引号含分号的参数、命令替换会让注册表静默丢参，判据以默认语义运行却仍然「绿」，故一律判红。
- `bone.py selftest` / `bone.py doctor`：契约测试 / 环境体检。

CI 侧由 `.github/workflows/ci.yml` 的 blocking step 调 `bash scripts/ci/scripts-registry-guard.sh`，脚本内只执行上面这条 `bone.py verify`，本地与 CI 同口径；接线待办见 §11 S5。

### 新门禁骨架（复用 `lib/gatekit`，别自己发明一套）

`scripts/lib/gatekit/` 是门禁/脚本共享框架（`Report` 收集判据 → 框架统一输出与退出码，
`repo_root()` 收敛仓库根定位，`self_test` 提供变异验证）。**模板是 `lib/gatekit/example_gate.py`，复制它改判据即可**：

```python
import sys
from pathlib import Path


def _gatekit_path() -> str:
    """向上找 scripts/lib（含 gatekit 子包），使 gatekit 可作为顶层包导入。"""
    p = Path(__file__).resolve().parent
    for _ in range(8):
        if (p / "lib" / "gatekit").is_dir():
            return str(p / "lib")
        if p.parent == p:
            break
        p = p.parent
    raise RuntimeError("找不到 scripts/lib/gatekit")


sys.path.insert(0, _gatekit_path())
from gatekit import Report, gate, repo_root  # noqa: E402


def main(report: Report, args) -> None:
    root = repo_root()
    report.fail("xxx.java:12 违反判据（判据真源：doc/... §N）")
    report.info("yyy 正常")


if __name__ == "__main__":
    sys.exit(gate(main))
```

硬约束：**零第三方依赖**（标准库 only，CI 不装包）、**违规逐条可定位**（文件:行 + 怎么改，只印总数等于没报）、
**不得「有产出即 0」**（反例见 `e2e/ui/ui-designer-probe.mjs` 的修复）。
存量 59 个门禁**不做批量迁移**——只在被触碰时逐个改到 gatekit（理由同 `e2e/` 的「不批量盲改」）。

---

## 10. 目录约定

| 目录 | 是否可手工放脚本 | 备注 |
|---|---|---|
| `scripts/`（根） | 是（须登记本文件） | 门禁 / 一次性辅助 |
| `e2e/`、`e2e/ui/` | 是（须登记各自 README） | 端到端权威入口 |
| `ci/`、`dev/`、`lib/`、`lib/gatekit/`、`ops/`、`frontend/`、`prompts/`、`sdk/` | 是（须登记本文件） | 分域助手；`lib/gatekit/` 是判据框架唯一真源 |
| `registry/` | **否**（生成物 + 手工登记） | `checks.json` 由 `bone.py sync` 生成、勿手改；`external.json` 可手工维护但须过 `sync --check` |
| `hooks/` | 是（须登记本文件） | 入库版 git hook，由 `setup-hooks.sh` 安装 |
| `tests/` | 是（`test_*.py`） | 门禁自身单测与契约测试，`check.sh [42]` 全量跑；`bone.py selftest` 亦可 |
| `migration/` | 是（须登记 manifest） | 顺序与完整性由 `check-migration-manifest.py` 托管 |
| `seed/uat/build/` | **否** | 生成产物 |
| `archive/` | **否** | 归档区，见 `archive/README.md` |
| `tmp/`、`logs/`、`__pycache__/` | **否** | git 忽略 |

---

## 11. 已知待办（有主人、有判据，不是许愿池）

| # | 待办 | 为什么不能现在做完 | 判据（做完的样子） |
|---|---|---|---|
| S1 | 3 个「唯一能力」脚本**迁进 `e2e/` 目录**并改用 `e2e_lib`（已接入 `run.sh py-domain`，未搬家） | 搬家要重写 login/check/teardown 三件套并逐个真跑对照基线，属独立改动 | `run.sh list` 成员路径落在 `e2e/` 内、脚本内零自带 check 样板、断言数不降 |
| S2 | `probe-dict-v11-*.{py,cjs}` 4 个字典专项探针并入 `e2e/` | 依赖字典 v1.1 灰度开关现场 | 同 S1 |
| S3 | 门禁单测缺口（冻结在 `test_scripts_catalog.MISSING_TEST_BASELINE`）：2026-11-12 补分页族 4 + 错误码族 3 + 菜单/国际化 4，**42 → 31** | 补测必须逐个真跑验证，批量无验证的补测等于假绿 | 基线集合逐条收缩至空，且 `check.sh [42]` 全绿 |
| S4 | `scripts/tmp/npmcache` 等本地缓存目录 | 未跟踪、属个人机器噪声，不影响 CI | 本地清理后全树扫描显著变快 |
| S5 | ~~把 `ci/scripts-registry-guard.sh` 接进 `ci.yml`~~ **已接线并收敛为单命令**，待架构师 Review 确认（L3） | CI 配置修改按 `AGENTS.md` L3 仍需人审；本次改动只新增 1 个 blocking step，不动既有判据 | `ci.yml` 出现 `Scripts Registry & Catalog Guard (blocking)` 且为绿；guard 脚本只执行 `python3 scripts/bone.py verify --format json`；`check-gate-liveness.py --strict` 仍绿；`--no-verify` / CI-only 提交同样受守卫约束 |
| S6 | **执行层 + shadow 已落地**：13 个库外判据登记 + 机械对账，9 个 `invokable` 全部实跑（8 pass / 1 条既有欠债入基线）；`bone.py verify` 的 `external` 步骤每次跑 `--run --report-only --ratchet`；**仅剩物理归位与合并**（3 个 `move_candidate` / 4 个 `consolidate_candidate`）待 L3 审批 | 与同目录资产（`docs.manifest.json` / `__dirname` 下 HTML / Vitest workspace）强耦合，须先按 `migration_blocker` 改路径解析为 `--root`，且属 L3 删码/搬迁；不搬目录也能复用，故不限期阻塞 | 判据迁移前后输出逐条 diff 为零、`sync --check` 绿、R4 扫描域覆盖到归位后的脚本 |
| S7 | 修复 `check-contrast.py` 报的既有漂移 `FATAL: 选择器未匹配到规则 -> .actionbar button.primary` | 2026-11-12 首次实跑发现；HTML 中 `.actionbar` 在但 `button.primary` 规则缺失，判据此前不在任何入口故长期不可见。属 UI 资产决策，需 owner 定 | 判据转 pass，`--ratchet` 报 `stale_entries`，随即从 `external-known-failures.txt` 删行；shadow 步骤可去掉 `--report-only` 升级为 blocking |
| S8 | 23 份 `legacy` 豁免台账认领 `owner` / `expires` 并转 `active` | 存量台账跨多个业务域，负责人与合理复审日须各域确认；不编造 owner 与到期日，当前只登记 + 持续告警 | `bone.py waivers` 的 `unowned` 逐条降至 0；每条 `active` 都有 `owner`/`reason`/ISO `expires`，过期由 `sync --check` 自动判红 |

