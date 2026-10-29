# Bone 测试策略

> **文档性质**：单测、集成、契约、架构与 E2E 的**分层边界与门禁**。  
> **更新**：2026-10-28  
> **关联**：[Bone-API-规范.md](./Bone-API-规范.md) §15、[Bone-DDD-最终实践方案.md](./Bone-DDD-最终实践方案.md)

---

## 1. 测试金字塔

```
        E2E（少量，发布前）
      契约 / 集成（模块 verify）
    单测 + ArchUnit（每次 PR）
```

| 层级 | 占比目标 | 速度 |
|------|----------|------|
| 单测 | 70%+ 用例数 | 秒级 |
| 集成 | 关键路径 | 分钟级 |
| 契约 | API 变更 | 分钟级 |
| E2E | 核心旅程 | 十分钟级 |

---

## 2. 单测

| 项 | 规则 |
|----|------|
| 框架 | JUnit 5 + Mockito |
| 命名 | `*Test.java` / `*Tests.java` |
| Domain | 纯逻辑，无 Spring 上下文 |
| Application | Mock Repository；测用例编排 |
| 覆盖率 | **已配置模块 ≥80% 行覆盖**（当前 `bone-metadata-sdk`）；其余模块待接入 JaCoCo 门禁（门槛真源见 Bone-DDD `G-1.7` 的 HC-005，当前父 POM 为 10%） |

---

## 3. 架构测试

| 模块 | 工具 | 内容 |
|------|------|------|
| bone-iam、bone-integration 等 | ArchUnit | 分层依赖、`*Service` 包位置 |
| 新增模块 | **必须** 提供 `ArchitectureTest` | 见 DDD 文档 |

---

## 4. 契约测试（HTTP）

真源与流程见 **[Bone-API-规范 §15](./Bone-API-规范.md#15-契约测试http--openapi)**。

| 类型 | 门禁 |
|------|------|
| openapi-diff | PR breaking 检测 |
| Provider（MockMvc） | 成功 + 典型 4xx |
| Pact | 对外 SDK / 跨团队 |

---

## 5. 集成测试

| 项 | 规则 |
|----|------|
| DB | Testcontainers MySQL 或 H2（仅非 MySQL 特性） |
| Redis | 嵌入式或 Testcontainers |
| 租户 | 每用例设置 `TenantContext`（见 [多租户规范](./Bone-多租户规范.md)） |

---

## 6. 前端

| 项 | 工具 |
|----|------|
| 单测 | Vitest |
| Lint | ESLint（`npm run lint`） |
| E2E | Playwright（关键路径，逐步）；统一方案与入口见 §7 |

---

## 7. 端到端可复用测试方案（scripts/e2e）

> **定位**：本节沉淀 E2E 的**方法论与门禁口径**（可复用测试方案）；操作细节只有唯一真源，不在本文复制——
> 怎么跑/怎么写见 [scripts/e2e/README.md](../../scripts/e2e/README.md)（现场手册 + 能力目录），
> 模块 API 见 `e2e_lib` 各模块 docstring；agent 认知入口见 [doc/agents/04 §6](../agents/04-测试与代码质量.md)（AGENTS.md 路由「测试 / 质量门禁 → 04」自动加载）。

### 7.1 三层沉淀入口（按需加载）

```
doc/agents/04 §6（agent 认知入口：何时需要 E2E）
   └─ scripts/e2e/README.md（现场手册：怎么跑、怎么写、能力目录 §4）
        └─ e2e_lib docstring（真源：模块 API 与契约）
```

编目铁律：**能力目录里每个脚本必须可查**——查不到 = 不存在 = 会被重复造轮子；新增/合并/归档脚本须同步 README §4。

### 7.2 库不是脚本堆（复用什么）

| 层 | 位置 | 唯一职责 |
|---|---|---|
| Python 公共层 | `scripts/e2e/e2e_lib/`（config / http / modeling / runtime / fixtures / report / teardown / mysql / sign / chain_runner） | 登录、断言、数据、残留回收、链路执行器的**唯一真源** |
| Node 公共层 | `e2e_lib/client.cjs` + `fixtures.cjs` + `ui.cjs` | 与 Python **跨语言对称**：同口径凭据/数据（同 seed 同数据）/`uiLogin` |
| 用例层 | 根目录 `*-full-chain-e2e.py`、`chains.py` 等 | **只写业务断言**，禁止复制 login/call/check/teardown 样板 |
| UI 探针 | `scripts/e2e/ui/` 单族（全 `.mjs`，Page Object 式） | 浏览器旅程断言；登录一律 `uiLogin`，不内联选择器样板 |

### 7.3 写一份新 E2E 的门禁口径（八步，细则见 README §2）

1. 配置只从 `e2e_lib.config` 取（环境变量可覆盖，零硬编码口令）；2. `http.login()` 失败即退出，不许静默假红；3. 数据一律 `fixtures` 生产仿真，**每个值只生成一次存变量**；4. 断言有牙——删掉对应实现必须变红，只断 200 不算数；5. 负向用例成对（400/409/412/422 闸门）；6. 运行级残留 `run_suffix` + `teardown.register` 自动回收（`BONE_E2E_KEEP_RESIDUE=1` 保现场）；7. `report.finish()` 按失败数定**退出码即契约**；8. 提交前 `py_compile`/`node --check` + 真跑全绿 + `./scripts/check.sh`。

### 7.4 数据铁律与残留治理（要点）

- 生产仿真：可读单号（`doc_no`）、非圆整金额（`amount`）、**守恒拆分**（`split`，Σ 精确；负向用 `break_` 破坏再恢复）、受控词表**单一 JSON 真源**（三处消费方只加载不内嵌）、同 seed 同数据可复跑比对——细则与反例表见 README §3。
- 残留：退出即回收（Python `teardown.register` / Node `registerCleanup` 对称）；排查用 `cleanup-residue.py --suffix <SFX>` 先 dry-run 再 `--apply`。

### 7.5 基线与执行入口

| 回归套件 | 断言基线（2026-10-28） | 说明 |
|---|---|---|
| `two-scenario-full-chain-e2e.py` | 90 | 三样板：建模深化 + 状态机 + 回收站 + 审计 |
| `app-to-data-full-chain-e2e.py` | 54 | 应用→数据全生命周期 + MySQL 落库核查 |
| `ui-schema-samples-e2e.py` | 31 | submitRules/linkage 服务端强制 |

基线数以脚本实际输出的「汇总」行为准（**重构不改基线**是提交纪律）；执行统一走 `scripts/e2e/run.sh`（`lint` 语法门禁 / `py` / `node` / 指定脚本 / `all`），CI 与编排只看退出码。

---

## 8. CI 映射

以 [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) 为准：

| Job | 内容 |
|-----|------|
| `backend-quality` | `mvn spotless:check` + `mvn clean verify -DskipITs=true` |
| `backend-security` | OWASP Dependency-Check（`failBuildOnCVSS=7`） |
| `frontend-quality` | `bone-frontend` 下 lint + build；**CI 主栈 pnpm**（与本地 npm workspaces 共存，详见 [bone-前端架构 §8](./bone-前端架构.md)） |
| `docker-build` | 镜像构建（按需） |

**蓝图**：根聚合不含 `bone-blueprint`；本地 `mvn -f bone-blueprint/pom.xml test`（见 [wiki/08](../wiki/08-blueprint与主工程对齐.md)）。`scripts/ci-local.sh` 可能包含额外检查，注释以脚本为准。

---

## 9. PR 检查清单

- [ ] 新逻辑有单测  
- [ ] API 变更有契约/OpenAPI  
- [ ] 新错误码有断言  
- [ ] ArchUnit 通过（若适用）  
- [ ] 覆盖率未降（模块门禁）  
- [ ] 动过 `scripts/e2e` 后基线不降、`run.sh lint` 过（§7.5）  

---

## 10. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-17 | 初版；与 API §15 分工 |
| 2026-10-28 | 新增 §7 E2E 可复用测试方案（三层入口/库分层/门禁口径/数据铁律/基线）；原 CI/PR/修订顺延为 §8/§9/§10 |
