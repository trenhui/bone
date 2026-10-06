# SDK `findById` 缺陷闭环报告（2026-10-06）

> **结论：方案 A 无需执行 —— 缺陷已不复存在。**
> 用户裁定「A：修 SDK 的 `SqlQueryBuilder` 条件绑定」后，我定位到 SDK 源码并复跑验证，
> 实测当前 SDK 生成的 SQL **完全正确**，`findById` 正常工作。
> **后端链路 `chains.py all`：135 PASS / 0 FAIL（100%）**，`创建受限账号` 这条挂了 4 轮的断言通过。
>
> **但更重要的是：本报告要纠正我 10-05 的两条错误结论。**

---

## 一、纠正 10-05 的两个错误判断

### 1.1 「SDK 源码不在本仓」—— 错，它就在本仓

| | |
|---|---|
| **我当时说** | `~/.m2` 里只有 jar、无 sources ⇒ 属外部依赖 ⇒ 本仓改不了 |
| **实际** | `bone-engine/bone-metadata-sdk/src/main/java/com/bone/metadata/sdk/query/builder/SelectBuilder.java` |
| **我怎么错的** | `find / -name "SqlQueryBuilder.java"` 无输出 → 直接下了"外部依赖"结论。**那次 find 被 SIGTERM 打断了** —— "找不到"不能作否定依据 |

**SDK 的真实结构**（供后续参考）：

| 文件 | 行数 | 角色 |
|---|---|---|
| `query/builder/SqlQueryBuilder.java` | **7** | 只是接口（`CompiledQuery build(T ctx)`），`SqlBuilder` 里的 `builders` Map 按 `QueryType` 注册它 |
| `query/builder/SelectBuilder.java` | 129 | **SELECT 真正实现**：第 80 行 `getMainConditions().map(Condition::toSql)` 生成 WHERE，第 106 行加软删 `m.deleted = false`，第 110 行调 `TenantFilterInjector.inject` |
| `query/criteria/Criteria.java` | — | **条件构建真源**：`add(target, field, op, ext, vals)` → `generateParamName(column)`（`column + "_" + counter`，counter 按列名递增）→ `bind(cond)` 绑参 |
| `query/criteria/Condition.java` | — | `toSql()` 按 operator 拼 `"别名.列 OP :参数名"` |

**纪律**：说"X 不存在 / 改不了"之前，先按模块名在本仓找一遍；
`find` 异常退出（SIGTERM/超时）**不能**作为否定结论的依据。

### 1.2 「`findById` 生成的 SQL 缺主键条件」—— 那个 SQL 来自**旧 jar**

10-05 我抓到的（用 SDK 的 `SqlExecutor` 开 DEBUG）：
```sql
WHERE m.deleted = :deleted_0 AND m.deleted = false AND m.tenant_id = :_sdk_tenant_id
params={deleted_0=770000000000000002, _sdk_tenant_id=0}   ← 部门 id 被绑到软删占位符，且无 m.id 条件
```

**10-06 用当前源码重建 SDK 后，同一测试抓到的 SQL**：
```sql
WHERE m.id = :id_0 AND m.deleted = false AND m.tenant_id = :_sdk_tenant_id
params={_sdk_tenant_id=0, id_0=770000000000000002}        ← 完全正确
```

**根因链**：`~/.m2/.../bone-metadata-sdk-1.0.0.jar` 的时间戳是 **2026-10-06 02:41**（昨晚重建），
而我 10-05 全天跑的服务是**更早的 jar**（10-05 启动时加载的版本）。
⇒ **服务重启 ≠ jar 更新**：改了 SDK 必须 `mvn install` 重建 + 重启所有依赖服务才生效。

**纪律**：跨轮次比对外科手术级结论（如"某依赖有 bug、影响 N 处"）前，
先花一句 `ls -la <artifact>` 确认**测的是不是最新构建**。这一条本可以省掉一整轮的错误结论与用户裁定。

---

## 二、本轮实际改动

### 2.1 把复现测试升级为**常驻回归门禁**

`DeptRepositoryPlatformTenantTest`（5 条判据）从「缺陷复现（默认 skip）」改为**必跑**：

- 去掉 `Assumptions.assumeTrue` 与 `-Dbone.iam.repo-findbyid-regression=true` 开关；
- 类注释改写为「回归门禁」，写清：历史症状 → 当时的错误 SQL → 当前正确 SQL →
  **为什么这类测试必须有**（`findById` 是全仓 147 处存在性判定的公共依赖，
  一旦退化表现为「资源不存在」404/500 而数据好好在库里、列表路径却完全正常 ⇒ 极易误判，10-05 误判 4 轮）；
- `DisplayName` 从"缺陷复现"措辞改为描述防复发意图。

5 条判据：

| 判据 | 作用 |
|---|---|
| 平台租户(0)能按主键单查 | 曾经退化的那条主路径 |
| 非 0 租户能按主键单查 | **对照组**：区分"租户口径问题"与"单查整体退化"（10-05 我就是靠它才推翻自己的假设） |
| 查回实体带 `tenantId` | 应用服务 `assertDeptBelongsToTenant` 就比这个值（null ⇒ 误报 404） |
| 跨租户单查查不到 | 隔离线守卫，防止测试给实现开口子（**缺陷期它是假绿**：SQL 无 id 条件时"查不到"与"查得到"都返回 null，修复后才成为真判据） |
| 列表路径能列出部门 | 固化"列表通、单查不通"这个前提，防止将来只测一边 |

### 2.2 顺带修的构建问题

改测试文件后 `mvn spring-boot:run` 被 spotless 拦
（`Failed to execute goal spotless:check ... format violations`）⇒ 已 `spotless:apply`，现在 `spotless:check` 通过。

---

## 三、验证结果

| 项 | 结果 |
|---|---|
| `DeptRepositoryPlatformTenantTest` | **5/5 绿，无任何开关** |
| `spotless:check`（bone-iam） | BUILD SUCCESS |
| **后端链路 `chains.py all`** | **135 PASS / 0 FAIL（100%）** |
| 前端 9 应用 `tsc --noEmit` | 全绿（10-05 已达成，未回退） |
| UI 层 44 菜单巡检 | 44/44 导航 100%，0 console error，0 失败请求（10-05 已达成） |

**`创建受限账号` 终于通过** —— 这条断言从 10-05 上午挂到 10-06，根因既不是"租户口径"也不是"环境"，
而是**我用旧 jar 下的结论**。全仓 147 处 `findById` 依赖随之恢复。

---

## 四、本轮再次验证的环境事实

| 现象 | 真因 | 处置 |
|---|---|---|
| `mvn spring-boot:run` 起来 95 秒 health 200，再过 2 分钟就 000，而 `memory_pressure` 报 free 38% | `nohup ... & disown` 起的进程**被回收** | 后端/前端服务一律用 `run_in_background=true` 的 Bash 任务常驻 |
| 后端重启后立刻跑 `chains.py` → `login failed 503 GW_UPSTREAM_UNAVAILABLE`，重跑 70 秒仍失败 | **网关熔断器冷却需 ~80 秒**（同期直连后端 health 200） | 先 `sleep 80+` 再跑；别把熔断当服务挂 |
| `java -jar` 启 iam 报 `Incorrect ConfigDataLocationResolver` | fat jar nested jar 的 `spring.factories` 扫不到 | 必须 `mvn -o spring-boot:run` |
| 改 Java 文件后 `spring-boot:run` 报 spotless violations | 格式门禁 | 先 `mvn -o -pl <mod> spotless:apply` |

---

## 五、现状与剩余待办

**已完成**：SDK 缺陷闭环（无需改 SDK）· 后端链路 100% · UI 层 44/44 · 前端 9 应用 tsc 全绿 ·
6 个应用的 antd 中文校验 Provider · 8 处 `destroyOnHidden` · 连接器 JSON 模板修复。

**剩余**（与 SDK 无关）：

1. **未跑 `scripts/check.sh`** —— 需补 Maven PATH（`PATH=/Users/renhui.trh/java/apache-maven-3.8.6/bin:$PATH`）；
   改 Java 文件后 spotless 会拦，需先 `spotless:apply`。
2. **`FlowDesign.tsx` 的 `Tabs.TabPane` → `items` 迁移** —— antd 5 deprecation，功能正常。
   要改必须一次成型并立刻 `tsc --noEmit` 验（JSX `{}` 容器内的对象字面量写 `{ key: ... }` 即可，**不需要**转义）。
3. **`file` 服务（8107）未起** —— 需 `mvn spring-boot:run`（fat jar 限制），文件相关 UI 未覆盖。
4. **巡检探针已沉淀**在 `scripts/e2e/ui/`（三个探针 + README 含全部踩坑），下轮无需重写。
