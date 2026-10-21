# ADR-0043：枚举选项源选型 — 分层真源 + 单向引用 + 发布物化（`meta_field.enum_source`）

| 项 | 内容 |
|----|------|
| **状态** | **已接受 / 已实现**（2026-10-20 实现 + 联调实证，2026-10-21 全量终验后归档） |
| **日期** | 2026-10-21 |
| **决策者** | 架构（追述登记：实现经 5 个 commit 落地并双远程同步，特补本记录闭环「落地前需补 ADR」的承诺） |
| **关联** | [11. 枚举选项源统一设计方案](../design/modules/11.%20枚举选项源统一设计方案.md)、[界面设计器字典打通设计方案](../design/lowcode/ui/界面设计器字典打通设计方案.md)（v1.1 十项优化）、[7a. 数据字典模块详细设计方案](../design/modules/7a.%20数据字典模块详细设计方案.md)（v3）、[0001](./0001-database-ddl-single-source.md)（DDL 单轨） |
| **下游同步** | `bone-init.sql`（`meta_field.enum_source` 列）、`scripts/migration/0050_meta_field_enum_source.sql`（存量库幂等补列）、`Bone-错误码登记.md`（`META_ENUM_SOURCE_*` / `META_MIGRATE_*` / `SYS_DICT_ITEM_*`）、`bone-metadata-server` catalog 包（`EnumSourceResolver` 端口 + `HttpDictEnumSourceResolver` + `EnumSourceApplicationService`）、`bone-metadata-app`（`FieldWizardModal` / `DictionaryPanel`） |

---

## 背景

界面设计器的字典面板此前只能引用 `meta_field.enum_values` —— 一份字段私有的 JSON 文本。三个断裂：

1. **重复建设**：平台字典体系（`sys_dict_*`：值域定义、层级、多语言译文、租户覆盖、生效窗口、禁删保护、漂移检测）已成熟，界面侧完全绕开它；
2. **口径分裂**：同一个「性别 / 状态 / 币种」在系统字典、主数据参考值域、`enumValues` 三处各存一份，改一处不生效另两处——「配了不生效」是平台最高发的正确性债务类型；
3. **外部值域无入口**：组织架构、供应商、客户等外部权威码表只能手工抄录。

问题被呈现为「界面设计器字典配置是否应与系统字典/主数据/外部接口打通」，需要选型的不是「是否打通」，而是**打通成什么形态**。

## 决策

**「分层真源 + 单向引用 + 统一解析 + 发布物化」**，而非三方合并：

1. **四层真源分层**（按治理强度）：L0 实体私有枚举（`enumValues`，永久合法形态）/ L1 平台字典（`sys_dict`，共享码表真源）/ L2 主数据参考值域（`mdm_reference_*`）/ L3 外部同步。各层职责唯一，不合并成一个字典库；
2. **单向引用**：字段经 `meta_field.enum_source` 声明真源（`{"type":"dict","mode":"snapshot","ref":{"typeCode":X}}`），UI 只引用 code，不复制、不新建（守住界面协议 S7「码表真源永不在 UI 层新建」）；
3. **发布物化**：保存/发布时实时解析真源 `/options` 并覆写 `enum_values`——把真源「编译」成运行契约，运行时渲染与写校验**零改动**；
4. **一致性**：快照 + 值域业务版本号协商 + stale-while-revalidate（RFC 5861 实践），不引跨服务 MQ（现网只有进程内事件总线）；
5. **悬空绑定 fail-closed**：真源不可达时解析失败即拒（`META_ENUM_SOURCE_RESOLVE_FAILED`），不接受「绑定成功但选项为空」。

### 备选方案与否决理由

| 方案 | 否决理由 |
|---|---|
| 三方合并成单一字典库 | 主数据值域有 steward/血缘/质量治理、外部码表权威在外部，合并即制造第四个半成品真源；违反单真源 |
| UI 层直接新建字典项 | 界面协议 S7 明令禁止；参照完整性无从保证；业界（SAP Domain / Salesforce Global Picklist / Dynamics Option Set）无此实践 |
| 运行期 live 直连（不物化） | 字典服务不可用即拖垮表单渲染；写校验集合随时间漂移不确定。采用 SWR 变体：版本协商 + 陈旧兜底，改字典 30s 内自动到达，无需重新发布实体 |
| 物理外键（SAP value table 式 check table） | 动态/跨租户值域落外键即锁死运营；写校验在应用层（`RuntimeWriteValidator`）已足够 |

## 后果

**正向**：界面侧即刻获得平台字典的治理能力（版本、多语言、审计、漂移检测）；存量 `enumValues` 行为逐字节不变（灰度开关关闭时回到 L0）；运营改字典不再需要改元数据模型 + 重新发布。

**代价**：`meta_field` 增一列 JSON（DDL 走 L3，迁移 0050 幂等）；物化使「保存即依赖字典服务可达」——以 fail-closed 显式暴露而非静默降级；漂移需要刷新动作（漂移徽标 + 版本协商轮询自动兜底）。

**范围纪律**：只做绑定、解析、快照、告警四件事；值域治理复用现有模块（不造「字典引擎」）；一致性用 HTTP 缓存标准实践而非新建 MQ。

## 验证证据（2026-10-21 终验，会话内复跑）

- 后端单测 38/38（`EnumSourceApplicationServiceTest` 23 / `DictBindingToggleTest` 8 / `EnumSourceMigrationTest` 7）；
- E2E 探针：`probe-dict-v11-e2e.py` 19/19、`probe-dict-v11-version.py` 10/10、`probe-dict-v11-governance.py` 29/29；
- UI 探针 `probe-dict-v11-ui.cjs` 50/50 且 console error 0（经 shell 真实登录）；
- 手动联调闭环：字段向导 ENUM→dict 绑定→预览 A/B→创建即物化（`resolvedBy=save`）→漂移徽标→手刷/版本协商自动刷新→服务端版本一致、diff 空。
