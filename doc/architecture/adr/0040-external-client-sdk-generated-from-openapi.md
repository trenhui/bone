# ADR-0040: 对外客户端 SDK — 以 OpenAPI 规范为唯一真源生成，不手写占位模块

**状态**：**已接受**（2026-10-03，架构组裁定）· **生成与烟测已落地**｜ **关联**：ADR-0039（发布包）、`doc/architecture/Bone-API-规范.md`、门禁 #16 / #18 / #19 / **#21**

> **裁定记录（2026-10-03）**：本 ADR 由「提议」转为**已接受**。裁定同时授权了 A2（`int64` → `string`，L3 breaking）与10b（补 5 份规范的 `securitySchemes`），二者已在同日完成并各配机器门禁（#18/#19）。
> **尚未完成**的是第 12（制品库发布流水线）与第 15（分页收敛后再发 SDK），二者依赖外部条件。
> **第 11 / 14 项已于 2026-10-03 落地**（见下「生成与烟测已落地」章节），并新建门禁 #21把「规范可喂给生成器」变成机器可校验的约束。

## 背景与问题

Bone 的 README 曾对外宣称「通过 API/SDK 分发」，仓库里也确实存在 `bone-sdk/bone-client-sdk` 与 `bone-sdk/bone-openapi-sdk` 两个模块，并在根 `pom.xml` 的 `<modules>` 中参与构建。2026-10-03 实测它们的真实状态：

| 事实 | 证据 |
|---|---|
| 业务代码 | 各**只有一个** `com/bone/cloud/Main.java`（19 行，IntelliJ 模板：`System.out.printf("Hello and welcome!")` + `i = 1..5` 循环） |
| 依赖 | 两个 `pom.xml` **零 `<dependency>`** |
| 测试 | **无 `src/test`** |
| 被依赖情况 | 全仓 `pom.xml` 中**零处** `<dependency>` 指向它们 |
| 构建产物 | 真出 jar：`bone-client-sdk-1.0.0.jar` 12 条目 / 2.4 KB，唯一 class 就是那个 `Main.class`（1187 字节） |
| 引入时间 | 2025-10-03「调整Bone工程包结构」随目录一起进来，**从未提交过业务代码** |

**核心矛盾不在"没实现"，而在"看起来有"。** 空壳模块比"没有这个模块"更危险：它占据模块树、依赖图与构建产物三位一体，读者（以及 AI）看到它会假设「客户端 SDK 已经有了」，真去写代码才发现拿到的是打印 hello world 的模板。同类风险**当时已在另一个位置造成真实事故**：`sys_config` / `meta_field` 因未声明 tenantId 而静默跨租户（见 ADR-0029/0034 背景）——本质同形：**声明存在 ≠ 能力到位**。

而契约侧的原料其实齐备：`doc/architecture/openapi/` 已有 8 份规范、**100 条 path、142 个操作**，`iam-v1` 已演进到 1.1.0、`extension-v1` 到 1.2.0，CI 有 `openapi-diff` job 用 oasdiff 阻断破坏性变更。这意味着**客户端 SDK 应当是构建产物，而不是手写模块**。

## 决策

1. **移除 `bone-sdk` 整棵目录**（2026-10-03 已执行）：从根 `pom.xml` `<modules>` 移除 `bone-sdk`，删除 `bone-client-sdk` / `bone-openapi-sdk`。同步清理 12 处外部引用（`docker/Dockerfile` 的 `COPY`、2 个 spotless `-pl` 列表、3 个门禁脚本扫描根、5 处文档、1 份基线登记）。
2. **对外客户端 SDK 若要提供，一律从 `doc/architecture/openapi/` 的规范生成**，不手写、不先建空壳占位。生成物是构建产物，不进 `src/`、不进版本库。
3. **规范是唯一真源**。SDK 不得自带独立于规范的模型或路径声明；规范变更须先过 oasdiff，再重新生成 SDK。
4. **门禁 `check-sdk-contract-surface.py` 的扫描范围**改为盯真实存在的对外 SDK（`bone-metadata-sdk`、`bone-extension-sdk`），规则不变：① 除去 IDE 模板类后无业务类 ⇒ 必须登记为 placeholder，不允许沉默地空着；② 已有业务类但零测试 ⇒ 失败（对外契约面是别人的编译依赖，零测试意味着破坏性变更无人拦截）。
5. **README 的能力宣称回到事实**：由「API/SDK 分发」改为「标准 REST API 分发」。
6. **对外 SDK 目标语言定为 Java**（2026-10-03 用户裁定）。外部集成方以 Java 为主，故只承诺 Java SDK，不生成 TS / Python 客户端。生成器选型见「生成方案」。

## 生成方案（Java）

**生成器**：openapi-generator 的 `java` generator。HTTP 客户端 library 的选择：

| 候选 library | 结论 |
|---|---|
| `okhttp-gson` | ✅ 推荐。主力 HTTP 客户端成熟、gson 与生成的 model 无缝对接；`WebClient` 同类但依赖 reactor，对纯 SDK 是负担 |
| `resttemplate` / `webclient` | 与仓库 Spring 栈一致，但把Spring 依赖泄进对外 SDK，调用方若非 Spring 项目需额外引入 |
| `native`（JDK HttpClient） | 零第三方依赖，但 openapi-generator 对其支持相对薄，错误处理与拦截器能力弱 |

**待定**：`apiPackage` / `modelPackage` 的包名规范（建议 `com.bone.sdk.<domain>.api` / `...model`，`domain` 取 8 份规范名去掉 `-v1`）。

**8 份规范 → 8 个独立 artifact 还是 1 个聚合 jar**：建议 8 个独立模块 + 1 个 `bone-sdk-bom` 聚合，理由是各域鉴权与生命周期不同（`iam` / `blueprint` / `console` 有 `bearerAuth`，其余 5 份**无 securitySchemes**），混在一个 jar 里调用方无法只取所需依赖。

### 硬阻塞：规范类型与后端序列化不一致（生成前必须解决）

**实测事实**（2026-10-03 统计，8 份规范全量；**注意下表是修复前的快照**，修复结果见「A2 已完成」与门禁 #18/#19）：

| 项 | 数量 |
|---|---|
| `type: integer, format: int64` | **71 处**（其中 **60 处为包装 `Long` → 已改 `string`**；**11 处为原生 `long` → 保持 `int64`**） |
| `type: integer`（无 format） | 30 处 |
| `type: string` | 81 处（修复后增加 60） |
| 声明了 `securitySchemes` 的规范 | **8 份全部**（修复前 3 份：`iam` / `blueprint-orders` / `console`） |
| 无 `securitySchemes` 的规范 | **0 份**（修复前 5 份：`extension` / `generator` / `integration` / `masterdata` / `metadata-runtime`） |
| `components.schemas` 复用定义 | 近乎为0（多数响应内联schema） |

`MetadataAutoConfiguration.boneLongToStringCustomizer()`（`support/config/MetadataAutoConfiguration.java:60-67`）全局注册 `serializerByType(Long.class / Long.TYPE, ToStringSerializer.instance)`，**所有应用都 `@Import` 它，是唯一落点**。其 javadoc 写明动机：雪花 ID 18~19 位超 JS Number 上限 2^53，以 JSON number 返回时前端 ID 末几位被静默截断（758267976611790848 → ...800），回传后端即 404 且无任何 JS 报错。

**⇒ 规范声明 `integer/int64`，服务端实际下发 JSON 字符串。** 生成器会照规范产出 `private Long id`，Gson/Jackson 在**多数配置下能容忍**字符串→Long，但这是依赖反序列化器宽松行为，不是契约保证；一旦调用方开启严格模式或换用别的反序列化器（如 `moshi` 直译），即`NumberFormatException`。

三个处置方案：

| 方案 | 内容 | 代价 |
|---|---|---|
| **A. 改规范对齐实现**（推荐） | 71 处 `int64` → `type: string` + `format` 备注雪花 ID | 规范改法本身是 breaking（`int64`→`string`），oasdiff 会报，**且前端需同步改40 个文件 / 331 处**（见下） |
| B. 改实现对齐规范 | 去掉 `ToStringSerializer` | 前端 ID 截断风险回归，**不可接受**（有真实事故记录） |
| C. SDK 侧加 `String ↔ Long` 适配层 | 生成后手改或加 type mapping 扩展 | 把契约漂移固化进 SDK，规范与实现继续不一致；且 71 处逐个映射易漏 |

**建议 A，但A 的真实成本高于"改 71 处规范"**。实测前端 TS 类型声明（排除 `node_modules`/`dist`，且只统计语义为标识符的字段名）：

| 类型 | 处数 |
|---|---|
| ID 声明为 `string` | 197 |
| **ID 声明为 `number`** | **331**，涉及 **40 个文件** |

`number` 型 Top 文件：`apps/bone-masterdata-app/src/services/api.ts`（72）、`apps/bone-extension-app/src/services/extensionApi.ts`（48）、`apps/bone-masterdata-app/src/types/governance.ts`（27）、`packages/shared-types/src/masterdata.ts`（25）、`apps/bone-system-app/src/services/api.ts`（19）、`packages/shared-types/src/integration.ts`（17）。

**⇒ 后端全平台发字符串，而前端 331 处类型声明说`number`——这是已经存在的 type lie**，只是靠 `===` 之类的比较大多能容忍（两侧都是 number 语义），一旦与真实字符串混用（如 `Map` key、`===` 与来自后端的其他值比较）即失效。这与记忆里已记录的「`PageResult.total: number` 属已知 type lie」是**同一类问题的更大范围版本**。

因此方案 A 应拆为两步，且**前端类型修正可独立先行**（不依赖 SDK 决策，只依赖后端序列化这个既成事实）：

1. **A1先修前端 331 处类型 lie**（前端已是 `string` 才正确，因为运行时就是字符串）。这与 SDK 生成解耦，可立即做。
2. **A2 再改 OpenAPI 规范的 71 处 `int64`→`string`**（L3 breaking，需 oasdiff 基线对齐 + 架构师审批）。前端已对齐后，此步无额外前端成本。

A1 属"修正类型声明以匹配既有运行时行为"，不改变任何运行时行为，风险低但**触及 11 个包 / 331 处 / 40 个文件**，须逐包`tsc` 验证。归类如下：

| 包 | 处数 |
|---|---|
| `apps/bone-masterdata-app` | 115 |
| `apps/bone-extension-app` | 65 |
| `packages/shared-types` | 54 |
| `apps/bone-integration-app` | 28 |
| `apps/bone-system-app` | 22 |
| `apps/bone-metadata-app` | 22 |
| `apps/bone-generator-app` | 9 |
| `packages/shared-services` | 6 |
| `apps/bone-iam-app` | 4 |
| `packages/core` | 3 |
| `apps/bone-shell` | 3 |

**建议 A1 先做且单独排期**（优先 `packages/shared-types` 54 处 → 各 app 的 `services/api.ts`，因为类型定义集中在那里、消费方最多），每包改完即构建验证，不要一次性全改。**目前无任何机器门禁覆盖此类 type lie**，建议 A1 完成后新增门禁 `check-frontend-id-types.py`：扫描前端 TS 中语义为标识符的字段，`number` 声明即失败（基线只可收缩）。否则改完无防 regress。

A2 才是真正的规范 breaking 变更（L3，oasdiff 会报 `int64`→`string`）。

#### A2 已完成（2026-10-03）+ 门禁 #19 已建立

**关键实测发现：71 处 `int64` 不是同质的，必须逐字段核对 Java 侧类型才能改。**
判据不是"运行时是字符串"这么笼统 —— 全局 `ToStringSerializer` 只对**包装类型 `Long`** 生效，
**原生 `long` / `double` 仍然是 JSON number**：

| 类别 | 数量 | Java 侧依据 | 处置 |
|---|---|---|---|
| 包装 `Long` ⇒ 运行时字符串 | **60** | `CreateOrderReq#customerId`(`Long`) / `OrderDto#id`(`Long`) / `PageResult#total`(`Long`) 等 | 已改为 `type: string` |
| 原生 `long`/`double` ⇒ 运行时数字 | **11** | `console-v1` 的 `KeyMetrics`（`long userCount` 等 8 个）、`ResourceUsage`（`long memoryUsedBytes`/`memoryMaxBytes`）、`ServiceStatus`（`long latencyMs`） | **保持 `int64` 不动** |

**若不做这个区分而一刀切把 71 处全改成 `string`，会在 `console-v1` 上制造 11 处新的 type lie**
（控制台指标会变成 `"1055"`），比现状更糟。这是本轮最值得留痕的判断。

分布（改前的 71 处）：`integration` 19 / `extension` 17 / `blueprint-orders` 12 / `console` 11（保留）/
`masterdata` 10 / `generator` 1 / `metadata-runtime` 1（`RuntimeRecordPage.total`，多行写法）。
另 `components/PageResult.yaml` 的 `total` 同为包装 `Long`，已修正；同文件 `page`/`size`/`pages`
是 `Integer`，保持 number。

| 项 | 内容 |
|---|---|
| 脚本 | `scripts/check-openapi-int64-contract.py`（`--report-only` / `--list-native-long`） |
| 豁免机制 | 脚本内 `PRIMITIVE_LONG_FIELDS` 白名单，每条附Java 字段与原生类型依据，**只可收缩** |
| 本地载体 | `scripts/check.sh` `[19/19]` |
| CI 载体 | `.github/workflows/ci.yml` 的 `backend-quality` job，`OpenAPI int64 Contract Lint (blocking)` |
| 状态登记 | `gate-state.json` 文档门禁 **#19**（Active） |

**判据实现上踩的一个坑（易漏）**：扫描必须**同时**覆盖 `components/schemas/*/properties/*`
与 `paths/*/parameters[]`。第一版只遍历 `properties`，只抓到 14 处 —— 路径与查询参数
（`{ name: id, in: path, schema: { type: integer, format: int64 } }`）才是 `int64` 主体，
占六成以上。补上 `parameters` 后才与全量 71 对上账（60 + 11 白名单 = 71，逐份对账无差）。

**双向验证**：注入「`PageResult.total` 改回 `integer/int64`」探针 → EXIT=1 精确点名；
把白名单内 `console.userCount` 改成 `string` → EXIT=0（确认豁免真实生效，
不是"因为没扫到所以绿"）；`redocly lint` 8/8 EXIT=0。

#### A1 已完成（2026-10-03）+ 门禁 #17 已建立

实测 HEAD 里 ID 型 `number` 声明为 **330 处**（11 个包 / 44 个文件），**已全部改为 `string`**。验证：`tsc --noEmit` 前后对比（基线 135 既存错误，未新增）；全仓 `Number()`/`parseInt`/自增自减/与数字比较对 ID 的使用**均为 0 处**（唯一真实的 `Number(previewTemplateId)` 已在同批修掉，见下）。

同期修掉一个**真实的静默截断缺陷**：`bone-generator-app` 的 `GenerateConfigModal.tsx` 里 `useState<string>` 存着模板 ID，调用时却 `Number(previewTemplateId)` 强转——18 位雪花 ID 会被静默截断（与 `AccountManagement.tsx` 注释里记录的同一类坑）。已改为直传字符串，`templateApi.getById` 签名同步改为 `(id: string)`（同文件另一个 `dataSourceApi.getById` 本就是 `string`）。

新增门禁：

| 项 | 内容 |
|---|---|
| 脚本 | `scripts/frontend/check-frontend-id-types.py`（`--check` / `--baseline`） |
| 基线 | `doc/architecture/frontend-id-type-baseline.json`（`exemptions: []`，只可收缩） |
| 本地载体 | `scripts/check.sh` `[8/11]`（步骤号已由 10 步顺延为 11 步） |
| CI 载体 | `.github/workflows/ci.yml` 的 `backend-quality` job 第 14 步 `Frontend ID Type Lint (blocking)` |
| 状态登记 | `gate-state.json` 文档门禁 **#17**（Active） |

判据要点：字段名语义为标识符（`Id`/`ID`/`Ids`/`IDs`/`_id`/`_ids` 结尾，或全等 `id`/`ids`），且排除 `width`/`height`/`size`/`index`/`page`/`count` 等 UI 布局与计数字段。**双向验证过**：注入 `id`/`masterDataEntityId?`/`recordIds` 三个 ID 字段 + `version`/`pageSize`/`width`/`grid` 四个非 ID 字段的门禁探针，**恰好只报 3 处**、不误报 4 处；`check-ddd-gate-state.py` 在删掉 workflow 调用后 `EXIT=1` 并明确指出「只能算 Manual」。

> **登记格式踩坑**：`gate-state.json` 的载体反查正则 `` `scripts/[\w\-./]+` `` 要求**反引号内只有纯路径**，写成 `` `scripts/...py --check` `` 会抓不到 → 自检不报错 → 门禁"看起来 Active 但没被验证过"。参数必须写在反引号外（`...py` 加 `--check`）。

### 次要阻塞与注意事项

- **本地 Maven 仓库无 openapi-generator 插件**：`~/.m2/repository/org/openapitools/` 不存在，首次生成须联网拉取插件及其依赖树。本仓库日常构建走 `-o`（离线），故生成步骤需单独联网执行，不能塞进离线流水线。
- ~~**5 份规范无 `securitySchemes`**~~ → **已解决（2026-10-03）**：8 份规范现已全部声明 `bearerAuth` + 顶层 `security`（门禁 #18）。生成 Java SDK 时 8 个域都会有认证拦截器，调用方无需自行拼 `X-Tenant-Id` 之类的头。
- **`PageResult.total` 跨规范已统一为 `string`**：8 份规范里的分页响应 total 原本散落着 `int64`（包装 `Long`，运行时字符串），现已全部对齐；但 `page`/`size`/`pages` 是 `Integer`，仍是 number —— 生成 SDK 后 `getTotal()` 返回 `String`、`getPage()` 返回 `Integer`，调用方需注意。
- **`servers.url` 全是相对路径**（如 `/api/v1/iam`），无host。生成 SDK 需调用方在构造时提供 base URL，文档需写明。
- **`components.schemas` 近乎为空**：多数响应内联 schema ⇒ 生成的 model 类会比预期多且可能重复。需在生成后核对 model 类数量与命名。
- **分页契约一致性已达成（2026-10-06 复核）**：`pageNum/pageSize` 存量已按 API 规范 §5.2 路线② 全量收敛，对外入参现仅存 `page/size`（30 个）与语义不同的 `cursor/limit`（2 个）两套。SDK 生成不再面临「三套命名固化进公共 API」的风险，该前置条件已解除。
- **契约测试**：生成物不手写，但**必须有烟测**（每个 tag 至少一次 `withHttpInfo` 调用断言 HTTP 码），否则「能编译不能用」无人发现。

## 生成与烟测已落地（2026-10-03，第 11 / 14 项）

| 项 | 内容 |
|---|---|
| 生成脚本 | `scripts/generate-sdk.sh` —— 8 份规范 → `target/sdk/` 下 8 个独立 artifact（`java` generator + `okhttp-gson`，包名 `com.bone.sdk.<domain>.api/.model`）。**必须联网**（首次下载 cli + jar），故不挂进走 `mvn -o` 的离线流水线 |
| 生成器版本 | npm 包 `@openapitools/openapi-generator-cli@2.15.0` + 它下载的 **jar 7.14.0**，两者分别钉死 |
| 生成结果 | 8 个 artifact 全部成功，合计 **28 个 Api 类 / 37 个 model 类** |
| 烟测清单 | `scripts/sdk-contract-smoke-manifest.py` 从**规范真源**推导（不是从生成物推导，否则等于用生成物验证生成物），每个 tag 取参数最少的一个 operation |
| 烟测驱动器 | `scripts/sdk/SdkContractSmoke.java` —— 起本地 `HttpServer`，对每个 tag **真发一次 HTTP**并断言 HTTP 200 + `Authorization: Bearer` |
| 门禁 #21 | `scripts/check-openapi-generation-readiness.py` —— 把「规范可喂给生成器」变成静态约束；`check.sh [21/21]` + CI `OpenAPI Generation Readiness Lint (blocking)` |

### 落地过程中修掉的两个真实契约缺陷

**这两类缺陷在 `redocly lint` 与 `check-openapi-contract.py` 下全是绿的** —— 它们只管YAML 可解析 /认证声明 / `$ref` 可解析，但生成器会直接拒绝或产出不可用的代码：

| 缺陷 | 实测后果 | 处置 |
|---|---|---|
| `generator-v1.yaml` 的 `PUT`/`DELETE /data-sources/{id}` 未声明 path 参数 `id` | openapi-generator 抛 `SpecValidationException`，**整份规范生成失败**（不是警告） | 补2 处 path 参数声明 |
| `iam` / `extension` / `generator` 共 **90 个 operation 全部缺 `operationId` 与 `tags`** | 生成结果退化为单个 `DefaultApi`：`iam` 的 47 个方法名变成 `accountsIdGetPost` 这类 path 机械拼接，SDK 不可用 | 逐条补齐；现142 个 operation 全部有 `operationId` + `tags`，去重后无冲突；修复后 `iam` 变成 7 个按 tag 分类的 Api 类 |

补齐后 `redocly lint` 8/8 仍 EXIT=0（warnings 均为 `operation-2xx-response` 类的既有 warn 级规则）。

### 三处判据设计上踩的坑（都会造成假红或假绿）

1. **path 参数判据必须先解 `$ref`。** `metadata-runtime-v1.yaml` 有 5 处 operation 的参数是 `$ref` 到 `components/parameters`，不解 `$ref` 会被误判成"缺声明"。第一版判据没解，扫描结果是7 处缺失，实际只有 2 处真缺陷。
2. **包名片段不能在脚本里手工维护。** `generate-sdk.sh` 的 `SPECS` 里把 `metadata-runtime` 的包名片段多写了一个字母，烟测立刻以 `ClassNotFoundException` 失败；而 `ls` 与 `find` 对同一目录的存在性给出相反结果（`find`能找到 `.class`、`ls` 说目录不存在），只有逐字节打印 `len` + `hex` 才定位到是 16 字节 vs 15 字节的差别。**门禁 #21 现在核对"手工常量 == 从规范名推导的值"。**
3. **公开端点不能断言认证头。** `POST /login`、`GET /sso/callback` 在规范里显式声明 `security: []`，对它们要求 `Authorization: Bearer` 是把正确行为判成失败 ⇒ 清单用 `auth=none` 标记；这个标记必须是**调用级**的，一个全局开关表达不了"这批要、下批不要"。

### 烟测为什么必须真发 HTTP

只new 出 Api 类、核对方法名存在，是典型的**假绿**：okhttp 在 404/500 上也不抛异常，"能编译不能用"无人发现 —— 这正是 `bone-sdk` 空壳事故的本质。所以驱动器起真实 HTTP server，三条断言各自对应一类缺陷：请求计数递增（方法真把请求发出去了）、响应 200（响应解析可用）、`Authorization` 头存在（`securitySchemes` 真被接上）。

**双向验证**：撤掉 `setBasePath` ⇒ EXIT=1 且报"stub 一次都没收到请求 ⇒ 烟测是假绿"；撤掉 `setBearerToken` ⇒ EXIT=1 且精确报出 **25** 处（28 个 tag 减去 3 个公开端点），证明豁免与断言都真实生效而非形同虚设。

### 仍未完成

| # | 动作 | 阻塞原因 |
|---|---|---|
| 12 | 生成物接入 CI 并发布到制品库，把 oasdiff 结果作为生成的前置门禁 | 需要制品库坐标与发布凭据，属外部条件 |
| 15 | ~~分页收敛后再发 SDK，或在 SDK 中显式标注三套命名并存~~ **已解除（2026-10-06）**：存量收敛完成，见上文 | 依赖 API 规范 §5.2 裁定；实测口径见 `scripts/check-paging-param-names.py --report-only` |

**另有一处规范侧待办（本次未改）**：8 份规范的 `servers.url` 全是相对路径（如 `/api/v1/iam`），无 host。生成 SDK 的 `ApiClient` 默认 `basePath` 就是这个相对路径，**调用方必须在构造时显式给绝对地址**，否则运行时才抛 `Expected URL scheme 'http' or 'https' but no scheme was found`（烟测正是因此必须调 `setBasePath`）。规范补 `servers[0].url` 为绝对地址涉及部署域名，属产品裁定。

## 理由

- **为什么是生成而非手写**：142 个操作的手写 SDK 与规范脱节只是时间问题，且脱节后**没有任何机制能发现**——这正是本次空壳事故的本质（无人依赖、无人发现、长期存在）。生成则天然跟随。
- **为什么先移除再谈生成**：留着空壳会让"是否要实现 SDK"这个决策永远悬置——模块在，就总有人假设它可用。移除是**诚实的默认状态**：没有就是没有，不会误导任何人。
- **为什么保留门禁而非一并删除**：本次事故暴露的是"空壳会静默存在"这一模式，而非"这两个模块"本身。门禁的成本是一次 `rglob`，收益是同类问题今后立即变红。删掉门禁等于放弃这个防线。
- **为什么改扫描范围而不是让门禁自然失效**：`SDK_ROOTS` 若仍指向已删的 `bone-sdk`，门禁将永远返回 OK——这是最坏结果（**绿灯幻觉**：看起来有防护，实则形同虚设）。改盯真实 SDK 后它才重新具备防错能力。

## 后果

### 正面

- 模块树与依赖图恢复为事实：现在 5 个顶层模块（`bone-parent` / `bone-framework` / `bone-engine` / `bone-platform` / `bone-blueprint`），无占位。
- 构建产物干净：SDK jar 只剩两个**真实**且健康的（`bone-metadata-sdk` 217 类/46 测试、`bone-extension-sdk` 89 类/49 测试）。
- 门禁从"装饰"变为"有效"：已用负向验证确认两条规则都会真红（掏空测试目录 → EXIT=1；模块只剩 IDE 模板类 → EXIT=1）。
- 对外发布能力并未丢失：OpenAPI 规范 + oasdiff 阻断 + redocly 校验（`struct` / `no-unresolved-refs` 为 `error`）是更完整的契约资产。

### 负面 / 风险

- **短期内没有 Java 客户端 SDK**：若外部集成方现在就需要 Java SDK，需先批准本 ADR 并启动生成流程。这是显式的成本，比"以为有、实际没有"好。
- **新增"规范漂移"这一类风险**：生成 SDK 后，Java/Python/TS 客户端都由规范派生，规范写错会**同时**传播到所有客户端。缓解：规范变更过 oasdiff（`breaking` 阻断）+ redocly 结构校验。
- **门禁已是真正的 CI 阻断**（2026-10-03 升级，原"仅本地 Manual"的表述已过时）：`.github/workflows/ci.yml` 的 `backend-quality` job 第 13 步 `SDK Contract Surface Lint (blocking)` 已调用 `check-sdk-contract-surface.py --check`，`gate-state.json` 门禁 #16 状态同步改为Active 并经 `generate` 重渲染。已用负向探针确认：删掉该workflow 调用后自检 `EXIT=1` 并明确指出"只能算 Manual"。

## 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| A. 保留 `bone-sdk` 并逐步实现 | 空壳会长期存在，期间持续误导读者（本次已实证：无依赖、无测试、无发现，任其存在等于把"看起来有"固化下来） |
| B. 立即手写完整 Java SDK | 142 个操作手写成本高且必然与规范脱节；脱节后无检测机制，重演本次事故 |
| C. 直接删除 `check-sdk-contract-surface.py` | 放弃"空壳静默存在"的防线。本次事故证明该风险真实（`bone-sdk` 空转了约一年无人发现） |
| D. 保留 `bone-sdk` 但改为 packaging=pom 聚合真实 SDK | 等价于本 ADR 第 1 步 + 换个目录名，收益仅是"看起来整齐"，反而多一层无意义间接 |

## 合规与迁移

**已完成（2026-10-03）**

| # | 动作 | 状态 |
|---|------|------|
| 1 | 根 `pom.xml` 移除 `<module>bone-sdk</module>` | ✅ |
| 2 | `git rm -r bone-sdk`（5 个跟踪文件 + 残留 `target/`） | ✅ |
| 3 | `docker/Dockerfile` 删除 `COPY bone-sdk/pom.xml bone-sdk/`（**唯一会让构建直接失败**的引用点） | ✅ |
| 4 | `scripts/ci-local.sh`、`CONTRIBUTING.md` 的 spotless `-pl` 列表移除 | ✅ |
| 5 | 3 个门禁脚本扫描根移除（`check-ddd-gate-state.py` / `check-tenant-entity-declaration.py` / `ci/check-ddd-doc-code-sync.py`） | ✅ |
| 6 | `README.md` 两处「API/SDK 分发」→「标准 REST API 分发」 | ✅ |
| 7 | `doc/agents/01`、`doc/wiki/02`、`scripts/prompts/02-nightly-design-review.md` 模块清单同步 | ✅ |
| 8 | `check-sdk-contract-surface.py` 扫描范围改为两个真 SDK；基线 `placeholder_modules` 清空为 `[]` | ✅ |
| 9 | `gate-state.json` 门禁 #16 补注变更，并跑 `check-ddd-gate-state.py generate` 重渲染 5 张表 | ✅ |

**验证**

- `mvn -o clean package -DskipTests` → **BUILD SUCCESS**，反应堆 52 个模块，日志内 `bone-sdk` 出现 **0** 次，jar 产物仅剩两个真 SDK
- 五道门禁 EXIT=0：sdk-contract-surface / gate-state / tenant（缺口仍 6 张 by-design，删扫描根未扩大缺口）/ ddl-required-columns / soft-delete-declaration
- 全仓残余 9 处 `bone-sdk` 字样**全为有意保留的历史说明**（门禁 docstring、基线注释、本 ADR），无一是活构建引用
- 逐文件 diff 均为 1~4 行精准改动，无意外扩散

**有意不改的一处**：`doc/design/BONE-X-Studio-详细设计方案.md` 中的 `| bone-sdk | core/metadata/extension-sdk | Apache 2.0 |` —— 那是**未来商业版仓库**的设计提案（含 core/metadata/extension-sdk），不是当前仓库清单。

**后续待执行（本 ADR 尚未涉及的改动）**

| # | 动作 | 门槛 |
|---|------|------|
| 10 | ~~选定对外 SDK 目标语言~~ → **已定 Java**（2026-10-03） | ✅ 已完成 |
| 10a | ~~解决 71 处 `int64` 与后端 `Long`→String 的契约不一致~~ → **已完成**（2026-10-03 架构组批准，见「A2 已完成」章节） | ✅ 已完成。**实测 71 处非同质**：60 处包装 `Long` 改为 `type: string`；11 处 `console-v1` 指标为原生 `long`/`double`（序列化器不生效）**保持 `int64` 正确**。门禁 #19 |
| 10b | ~~补齐 5 份规范的 `securitySchemes`~~ → **已完成**（2026-10-03，按规范 §9.1「默认需认证」补 `bearerAuth` + 顶层 `security`） | ✅ 已完成。门禁 #18；`redocly.yaml` 的 `security-defined` 同时由 `off` 改为 `error` |
| 10c | **契约文件的机器可解析性**（3 份规范因 flow mapping 值未加引号而 YAML 解析失败） | ✅ 已完成（2026-10-03）。9 处加引号 + 1 处 `summary: '@Capability'`；门禁 #18 |
| 11 | 接入 openapi-generator（`java` / `okhttp-gson`），定包名规范 | ✅ **已完成**（2026-10-03）。`scripts/generate-sdk.sh`，8 个 artifact 全部生成成功；顺带修掉 2 处 path 参数缺失 + 90 个 operation 缺 `operationId`/`tags`；新建门禁 #21 |
| 12 | 生成物接入 CI（发布到制品库），并把 oasdiff 结果作为生成的前置门禁 | 待办：需制品库坐标与发布凭据（外部条件）。生成脚本已就绪，接 CI 只是加一个 job |
| 13 | 把 `check-sdk-contract-surface.py` 升级为 CI 阻断 | ✅ **已完成**（2026-10-03，`.github/workflows/ci.yml` 的 `backend-quality` job `SDK Contract Surface Lint (blocking)`） |
| 14 | 为生成物补契约烟测（每个 tag 至少一次 `withHttpInfo` 断言 HTTP 码） | ✅ **已完成**（2026-10-03）。`scripts/sdk-contract-smoke-test.sh` + `scripts/sdk/SdkContractSmoke.java`，28 个 tag 全部真发 HTTP 往返 200 且带 Bearer 认证 |
| 15 | ~~收敛分页命名后再发 SDK~~ **已解除（2026-10-06）**：存量收敛完成 | 依赖 API 规范 §5.2 裁定；实测口径见 `scripts/check-paging-param-names.py --report-only` |
