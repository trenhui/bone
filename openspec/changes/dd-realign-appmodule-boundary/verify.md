# 验证报告：应用/模块领域边界重划分

- **Change**: `dd-realign-appmodule-boundary`
- **日期**: 2026-08-25
- **验证人**: AI（comet build + verify 监督）

## 1. 验证范围

本 change 将应用(App)/模块(Module)聚合根从 metadata 上下文彻底收敛到 IAM 上下文，最终边界：
**IAM = App + Module**；**metadata = Entity + Field + Relation**。元数据侧删除 `BoneApplication`/`BoneModule`
双聚合根（前端未使用的重复实现），新增指向 IAM 同库表的只读引用实体 `IamApplicationRef`/`IamModuleRef`
+ 存在性校验器，`MetaEntity.moduleId` 直接引用 IAM `bone_module`。

## 2. 已通过的静态与单元验证（强）

| 项 | 命令/证据 | 结果 |
|---|---|---|
| 后端 clean 全量编译 | `mvn -pl bone-engine/bone-metadata-server clean compile` | EXIT=0 |
| spotless 格式 | `mvn spotless:apply` 后无违规 | PASS |
| Validator 单元测试 | `IamApplicationValidatorTest` + `IamModuleValidatorTest`（6 用例：空/不存在/存在） | 全绿 TEST_EXIT=0 |
| 全模块测试（含 ArchUnit） | `mvn -pl bone-engine/bone-metadata-server test` | EXIT=0 |
| 引用清理 | 删除 metadata App/Module 全套 26 文件后，全仓库 grep `BoneModule/ModuleDTO/ModulePageQuery/CreateModuleCommand` NONE_REMAINING | PASS |

## 3. 前端契约核实

- 搜索 `bone-metadata-app/src` 对 `/metadata/apps`、`AppCatalog` 的引用：**0 匹配**。
- `ApplicationManagement.tsx` / `ModuleManagement.tsx` 仅依赖 `appApi` / `moduleApi`（路径 `/api/v1/apps`、`/apps/{id}/modules`，经网关到 IAM）。
- 修正 `appModuleApi.ts` 过时错误注释（"后端未实现"为虚假断言）。前端逻辑无需改动。

## 4. 全链路 E2E（已执行）

环境：MySQL(:3306)/Redis 就绪，metadata-server 用本次改动新 jar 启于 :9001（`java -jar bone-metadata-server-*.jar --spring.profiles.active=local`），鉴权 `Bearer bone-metadata-default-key`。

| # | 场景 | 请求 | 结果 |
|---|---|---|---|
| 1 | 负向：moduleId 不存在 | POST `/api/v1/metadata/entities` `{"displayName":"E2E负向","tableName":"e2e_neg","moduleId":999999}` | **HTTP 400** `所属模块不存在: 999999`（`META_BIZ_ERROR`） |
| 2 | 正向：moduleId 可选省略 | POST `/api/v1/metadata/entities` `{"displayName":"E2E正向","tableName":"e2e_pos"}` | **HTTP 201** 返回新实体 id `747351206136905728` |

**结论**：
- 负向用例证明 `CreateMetaEntityHandler` 接入的 `IamModuleValidator.requireExists(moduleId)` 运行时正确接线——直查 IAM 同库 `bone_module` 表，拒绝不存在的 moduleId，跨上下文引用一致性守卫生效。
- 正向用例证明删除 metadata 双聚合根（`BoneApplication`/`BoneModule`）后，`MetaEntity` 建模链路完全正常，`moduleId` 保持可选（兼容历史实体无 moduleId），领域边界收敛不破坏现有建模能力。
- 前端应用/模块管理早已指向 IAM（`/api/v1/apps`、`/apps/{id}/modules`），metadata 仅消费，无需改页面逻辑（已核实）。

## 5. 结论

后端收敛实现完整、编译与测试通过、领域边界清晰（IAM 独占 App+Module，metadata 纯建模）。
建议 E2E 通过后归档（archive-confirm）。
