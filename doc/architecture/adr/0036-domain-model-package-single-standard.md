# ADR-0036：domain 分组形态统一为 `domain/model/{聚合}/`

| 项 | 内容 |
|----|------|
| **状态** | **已采纳（Accepted，2026-09-22）**：D1（唯一形态记法）与 D2（值对象子包名 `valueobject`）经架构师确认；规范与登记已落地，**代码迁移由架构师手工执行** |
| **日期** | 2026-09-22 |
| **决策者** | 架构师 |
| **取代** | `Bone-DDD-最终实践方案.md` E-10「domain 内部分组有两种合法形态」双形态条款（v5.5.16 及以前） |
| **关联** | E-0.2（存量处理 / 受控批量收敛通道）、E-0.3、E-4.1（投影返回类型白名单）、E-9（模块适用性）、E-10.1～E-10.3、E-13.1（后缀与落点表）、[ADR-0030](./0030-domain-repository-read-merge.md)、[ADR-0032](./0032-controlled-batch-convergence.md) |
| **下游同步** | DDD 规范（E-10 参考图 / 双形态条款 / E-10.3 样例 / E-4.1 与 E-13.1 的投影落点）、[03-架构分层规范.md](../../agents/03-架构分层规范.md)、各模块 README 的 `E-10 domain 分组形态登记`、`doc/_generated/**`（迁移后重算）、`studio-generator` 生成模板（**代码，待办**）、形态校验门禁（**未实现，待增项**） |

---

## 背景

### 1. 触发：把 `domain/model/` 定为平台唯一标准

原规范允许两种形态，且明确「两套形态不设优劣……跨模块不要求统一」（E-10 §1514–1519）。架构师决定收敛为唯一形态 `domain/model/`——理由是聚合构件与 `gateway` / `repository` 端口同层平放时概念层级不清。本 ADR 负责把「唯一形态」定义清楚，并给出存量迁移纪律。

### 2. 现行规范对 `model/` 的定义自相矛盾（必须先解决）

同一份规范里，三处关于 `model/` 的说法不能同时成立：

| 出处 | 原文 | 读出的形态 |
|---|---|---|
| E-10 参考图（§1476–1481）+ 条文 §1516 | `model/{aggregate\|entity\|valueobject\|event}`「按构件角色分组」 | **跨聚合角色桶**：全模块的聚合根都进 `model/aggregate/` |
| E-10.3 §1596 | 「聚合根平铺，**非角色子目录**」 | 聚合包内**不得**再按角色分子目录 |
| E-13.1 落点表 §1694 ／ E-4.1 §1037 | 本聚合读投影落点 `domain/<聚合>/projection/` | 预设**一个聚合一个包**；角色桶下这个落点无处安放 |

只写「统一到 `model/`」而不裁定记法，会把这处歧义复制到全部模块。

### 3. 现实里 `model/` 已经长出三种形状

| 模块 | `domain/` 现状 | 对 `model/` 的解读 |
|---|---|---|
| bone-masterdata | `domain/{entity,lineage,quality,record,standard}` **与** `domain/model/{entity,field,quality,record}` 并存 | `model/{聚合}` = 聚合子构件，聚合根仍留在 `domain/{聚合}` —— **一个 domain 两套分组并存** |
| bone-integration | `domain/{client,connector,execution,flow}` **与** `domain/model/{connector,execution,flow}` 并存 | 同上 |
| bone-metadata-server | `domain/model/` 下 9 个类平铺 + `model/physical/` | `model/` = 领域类平铺桶 |
| bone-extension-studio | `domain/model/` 下 9 个类平铺 | 同上 |
| bone-blueprint / bone-iam / bone-system / bone-notification | `domain/{聚合}/{event,valueobject\|vo}` | 不使用 `model/`（扁平） |

结论：**歧义记法本身在生产漂移**——同一句规范被读成三种意思，其中 masterdata 与 integration 已经落到「一个 `domain` 包内两套分组并存」这个 E-10 末句明令禁止的状态。这正是本 ADR 必须先裁定 D1 的原因。

## 决策

### D1. 唯一形态：`domain/model/{聚合}/`（聚合包，包内平铺）

```text
com.bone.{module}/
└── domain/
    ├── model/
    │   ├── {aggregate}/                # 一聚合一包
    │   │   ├── {AggregateRoot}.java    # 聚合根，直接平铺
    │   │   ├── {Entity}.java           # 聚合内实体，直接平铺
    │   │   ├── {ValueObject}.java      # 值对象，直接平铺
    │   │   ├── event/                  # 聚合内领域事件（过去式）
    │   │   └── projection/             # 本聚合读投影（ADR-0030，按需）
    │   └── shared/                     # 跨聚合共享的模型构件（值对象 / 领域异常），按需
    ├── repository/                     # 聚合仓储接口（端口，不随 model 下移）
    ├── gateway/                        # 外部业务能力端口
    ├── extension/                      # 模块自有领域端口子包（如 blueprint 扩展点契约），按需
    └── service/                        # 领域服务（最后选择）
```

硬规则：

- **R1** 聚合根 / 聚合内实体 / 值对象在同一聚合包内**直接平铺**，不再按角色分子目录；
- **R2** **禁止** `domain/model/aggregate` / `entity` / `valueobject` / `event` 这类跨聚合角色桶；
- **R3** `repository` / `gateway` / `service` 以及模块自有的领域端口子包（如 blueprint `extension/`）**留在 `domain/` 根**，不进 `model/`；
- **R4** 同一 `domain` 包内**不得并存**两套分组（`domain/{聚合}` 与 `domain/model/{聚合}` 并存即违规）；
- **R5** 原扁平形态 `domain/{聚合}` 不再是合法变体，**降级为存量形态**（见 D4）。

### D2. 值对象子包名统一为 `valueobject`

现网两种写法：`bone-blueprint` 用 `valueobject`，`bone-iam` / `bone-system` 用 `vo`。本 ADR 取 `valueobject`：它是 E-10 参考记法、是参考实现 blueprint 的现行写法，也与 `projection` / `repository` / `application` 等结构包「写全词」的惯例一致。

**未采纳的备选**：全平台改用 `vo`（更短）。架构师 2026-09-22 确认取 `valueobject`，因此 `bone-iam` / `bone-system` 的 `vo/` 子包按迁移清单改名为 `valueobject`（D1 不受影响）。

### D3. 适用边界

- **强制**：`bone-platform/*` 与 `bone-engine/*` 中具备领域模型的应用模块——blueprint、iam、system、notification、masterdata、integration、metadata-server、extension-studio、studio-generator。
- **豁免**：`bone-framework/bone-core`（`domain/{entity,event,id,extension}` 是内核抽象：`AggregateRoot` / `DomainEvent` / `AbstractDTO`）、`bone-metadata-sdk` 及其它 SDK / 框架库——按 E-9 的「SDK/框架库」档位，只约束依赖方向与领域纯净度，不套应用层目录约定。
- `bone-engine/go-engine/*` 是 Go 引擎，不适用 Java 包规范。
- 豁免模块**可以**自行使用 `model/`，只是不强制迁移。

### D4. 迁移纪律：存量一律是存量

- 存量按 [E-0.2 存量不符合规范代码的处理](../Bone-DDD-最终实践方案.md#e-02-存量不符合规范代码的处理) 触达即收敛；本轮是**全平台一次性结构统一**，属 E-3.11 第 3 条「批量重命名」禁区，**授权载体即本 ADR**（走 [ADR-0032](./0032-controlled-batch-convergence.md) 的受控批量收敛通道）。
- 通道第 ② 条「对外契约全程不变」的等价性证据：本批**只改包声明与 import**，不改任何类型 / 方法 / 字段 / 行为 / HTTP 契约 / 事件 payload。
- **顺序**（风险由低到高；`bone-iam` 最后——中央鉴权模块，且提出本 ADR 时其工作区仍有未提交改动）：`bone-notification` → `bone-system` → `bone-blueprint`（参考实现，须与规范同批对齐）→ `bone-masterdata` → `bone-integration` → `bone-extension-studio` → `bone-metadata-server` → `bone-iam`。
- **每模块一次提交、可回滚**；提交前 `mvn spotless:apply`，提交时 `./scripts/check.sh`（就近 ArchUnit）与该模块 `*ArchitectureTest` 全绿。
- **不设数字 KPI**（E-0.2）：本 ADR 只登记顺序与判据，进度由各模块 README 的迁移状态字段承载。

### D5. 门禁：本轮无 ArchUnit 连锁；形态校验尚未机器化

- **实测**：共享规则库 `BoneDddArchRules` 与各模块 `ArchitectureTest` 的谓词粒度均为 `..domain..` / `..domain.repository..` / `..domain.service..` / `..domain.store..`，**无一条**按聚合包名或包深度判定；`AggregatePureUnitTestGuard` 按「`..domain..` 下的具体聚合根」判定，同样与包深度无关。搬包既不破坏也不新增覆盖。
- **`FreezingArchRule` 基线不需要重冻**：`bone-platform/bone-iam/archunit_store/` 实测为 12 个 **0 字节**违规文件 + `stored.rules`（存「规则文本 → UUID」，UUID 由规则文本派生、与类名无关），没有活跃违规指纹可失效。
- 「`domain` 下不得出现未包在 `model/` 的聚合包」这条机器校验**当前不存在**，列为 G-1.1 待增项；状态真源是 [gate-state.json](../gate-state.json)，在实现并接入 CI 之前**不得写成 Active**。

### D6. 脚手架对齐（代码，待办）

`studio-generator` 当前产出 `{basePackage}.domain.entity`（`entity.ftl`）与 `.domain.repository`（`repository.ftl`），路径在 `CodeGeneratorServiceImpl` / `EntityGenerator` / `RepositoryGenerator` 中拼接——即**第三种形态**。需改为目标形态（生成 `domain/model/{聚合}/…`）。属代码改动，由架构师手工执行，本 ADR 只登记为下游同步项。

## 迁移影响面清单

| 模块 | 现状 | 目标 | 备注 |
|---|---|---|---|
| bone-notification | ~~`domain/{notification,repository}`~~ **已于 2026-09-22 完成迁移** | `domain/model/notification` + `domain/repository` | **已完成**（20 测试全绿）；无 `vo` / `event` 子包；**本模块无 README.md**，E-10 登记随其 README 建立时补 |
| bone-system | ~~`domain/{alert,config,console,dict,log,schedule}` + `{}/vo`、`{}/event`~~ **已于 2026-09-22 完成迁移** | `domain/model/{alert,config,…}/` + `vo→valueobject` | **已完成**（92 测试全绿）；README 包树与登记已同步改写；遗留 `vo` 未统一为 `valueobject` |
| bone-blueprint | ~~`domain/{order,payment}/{event,projection,valueobject}` + `domain/{shared,extension,gateway,repository}`~~ **已于 2026-09-22 完成迁移** | `domain/model/{order,payment,shared}/…`；`extension` / `gateway` / `repository` 留根 | **参考实现，已完成**（首个落地模块，212 测试全绿）；`@EnableExtensionPoints(basePackages="com.bone.blueprint.domain.extension")` 是字符串包名，因 `extension` 留根故无需改动——已逐处核对 |
| bone-masterdata | ~~`domain/{entity,lineage,quality,record,standard}` 与 `domain/model/{entity,field,quality,record}` 并存~~ **已于 2026-09-22 完成合并** | 合并为 `domain/model/{entity,field,quality,record,standard,lineage}/` | **合并双树已完成**（59 测试全绿）；`entity` / `quality` / `record` 三个包名两边都有，已逐个核对归属；遗留 `vo` 命名未统一为 `valueobject` |
| bone-integration | ~~`domain/{client,connector,execution,flow}`~~ **已于 2026-09-22 完成迁移** | `domain/model/{connector,execution,flow}/`；`client` 留根 | **已完成**（75 测试全绿）；`client` 定性为**端口接口**（`ExternalSystemClient` 由 `infrastructure/external/*ClientImpl` 实现），按 R3 留根不进 `model/` |
| bone-extension-studio | ~~`domain/model/` 下 9 个类平铺~~ **已于 2026-09-22 按聚合分组完成** | `domain/model/{plugin,extpoint,extension,execution,marketplace,audit,operation}/` | **已完成**（59 测试全绿）；`domain/{gateway,repository}` 留根；`gateway/*ReadPort` 走 ADR-0013 单独路径 |
| bone-metadata-server | ~~`domain/model/` 类平铺 + `model/physical` + `domain/{enums,service,gateway,repository}`~~ **已于 2026-09-22 完成** | `domain/model/{meta,iam,physical}/` | **已完成**（47 测试全绿）；`enums` 角色包解散，两个枚举定性为模型构件随 `MetaEntity` 迁入 `model/meta/`；`{gateway,repository,service}` 留根 |
| studio-generator | ~~`domain/catalog/{model,repository}` + `domain/{code,history,data}`~~ **已于 2026-09-22 完成** | `domain/model/{catalog,code,data,history}/`；`catalog/repository` 并入 `domain/repository` | **已完成**（52 测试全绿）；D6「生成模板对齐」本次**无需同步**——模板资源未内嵌本模块包名（已全量 grep 确认）；**本模块无 README.md**，E-10 登记随其 README 建立时补 |
| bone-iam | ~~`domain/{account,app,audit,client,dept,menu,permission,role,session,tenant}` + `{}/event`、`{}/vo`~~ **已于 2026-09-22 完成迁移** | `domain/model/{…}/` + `vo→valueobject` | **已完成**（最后一棒，132 测试全绿）；`client`（`SsoClient` / `StorageClient`）经定性为**端口接口**，按 R3 留根不进 `model/`；遗留 `vo` 未统一为 `valueobject` |

## 代价与风险

- **收益**：一次裁决消掉「同一句规范三种读法」的持续漂移；新模块与脚手架有唯一目标；评审不再需要逐模块记忆形态。
- **代价**：8 个应用模块的**模块内**机械改名。实测无跨模块耦合——全仓引用 `com.bone.iam.domain.<聚合>` 的 83 个文件（61 main + 22 test）全部落在 `bone-iam` 内，其余模块同理。
- **风险清单（每模块迁移时逐项核对）**：
  1. **字符串包名**：`@ComponentScan` / `@EnableSqlRepositories` / `@EnableExtensionPoints` 的 `basePackages` 字面量、`@AnalyzePackages`、`logging.level.*`、yml 中的包名——IDE 重命名不会改这些，须手动 grep；
  2. **外置 SQL 目录**：资源路径镜像包路径。实测本仓唯一的 `src/main/resources/sql/…` 镜像的是 `domain/repository`（`blueprint/domain/repository/OrderRepository/…`），而 `repository` **不在迁移范围**，故本项实测为零；迁移时仍须每模块 `find …/resources/sql` 核对一次；
  3. **包私有可见性**：改包会改变 `package-private` 边界。同一聚合内的类相对关系不变（如 `order/` 与 `order/event/`），风险限于个别「原本靠同包直连、迁移后被拆开」的类，以编译通过为准；
  4. **并行工作区**：`bone-iam` 与 `bone-masterdata` 当前有未提交改动，须待其 WIP 落地后再迁。

## 未采纳方案

1. **维持双形态（不动）**：被架构师否决；且上文 §背景 2、3 已证明歧义记法在持续生产漂移。
2. **角色桶 `model/aggregate` / `entity` / `valueobject` / `event`**：与 E-10.3「非角色子目录」直接冲突；与 ADR-0030 / E-4.1 / E-13.1 的 `domain/<聚合>/projection` 落点冲突；把多个聚合的值对象混进同一目录，损失聚合内聚性并制造新的横切大桶（E-10 §1512 明确反对）；全仓零实例。
3. **只改规范、不做存量登记**：会让规范一落地就把 8 个模块判成违规，与 E-0.2 的存量纪律冲突。
