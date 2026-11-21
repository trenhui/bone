# P0 核心代码优化实现计划：上下文生命周期与统一 JSON 数值序列化

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除 `TenantInterceptor` 热路径反射，基于强类型 SPI 补齐请求上下文生命周期（防御性清理、异步释放、异常隔离与对称清理），并统一 `JsonUtil` 与 Spring MVC 的高精度数值（`Long`/`BigInteger`）字符串序列化契约。

**Architecture:** 
1. 在 `bone-core` 建立 `RequestContextLifecycle` SPI，在 `bone-web` 改造 `TenantInterceptor` 为实现 `AsyncHandlerInterceptor` 并通过 Spring `ObjectProvider` 弱依赖注入 SPI；
2. 在 `bone-extension-sdk` 实现 `ExtensionRequestContextLifecycle`，通过 `ExtensionAutoConfiguration` 自动装配，对接 `ExtensionContextManager`；
3. 在 `MetadataAutoConfiguration` 和 `JsonUtil` 中统一配置不可变数值序列化模块，将 `Long` 和 `BigInteger` 转为 String 输出；
4. 建立包含单线程池线程复用、异步分派释放和反序列化兼容性的自动化测试。

**Tech Stack:** Java 21, Spring Boot 3.5, Jackson (Jackson2ObjectMapperBuilderCustomizer, ToStringSerializer), JUnit 5, AssertJ.

## Global Constraints
- Java 源码改动后必须运行 `mvn spotless:apply` 符合工程规范。
- 遵循架构分层规范：`domain` 零外部依赖，`bone-web` 不得直接引入 `bone-extension-engine` 依赖。
- 不破坏向后兼容性：`TenantInterceptor` 保留无参构造器，确保手写 `new TenantInterceptor()` 与单元测试不被破坏。

---

### Task 1: 建立 `RequestContextLifecycle` SPI 接口

**Files:**
- Create: `bone-framework/bone-core/src/main/java/com/bone/core/context/RequestContextLifecycle.java`

**Interfaces:**
- Produces: `com.bone.core.context.RequestContextLifecycle` 接口，定义 `initialize(String tenantId, String bizCode)` 与 `clear()`。

- [x] **Step 1: 创建 `RequestContextLifecycle.java`**

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
   * @param bizCode 业务场景标识
   */
  void initialize(String tenantId, String bizCode);

  /** 清理当前线程的上下文。必须保证幂等性与异常安全性。 */
  void clear();
}
```

- [x] **Step 2: 验证编译**
Run: `mvn -f bone-framework/bone-core/pom.xml compile`
Ensure code compiles with 0 errors.

---

### Task 2: 统一 JSON 数值序列化契约（`JsonUtil` & `MetadataAutoConfiguration`）

**Files:**
- Modify: `bone-framework/bone-core/src/main/java/com/bone/core/util/JsonUtil.java`
- Modify: `bone-engine/bone-metadata-sdk/src/main/java/com/bone/metadata/sdk/support/config/MetadataAutoConfiguration.java`
- Create: `bone-framework/bone-core/src/test/java/com/bone/core/util/JsonUtilNumberSerializationTest.java`

**Interfaces:**
- Consumes: Jackson `SimpleModule`, `ToStringSerializer`.
- Produces: 统一数值序列化，确保 `Long`、`long`、`BigInteger` 输出为 JSON String，`Integer`、`BigDecimal` 维持原始数值。

- [x] **Step 1: 编写数值序列化失败测试 `JsonUtilNumberSerializationTest.java`**
覆盖大整数（大于 $2^{53}-1$）、普通整数、BigDecimal、对象嵌套与反序列化测试。

- [x] **Step 2: 运行测试验证失败**
Run: `mvn -f bone-framework/bone-core/pom.xml test -Dtest=JsonUtilNumberSerializationTest`
Expected: 失败（当前 `JsonUtil` 未注册 `Long`/`BigInteger` 为 String）。

- [x] **Step 3: 改造 `JsonUtil.java` 的 `JacksonUtils`**
在不可变静态块中注册 `ToStringSerializer` 处理 `Long.class`, `Long.TYPE`, `BigInteger.class`。

- [x] **Step 4: 改造 `MetadataAutoConfiguration.java`**
在 `boneLongToStringCustomizer` 中追加 `.serializerByType(BigInteger.class, ToStringSerializer.instance)`。

- [x] **Step 5: 重新运行单测并验证通过**
Run: `mvn -f bone-framework/bone-core/pom.xml test -Dtest=JsonUtilNumberSerializationTest`
Ensure tests pass.

---

### Task 3: 改造 `TenantInterceptor` 补齐生命周期并实现异步支持

**Files:**
- Create: `bone-framework/bone-web/src/test/java/com/bone/core/web/interceptor/TenantInterceptorTest.java`
- Modify: `bone-framework/bone-web/src/main/java/com/bone/core/web/interceptor/TenantInterceptor.java`

**Interfaces:**
- Consumes: `com.bone.core.context.RequestContextLifecycle`, `com.bone.core.tenant.context.TenantContext`.
- Produces: `TenantInterceptor implements AsyncHandlerInterceptor`，提供完整的生命周期管理。

- [x] **Step 1: 编写 `TenantInterceptorTest.java` 单元测试**
覆盖用例：
1. 正常请求初始化与清理；
2. 缺失 Header 时的 fail-closed 语义；
3. 线程池复用测试（使用单线程 ExecutorService 验证连续不同租户请求不串用）；
4. 入口防御性清理测试；
5. 异步调度释放测试（`afterConcurrentHandlingStarted`）；
6. 容灾测试（外部 SPI 抛异常时，`TenantContext.clear()` 依然在 finally 中执行）；
7. 无 SPI 注入时的独立运行兼容性。

- [x] **Step 2: 运行单测验证失败**
Run: `mvn -f bone-framework/bone-web/pom.xml test -Dtest=TenantInterceptorTest`
Expected: 失败。

- [x] **Step 3: 改造 `TenantInterceptor.java`**
1. 实现 `AsyncHandlerInterceptor`；
2. 注入 `ObjectProvider<RequestContextLifecycle>` 并提供默认无参构造器；
3. 移除每次请求的 `Class.forName` 和 `getMethod` 反射；
4. 在 `preHandle` 首行执行 `clearContext()` 防御性清理；
5. 在 `afterConcurrentHandlingStarted` 和 `afterCompletion` 执行 `clearContext()`；
6. `clearContext()` 采用 `try-finally` 保证 `TenantContext.clear()` 必定执行。

- [x] **Step 4: 重新运行单测验证通过**
Run: `mvn -f bone-framework/bone-web/pom.xml test -Dtest=TenantInterceptorTest`
Ensure all tests pass.

---

### Task 4: 实现 `ExtensionRequestContextLifecycle` 并自动装配

**Files:**
- Create: `bone-engine/bone-extension-engine/bone-extension-sdk/src/main/java/com/bone/engine/extension/support/context/ExtensionRequestContextLifecycle.java`
- Modify: `bone-engine/bone-extension-engine/bone-extension-sdk/src/main/java/com/bone/engine/extension/support/config/ExtensionAutoConfiguration.java`
- Create: `bone-engine/bone-extension-engine/bone-extension-sdk/src/test/java/com/bone/engine/extension/support/context/ExtensionRequestContextLifecycleTest.java`

**Interfaces:**
- Consumes: `RequestContextLifecycle`, `ExtensionContextManager`.
- Produces: 自动装配的扩展引擎请求上下文生命周期实现。

- [x] **Step 1: 编写 `ExtensionRequestContextLifecycleTest.java`**
验证 `initialize` 正确驱动 `ExtensionContextManager.with(tenant, biz)`，`clear` 正确调用 `clearContext()`。

- [x] **Step 2: 创建 `ExtensionRequestContextLifecycle.java`**
实现 `RequestContextLifecycle`，调用 `ExtensionContextManager`。

- [x] **Step 3: 在 `ExtensionAutoConfiguration.java` 注册 Bean**
添加 `@Bean @ConditionalOnMissingBean public RequestContextLifecycle extensionRequestContextLifecycle()`.

- [x] **Step 4: 运行单测验证通过**
Run: `mvn -f bone-engine/bone-extension-engine/bone-extension-sdk/pom.xml test -Dtest=ExtensionRequestContextLifecycleTest`
Ensure all tests pass.

---

### Task 5: 格式化与工程门禁自检

**Files:**
- All modified and new files.

- [x] **Step 1: 运行 Spotless 格式化**
Run: `mvn spotless:apply`

- [x] **Step 2: 运行变更模块的单元测试**
Run:
`mvn -f bone-framework/bone-core/pom.xml test`
`mvn -f bone-framework/bone-web/pom.xml test`
`mvn -f bone-engine/bone-metadata-sdk/pom.xml test`
`mvn -f bone-engine/bone-extension-engine/bone-extension-sdk/pom.xml test`

- [x] **Step 3: 运行全仓架构门禁检查**
Run: `./scripts/check.sh`
Ensure 0 failures across all gate checks.
