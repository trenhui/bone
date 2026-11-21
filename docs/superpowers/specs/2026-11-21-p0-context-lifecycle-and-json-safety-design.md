# P0 核心代码优化设计规范：请求上下文生命周期与统一 JSON 数值序列化

## 1. 目标与背景
针对 Bone 平台在热路径反射、上下文生命周期闭环以及高精度数值 JSON 序列化方面的技术隐患，基于业界最佳实践（DDD、Spring SPI、IEEE 754 安全性、线程池安全）进行针对性 P0 级别代码重构。

- **消除热路径反射查找与无效计算**：`TenantInterceptor` 摆脱每请求反射，通过底层强类型 SPI 解耦扩展上下文；
- **补齐上下文生命周期**：入口防御性清理、异步释放、异常隔离与 `finally` 对称清理；
- **统一 JSON 数值序列化规则**：统一 Spring MVC 与 `JsonUtil`，对 `Long`/`BigInteger` 进行安全字符串转换，彻底规避前端大整数精度截断。

## 2. 详细技术方案

### 2.1 强类型 SPI 契约：`RequestContextLifecycle`
- **模块位置**：`bone-framework/bone-core`
- **包路径**：`com.bone.core.context`
- **接口契约**：
```java
package com.bone.core.context;

/**
 * 请求级上下文生命周期 SPI 契约。
 *
 * <p>用于解耦底层入站拦截器（Web/RPC）与上层各引擎（扩展引擎、审计、链路等）的线程级业务上下文绑定。
 */
public interface RequestContextLifecycle {

  /**
   * 初始化当前线程的请求上下文。
   *
   * @param tenantId 租户唯一标识
   * @param bizCode  业务场景标识
   */
  void initialize(String tenantId, String bizCode);

  /**
   * 清理当前线程的上下文。必须保证幂等性与异常安全性。
   */
  void clear();
}
```

### 2.2 上下文生命周期闭环：`TenantInterceptor` 改造
- **模块位置**：`bone-framework/bone-web`
- **包路径**：`com.bone.core.web.interceptor`
- **核心逻辑**：
  1. 实现 `AsyncHandlerInterceptor`，支持 Spring MVC 异步请求。
  2. 构造器注入 `ObjectProvider<RequestContextLifecycle>`，同时保留无参构造器确保向后兼容。
  3. `preHandle`：先执行 `clearContext()` 防御性冲刷线程池残留上下文；再根据 `X-Tenant-Id` 初始化 `TenantContext`，并通过 SPI 初始化各生命周期实现。
  4. `afterConcurrentHandlingStarted`：异步开始时及时清理当前 Servlet 线程的 ThreadLocal。
  5. `afterCompletion`：请求结束执行 `clearContext()`。
  6. `clearContext()` 幂等清理：在 `finally` 块中强制清理 `TenantContext`，外部 SPI 异常记录日志不中断主链路清理。

### 2.3 业务扩展引擎实现：`ExtensionRequestContextLifecycle`
- **模块位置**：`bone-engine/bone-extension-engine/bone-extension-sdk`
- **包路径**：`com.bone.engine.extension.support.context`
- **核心逻辑**：
  1. 实现 `RequestContextLifecycle`。
  2. `initialize(tenantId, bizCode)`：调用 `ExtensionContextManager.with(tenantId, bizCode)`。
  3. `clear()`：调用 `ExtensionContextManager.clearContext()`。
  4. 在 `ExtensionAutoConfiguration` 中完成 Spring Bean 注册。

### 2.4 统一 JSON 数值序列化规范
- **类型映射契约**：
  - `Long` / `long` -> String
  - `BigInteger` -> String
  - `Integer` / `int` -> Number
  - `BigDecimal` -> Number
  - `Double` / `float` -> Number
- **落地模块 1**：`bone-engine/bone-metadata-sdk`
  - 修改 `MetadataAutoConfiguration.boneLongToStringCustomizer`，增加 `.serializerByType(BigInteger.class, ToStringSerializer.instance)`。
- **落地模块 2**：`bone-framework/bone-core`
  - 修改 `JsonUtil.JacksonUtils`，在初始化时通过不可变模块注册 `Long.class`, `Long.TYPE`, `BigInteger.class` 的 `ToStringSerializer.instance`，保持单例线程安全且与 Spring MVC 契约完全一致。

## 3. 测试与验证策略
- **单元测试**：
  - `TenantInterceptorTest`：
    - 正常请求初始化与清理验证；
    - 线程池单线程复用测试（使用 `Executors.newSingleThreadExecutor()` 断言两次请求间不串用上下文）；
    - 入口防御性清理测试；
    - Spring MVC 异步请求释放测试（`afterConcurrentHandlingStarted`）；
    - 异常隔离测试（Mock SPI 抛出异常，断言 `TenantContext` 仍被成功清理）；
    - 无扩展引擎独立运行兼容性测试。
  - `JsonSerializationTest`：
    - 大整数精度断言（大于 $2^{53}-1$）；
    - 对象属性、List 列表、Map Key/Value 序列化断言；
    - `JsonUtil` 与 Spring `ObjectMapper` 行为一致性断言；
    - 反序列化字符串转数字无损解析。
- **门禁验证**：
  - `mvn spotless:apply` 代码格式化；
  - `./scripts/check.sh` 全流程架构检查与单元测试验证。

## 4. 架构评审意见处理记录
针对评审提出的三条核心问题与补充建议，逐条处理结果如下（2026-11-21）：

| 评审建议 | 处理 | 落地说明 |
|---|---|---|
| 用强类型 SPI + Optional 注入替代 MethodHandle | **已采纳** | 建立 `com.bone.core.context.RequestContextLifecycle` SPI；`TenantInterceptor` 通过 `ObjectProvider` 弱依赖；移除全部反射/MethodHandle；bone-web 不依赖 extension-sdk。 |
| 返回值为 `ExtensionScope` 不可丢弃 | **已采纳** | `ExtensionRequestContextLifecycle` 用 `ThreadLocal<ExtensionScope>` 保存 `with()` 返回值，`clear()` 走 `scope.close()` 恢复旧上下文，不再另起 `clearContext()` 两套机制。判别用例 `testClearRestoresOuterContextViaScopeClose` 验证；变异（改回丢弃 scope）实测变红。 |
| 入口防御性清理 | **已采纳** | `preHandle` 首行 `clearContext()`。 |
| 异常/初始化失败清理 | **已采纳** | `preHandle` 初始化包 try-catch：失败时 `clearContext()` 后向上抛异常 fail-closed（不静默降级）。因 Spring `applyPreHandle` 在 `preHandle` 抛错时不回调本拦截器 `afterCompletion`，必须就地清理，否则 ThreadLocal 泄漏。判别用例 `testInitializeFailureCleansContextAndPropagates`。 |
| 异步请求 `afterConcurrentHandlingStarted` 清理 | **已采纳** | `TenantInterceptor implements AsyncHandlerInterceptor`，异步分派时释放原 Servlet 线程上下文。 |
| 清理幂等 + finally 保证 TenantContext 清理 | **已采纳** | `clearContext()` 以 try-finally 包裹，SPI 清理异常逐个捕获记日志，不影响 `TenantContext.clear()`。 |
| BigInteger 序列化统一 Spring Jackson 与 JsonUtil | **已采纳** | `MetadataAutoConfiguration` customizer 与 `JsonUtil` 均登记 `Long`/`long`/`BigInteger` → String；新增跨模块一致性测试，变异（漏登记 BigInteger）实测变红。 |
| BigInteger Map Key 单独验证 | **已采纳** | `testBigIntegerAsMapKeyKeepsPrecision` 实证 key 精度无损且可反序列化。 |
| JsonUtil 行为变更的持久化兼容性回归 | **已采纳** | 排查全仓 `JsonUtil.toJson` 唯一生产用途为 `ext_data_json` 落库；新增 `testDeserializeLegacyNumericLongStillWorks` 证明历史 JSON number 格式旧数据仍可无损读回。 |
| 可选模块装配规范（禁组件扫描绕过开关） | **已采纳** | 移除 `ExtensionRequestContextLifecycle` 的 `@Component`，仅经 `ExtensionAutoConfiguration` 的 `@Bean @ConditionalOnMissingBean` 装配，尊重 `bone.extension.enabled` 开关。 |
| X-Tenant-Id 不能直接作为可信身份来源，须结合认证主体校验 | **本期不实施（登记为后续技术债）** | 仓库当前无任何租户授权校验组件与可依赖的认证主体来源。引入租户级授权属于认证授权体系建设，超出本期三个 P0 目标（热路径反射/上下文生命周期/JSON 精度）范围；按评审"不扩大本期 P0 范围、不顺手重构整个上下文体系"原则，仅在此明确登记，待认证授权基础设施就绪后单独治理。 |

### 判别力验证
遵循"全绿≠有判别力"原则，对两条核心断言做了变异实测：
1. 移除 `MetadataAutoConfiguration` 的 BigInteger 登记 → 一致性判别用例变红；
2. 将 `ExtensionRequestContextLifecycle.clear()` 改回"丢弃 Scope + clearContext()" → Scope 恢复判别用例变红。
两次变异均已还原源文件并强制重编译验证全绿。
