# ADR-0036：domain 分组形态统一为 `domain/model/{聚合}/`

| 项 | 内容 |
|----|------|
| 状态 | 已采纳（2026-09-22）。全部应用模块的存量迁移已于当日完成，见「实现结果」 |
| 日期 | 2026-09-22 |
| 决策者 | 架构师 |
| 取代 | E-10「domain 内部分组有两种合法形态」双形态条款（v5.5.16 及以前） |
| 关联 | E-0.2、E-4.1、E-9、E-10.3、E-13.1、[ADR-0030](./0030-domain-repository-read-merge.md)、[ADR-0032](./0032-controlled-batch-convergence.md) |
| 下游同步 | DDD 规范（E-10 参考树、分组条款、E-10.3 样例、E-4.1 与 E-13.1 投影落点）、[03-架构分层规范.md](../../agents/03-架构分层规范.md)、七个模块 README 的「E-10 domain 分组形态登记」、`studio-generator` 生成模板与 `GeneratedLayoutTest`、`scripts/check-domain-model-layout.py` 与 [domain-model-layout-baseline.json](../domain-model-layout-baseline.json) |
| 未完成项 | 无；D5 / D6 均已于 2026-09-23 实现 |

## 背景

E-10 原先允许两种 domain 分组形态，并声明「不设优劣、跨模块不要求统一」。2026-09-22 架构师决定收敛为唯一形态 `domain/model/`，理由是聚合构件与 `gateway` / `repository` 端口同层平放时概念层级不清。

收敛前，规范自身对 `model/` 有三处互斥说法：

| 出处 | 说法 |
|---|---|
| E-10 参考图与条文 §1516 | `model/{aggregate\|entity\|valueobject\|event}`，按构件角色分组 |
| E-10.3 §1596 | 「聚合根平铺，非角色子目录」 |
| E-13.1 §1694 ／ E-4.1 §1037 | 本聚合读投影落在 `domain/<聚合>/projection/` |

前两条互相否定：若在 `model/` 下按角色建桶，就没有稳定的聚合包承载第三条要求的投影。

现实里 `model/` 也被读成三种意思（2026-09-22 立项时现场）：

| 模块 | `domain/` | `model/` 的用法 |
|---|---|---|
| bone-masterdata | `domain/{entity,lineage,quality,record,standard}` 与 `domain/model/{entity,field,quality,record}` 并存 | `model/{聚合}` 只装子构件，聚合根留在 `domain/{聚合}` |
| bone-integration | `domain/{client,connector,execution,flow}` 与 `domain/model/{connector,execution,flow}` 并存 | 同上 |
| bone-metadata-server | `domain/model/` 下 9 个类平铺，另有 `model/physical/` | `model/` 当领域类平铺桶 |
| bone-extension-studio | `domain/model/` 下 9 个类平铺 | 同上 |
| bone-blueprint、bone-iam、bone-system、bone-notification | `domain/{聚合}/{event,valueobject\|vo}` | 不使用 `model/` |

其中 masterdata 与 integration 已落到「一个 `domain` 内两套分组并存」，正是 E-10 末句禁止的状态。只写「统一到 `model/`」而不裁定 `model/` 内部结构，会把这处歧义复制到所有模块，因此 D1 必须同时定义聚合包内部的组织方式。

## 决策

### D1 唯一形态：`domain/model/{聚合}/`

```text
domain/
├── model/
│   ├── {aggregate}/                # 一聚合一包
│   │   ├── {AggregateRoot}.java    # 聚合根，直接平铺
│   │   ├── {Entity}.java           # 聚合内实体
│   │   ├── {ValueObject}.java      # 值对象
│   │   ├── event/                  # 聚合内领域事件（过去式）
│   │   └── projection/             # 本聚合读投影（ADR-0030，按需）
│   └── shared/                     # 跨聚合共享的模型构件，按需
├── repository/                     # 聚合仓储接口
├── gateway/                        # 外部业务能力端口
├── extension/                      # 模块自有的领域端口子包，按需
└── service/                        # 领域服务（最后选择）
```

- R1：聚合根、聚合内实体、值对象在同一聚合包内直接平铺，不按角色再分子目录。
- R2：不得设置 `model/aggregate`、`model/entity`、`model/valueobject`、`model/event` 这类跨聚合角色桶。
- R3：`repository` / `gateway` / `service` 与模块自有的领域端口子包留在 `domain/` 根，不进 `model/`。
- R4：同一 `domain` 内不得并存两套分组。
- R5：原扁平形态 `domain/{聚合}` 不再是合法变体，降级为存量形态，按 E-0.2 收敛。

### D2 值对象子包名统一为 `valueobject`

原先 blueprint 用 `valueobject`，bone-iam 与 bone-system 用 `vo`。取 `valueobject`，与 E-10 参考记法、blueprint 参考实现以及 `projection` / `repository` 等结构包写全词的惯例一致。备选方案是全平台改用 `vo`，好处是名字更短，代价是要同时改参考实现与规范记法。

### D3 适用边界

- 强制：`bone-platform/*` 与 `bone-engine/*` 中具备领域模型的应用模块，即 blueprint、iam、system、notification、masterdata、integration、metadata-server、extension-studio、studio-generator。
- 豁免：`bone-framework/bone-core`（`domain/{entity,event,id,extension}` 是内核抽象）、`bone-metadata-sdk` 及其它 SDK / 框架库，按 E-9 的「SDK / 框架库」档位只约束依赖方向与领域纯净度。
- `bone-engine/go-engine/*` 是 Go 模块，不适用 Java 包规范。
- 豁免模块可以自行使用 `model/`，本 ADR 不强制其迁移。

### D4 迁移方式

全平台一次性结构统一属于 E-3.11 第 3 条禁止的批量重命名，授权载体为本 ADR，走 ADR-0032 的受控批量收敛通道。等价性证据：只改包声明与 import，不改类型、方法、字段、行为、HTTP 契约与事件 payload。

执行顺序按风险由低到高：notification → system → blueprint → masterdata → integration → extension-studio → metadata-server → iam。每个模块单独提交、可独立回滚；提交前 `mvn spotless:apply`，提交时 `./scripts/check.sh` 与该模块 `*ArchitectureTest` 全绿。进度不设数字 KPI（E-0.2），由模块 README 登记。

### D5 门禁

- ArchUnit 无需改动：共享规则库与各模块 `ArchitectureTest` 的谓词粒度都在 `..domain..` 这一层，没有按聚合包名或包深度判定的规则；`AggregatePureUnitTestGuard` 亦与包深度无关。
- `FreezingArchRule` 无需重冻：`bone-iam/archunit_store/` 的违规文件均为空，键位由规则文本派生，与类名无关。
- 形态校验已实现（2026-09-23）：`scripts/check-domain-model-layout.py`，接入 `scripts/ci-check.sh` `[10/10]`，四条判定为：

  - C1 两套分组并存；
  - C2 `model/` 下平铺；
  - C3 空聚合包；
  - C4 `domain/` 根下未登记子包。

  模块自有的领域端口子包（iam 与 integration 的 `client`、blueprint 的 `extension`）登记在 [domain-model-layout-baseline.json](../domain-model-layout-baseline.json)，只可收缩。状态为 **Manual（本地脚本，未接入 workflow）**，G-1.1 第 18 行由 gate-state.json 渲染。
- 适用边界由脚本自行判定：只在同时具备 `application` 与 `adapter` 包的四层应用模块内生效；按层拆成多个 Maven 模块的引擎（`bone-metadata-engine` 的 `-domain` / `-ports` / `-runtime` / `-starter`）不套用本形态。

### D6 生成模板对齐（2026-09-23 完成）

`studio-generator` 有两条生成通道，都已对齐 D1：

- 模板通道（`entity.ftl` / `repository.ftl` / `controller.ftl` / `applicationService.ftl` 与对应的四个 `*Generator`）：实体包与引用改为 `{basePackage}.{module}.domain.model.{聚合}`，聚合段取实体名全小写（`Order` → `order`，生成后可按业务语义改名）。同时修正了文件路径与 package 声明的系统性错位——实体路径原为 `…/domain/{module}/`、其余三个生成器的路径缺模块段。
- 内联通道（`CodeGeneratorServiceImpl.generateEntity` / `generateRepository` / `generateService`）：包名由 `domain.model.entity` 改为 `domain.model.{聚合}`。原写法是 R2 禁止的角色桶。

`GeneratorUtils` 新增 `aggregateSegment` 与 `basePath` 供两条通道共用；`GeneratedLayoutTest` 锁定四条生成路径与包声明，防止再次漂移。

## 实现结果

2026-09-22 全部完成，各模块测试全绿：

| 模块 | 迁移后 | 规模 |
|---|---|---|
| bone-notification | `domain/model/notification` + `domain/repository` | 20 测试 |
| bone-system | `domain/model/{alert,config,console,dict,log,schedule}`；`vo` → `valueobject` | 92 测试 |
| bone-blueprint | `domain/model/{order,payment,shared}`；`repository` / `gateway` / `extension` 留根 | 212 测试，首个实现模块 |
| bone-masterdata | 双树合并为 `domain/model/{entity,field,lineage,quality,record,standard}`；`vo` → `valueobject` | 59 测试 |
| bone-integration | `domain/model/{connector,execution,flow}`；`client` 定性为端口接口，留根 | 75 测试 |
| bone-extension-studio | `domain/model/{plugin,extpoint,extension,execution,marketplace,audit,operation}` | 59 测试 |
| bone-metadata-server | `domain/model/{meta,iam,physical}`；原 `enums` 解散，随 `MetaEntity` 并入 `model/meta` | 47 测试 |
| studio-generator | `domain/model/{catalog,code,data,history}`；`catalog/repository` 并入 `domain/repository` | 52 测试 |
| bone-iam | `domain/model/{account,app,audit,dept,menu,permission,role,session,tenant}`；`vo` → `valueobject`；`client` 定性为端口接口，留根 | 132 测试，最后一例 |

README 登记：blueprint、iam、system、masterdata、integration、metadata-server、extension-studio 已补「E-10 domain 分组形态登记」；notification 与 studio-generator 无 README，登记待其建立 README 时补。

## 代价与遗留

改名不产生跨模块影响：全仓引用 `com.bone.iam.domain.<聚合>` 的 83 个文件（61 个 main、22 个 test）全部位于 `bone-iam` 内，其余模块同理，改包是模块内操作。

迁移中核对过的三类风险，供后续同类改动复用：

1. 字符串包名字面量不受 IDE 重命名影响，需逐处 grep：`@ComponentScan` / `@EnableSqlRepositories` / `@EnableExtensionPoints` 的 `basePackages`、`logging.level.*`、yml 中的包名。blueprint 的 `com.bone.blueprint.domain.extension` 因 `extension` 按 R3 留根而无需改动。
2. 资源目录镜像包路径：本仓唯一的 `src/main/resources/sql/…` 镜像 `domain/repository`，不在迁移范围内。
3. `package-private` 可见性随包边界变化，以编译通过为准。

遗留：无。D5 布局门禁与 D6 生成模板均已实现。

## 未采纳方案

1. 维持双形态。歧义记法已经产生三处互斥说法与两种并存现状，收敛正是本次目的。
2. 角色桶 `model/aggregate`、`model/entity`、`model/valueobject`、`model/event`。与 E-10.3「非角色子目录」冲突，与 E-13.1 / E-4.1 的 `domain/<聚合>/projection` 落点冲突；把多个聚合的值对象混进同一目录，损失聚合内聚性；仓内无实例。
