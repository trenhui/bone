# bone-extension-engine（ExtPoint 扩展引擎）

Bone 四大引擎之一：在**不修改核心代码**的前提下，通过标准化扩展点注入个性化业务逻辑（开闭原则）。与根目录 README「ExtPoint 扩展引擎」叙事一致。

## 文档（请按角色阅读）

| 文档 | 读者 | 内容 |
|------|------|------|
| [bone-extension-sdk/README.md](./bone-extension-sdk/README.md) | 业务/平台开发 | 注解、路由、`BizContext`、示例与 FAQ（篇幅较长，作参考手册） |
| [docs/使用指南.md](./docs/使用指南.md) | 接入与运维 | **推荐路径**：依赖、`@EnableExtensionPoints`、五分钟上手、配置与排错 |
| [docs/README.md](./docs/README.md) | — | 文档索引 |
| [doc/design/modules/5. 扩展管理模块详细设计方案.md](../../doc/design/modules/5.%20扩展管理模块详细设计方案.md) | 产品/架构 | 详设 **v2.1**（As-Is / [Target] / [Vision] 分层） |
| [Bone-DDD 最终实践方案](../../doc/architecture/Bone-DDD-最终实践方案.md) | 全栈开发 | 分层与持久化 P0（扩展实现仍须符合平台规约） |

**事实来源**：`bone-extension-sdk/src/main/java`、测试包 `com.bone.example.extension`；勿在仓库中恢复已删除的长篇「设计方案」副本。

## 架构：数据面 + 控制面

| 模块 | 角色 | 默认端口（开发） |
|------|------|------------------|
| **bone-extension-sdk** | 运行时：扩展点注册、代理调用、路由（租户/业务/场景/SpEL）、类加载隔离、事件与指标 | 嵌入业务进程 |
| **bone-extension-studio** | 控制面：扩展点/扩展实现（控制台称插件）、JAR 制品与路由发布 | **8088**（`BONE_EXTENSION_STUDIO_PORT`） |
| **bone-extension-app**（前端） | 扩展管理微应用 | **3008** |

## 核心能力（摘要）

1. **扩展点契约**：`@ExtensionPoint` 接口 + `@Extension` 实现；业务侧注入接口调用，框架代理路由。
2. **上下文路由**：`BizContext`（租户、业务域、场景等）+ 精确匹配 / SpEL / 默认实现；支持优先级与缓存。
3. **生命周期**：插件打包部署、启停、灰度与卸载；与 Studio 协同（以当前实现为准）。
4. **隔离与治理**：独立类加载、依赖冲突检测思路、异常隔离与降级。
5. **可观测**：调用次数/耗时等指标、链路追踪集成点、结构化日志（见 SDK 配置）。
6. **动态配置**：扩展参数外部化（如 Nacos），按租户/插件维度生效。

## 运行时流程（概念）

```mermaid
flowchart LR
  Biz[业务代码] --> Ctx[BizContext]
  Ctx --> Proxy[扩展点代理]
  Proxy --> Router[ExtPointRouter]
  Router --> Impl[@Extension 实现]
  Impl --> Biz
```

1. 启动：`@EnableExtensionPoints` 扫描扩展点与实现并注册。  
2. 调用：绑定 `BizContext` → 注入扩展点接口 → 代理选实现并执行。  
3. 治理：Studio / 配置变更驱动启停与路由规则更新（无需改核心代码）。

## 典型场景

| 场景 | 做法 |
|------|------|
| 电商促销 / 订单计价 | 订单链路上定义扩展点；按租户、渠道、会员等级路由不同 `@Extension` |
| 政务差异化审批 | 以 `tenantCode` 区分部门插件，动态启停审批节点扩展 |
| 多租户 SaaS 定制 | 租户级插件绑定；默认实现 + 租户覆盖 |

## 与平台其他引擎

| 引擎 | 协同方式 |
|------|----------|
| 智能元数据 | 扩展点参数与实体模型对齐；控制台展示扩展元数据 |
| 主数据 | 扩展逻辑中校验主数据编码与质量 |
| 集成 | 插件经集成连接器访问外部系统（在隔离边界内） |

## 构建

```bash
# SDK 单元测试
mvn -pl bone-engine/bone-extension-engine/bone-extension-sdk test

# Studio 本地运行（profile 见模块 resources）
mvn -pl bone-engine/bone-extension-engine/bone-extension-studio -am spring-boot:run
```

## 参考实现

- 订单扩展示例：[bone-blueprint](../../bone-blueprint/)（价格计算等扩展点）
- SDK 测试示例：`bone-extension-sdk/src/test/java/com/bone/example/extension/`
