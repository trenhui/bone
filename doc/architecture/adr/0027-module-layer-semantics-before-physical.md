# ADR-0027：模块层级语义先立、物理结构后收

| 项 | 内容 |
|----|------|
| **状态** | 已接受 |
| **日期** | 2026-09-14 |
| **决策者** | 架构师批准 |
| **关联** | [BONE 总体架构设计方案 §4.1.1](../BONE-总体架构设计方案.md#411-模块层级归属与依赖规则kernel--framework--engine--platform)、[ADR-0023](./0023-core-domain-smart-metadata.md)、[Bone-DDD CORE-09/CORE-10](../Bone-DDD-最终实践方案.md) |

---

## 背景

外部架构评审指出 Bone 存在「逻辑核心域与物理模块边界不一致」的风险：Engine / Platform 的分类标准不统一（如 SDK 挂在 engine 下却被 platform 当底座、studio/server 是可部署应用却混在 engine 中），长期会导致模块分类持续漂移、依赖方向失控。

仓库现状核对（2026-09-14）：

1. `bone-metadata-sdk` / `bone-extension-sdk` 仅依赖 `bone-core`，被 `bone-platform/*` 全部消费——物理位置（engine）与角色（底座）不一致。
2. `bone-metadata-server` / `bone-extension-studio` / `studio-generator` 是可独立部署应用，物理位于 `bone-engine`。
3. `bone-integration` 的 Camel 编译与 Flow 执行能力以平台服务形态交付（已收敛为唯一集成服务）。
4. 依赖方向本身健康：无 engine → platform 反向依赖。

## 决策

### 1. 建立四层语义，不建新物理模块

| 层级 | 定义 | 当前归属 |
|------|------|----------|
| Kernel | 跨模块共享契约与抽象 | `bone-core`、各模块 `api` / `contract` 包、共享内核白名单 |
| Framework | 横切基础能力 | `bone-framework/*` |
| Engine | 可嵌入运行时引擎 | `bone-engine/*` |
| Platform | 可独立部署业务服务 | `bone-platform/*` |

依赖方向（强制）：`Platform → Engine(SDK 底座) → Framework → Kernel`；禁止 `Kernel → Platform`、`Engine → Platform`。

### 2. 规则先行，物理后收

- 立即：新增 ArchUnit 规则 `engineModulesMustNotDependOnPlatform`（ARCH-LEVEL-01）、`platformMustNotDependOnEngineApps`（ARCH-LEVEL-02），在 engine 侧（bone-metadata-server）与 platform 侧（bone-iam）架构测试启用。
- MVP 后（`release/mvp-v1.0` 之后）：SDK 底座迁出 engine、应用壳收敛至 Platform 层，见 `doc/wiki/07-P0-TODO看板.md` LYR-01/LYR-02。

### 3. 三处例外认可现状

1. SDK 底座规则上承认"被任意层依赖"地位，不因物理位置视为引擎应用。
2. 应用壳规则上按 Platform 层约束（不得被 platform 其他服务依赖、不得依赖 platform）。
3. Integration 的引擎能力以平台服务形态交付，不再作为独立 engine 演进。

### 4. 配套红线

同步确立 CORE-09（EAV 只承载扩展字段）与 CORE-10（Metadata 描述模型、不执行业务），防止 Metadata 膨胀为"第二个应用运行时"。

## 后果

- 新模块默认按四层语义归位；物理位置迁移作为独立重构立项，不阻塞 MVP 功能开发。
- ArchUnit 规则空匹配允许通过（`allowEmptyShould(true)`），在扫描到对侧包时实打实检查，避免模块测试范围限制导致误报。
- 存量违规（如 `CreateTenantCommandHandler` 直用 Criteria）通过 freeze 基线接受，登记 LYR-03，修复后收缩基线。
