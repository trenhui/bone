# ADR-0022：外部回调验签端口化（由支付样板推广为全局硬规则）

| 项 | 内容 |
|----|------|
| **状态** | 提议 |
| **日期** | 2026-08-30 |
| **决策者** | 平台架构组（待评审） |
| **关联** | [Bone-DDD-最终实践方案](../Bone-DDD-最终实践方案.md) §5.5（`:186` 回调验签必须）/ §19 防腐；`bone-blueprint` / `bone-security` / `bone-core`；change `ddd-spec-v4-5-convergence`（M3） |

---

## 背景

### 现状：验签是支付样板孤例，未下沉平台

- **唯一实现**：`bone-blueprint` 的 `PaymentSignaturePort`（`domain/gateway` 防腐端口）+ `SimulatedPaymentSignatureVerifier`（`infrastructure/gateway/payment`）。
  > **2026-09-19 补注（现状已变，本条留作当时快照）**：`PaymentSignaturePort` 现位于 `application/port/out`（按 E-10.2 属技术能力端口，不是 domain 业务网关），实现类为 `MockPaymentSignaturePortAdapter` 且落点从 `infrastructure/gateway/payment` 迁到 `infrastructure/signature`；类名 `SimulatedPaymentSignatureVerifier` 已不存在（占位实现统一 `Mock` 前缀，E-13.3）。下文 `:37` / `:31-32` 等行号与 `HandlePaymentCallbackCommandHandler` 同属旧快照，勿按行号定位。
- **算法**：HMAC-SHA256（`SimulatedPaymentSignatureVerifier.java:37`），常量时间比较 `MessageDigest.isEqual`（`:31-32`）。
- **密钥**：硬编码 `MOCK_SECRET = "bone-blueprint-mock-secret"`（`:22`，注释明确「仅演示，真实接入须外部化」）——**反模式**，且密钥仅存在于 infrastructure 层（domain 依赖端口、不依赖密钥，方向正确）。
- **签名放置**：请求 JSON body 的 `signature` 字段（非 HTTP header）。
- **验签失败处理**：抛 `BizException("支付回调签名校验失败")` 拒绝请求（`HandlePaymentCallbackCommandHandler.java:53-55`）。
- **样板缺口**：仅在 `cmd.success()==true`（成功回调）时验签；失败回调直接 `markFailed`，**不验签**（`:57-59`）。

### 平台现状缺口（复核实证）

1. **无共享验签能力**：`bone-core/util` 无任何加解密/验签工具；`bone-security` 提供的是**用户态 JWT 鉴权**（`JwtTokenService` / `AbstractJwtAuthenticationFilter`），针对「已登录用户 Bearer Token」，**对「外部系统回调报文验签」这一无需用户态、需报文体验签的场景无任何统一抽象**；`bone-extension-engine` 的 `ExtensionSignatureVerifier`（RSA）验证对象是扩展代码来源 JAR，与回调验签无关。
2. **网关/框架层无回调统一处理**：`JwtAuthGlobalFilter` 只做用户态 JWT；且**支付回调不经网关**（网关路由不含 blueprint），blueprint 自身无 Security 配置——回调安全**完全依赖应用内验签**。
3. **潜在风险点**：`bone-masterdata` 的 `SecurityConfig` 将 `/api/**` 全部 `permitAll`（无鉴权）；IAM `/api/v1/iam/sso/callback` 被 permitAll 但返回 501 未实现、且无验签约定。未来任何模块新增入站回调端点，均无现成的验签约定可循。

主规范 §5.5（`:186`）已定义「**回调验签（安全，必须）**：真实渠道必须验签（HMAC/RSA/证书）；经防腐层在 Handler 进入领域前验签，签名不可信抛 `BizException`；真实接入要求密钥外部化、加防重放（nonce/时间戳）、按渠道用证书」。现状与该约定的差距——孤例、不可复用、硬编码密钥、失败回调不验签——即本 ADR 要解决的。

## 决策

在**共享平台层**（建议 `bone-security`，已有密码学/签名基础设施；或评审决定放 `bone-core/security`）定义「外部回调验签」统一抽象，并作为全局硬规则推广：

### 1. 端口定义

```java
/** 外部系统回调报文验签端口（防腐层契约） */
public interface CallbackSignatureVerifier {
  /** 验签：报文要素（规范化拼接串或原始报文）、签名、密钥标识 */
  boolean verify(CallbackSignatureContext context);
}
```

- `CallbackSignatureContext` 携带：算法类型（`HMAC_SHA256` / `RSA_SHA256` 等）、报文要素、签名原文、密钥标识（非密钥明文）。
- 实现：HMAC 实现（常量时间比较，强制 `MessageDigest.isEqual`）+ RSA 实现（可扩展）；实现细节参考支付样板 `SimulatedPaymentSignatureVerifier` 的模式，但**密钥来源改为外部化**。

### 2. 密钥外部化（强制，禁止硬编码）

- 密钥经配置 / 密钥管理接入（环境变量、配置中心、KMS 适配），**禁止代码内硬编码密钥**；对照 `MOCK_SECRET` 反模式，新增 Checkstyle/CR 检查。
- 渠道级密钥路由：按渠道标识选择密钥（未来多渠道/多证书场景），端口内部处理，业务层不感知密钥细节。

### 3. 防重放（强安全场景必选）

- 时间戳 / nonce 校验作为端口能力提供；支付等资金类回调**必须启用**；其余场景按风险等级启用（规范约定）。

### 4. 验签失败处理约定（硬规则）

- 验签失败**必须拒绝请求**（抛 `BizException` / 返回 4xx），**禁止静默忽略**。
- **所有回调（含失败回调）都须验签**——修正支付样板「仅成功回调验签」的缺口。

### 5. 端口化推广为全局硬规则

- **接收外部回调的模块必须接入该端口**：在 Handler 进入领域前验签（防腐层语义，与 §5.5 一致）。
- 落地方式：规范登记 + CR 检查清单为主；ArchUnit 辅助（若提炼出可靠模式：回调端点类名含 `Callback` 且所在包须引用验签端口）。
- 与用户态 JWT 鉴权**正交**：回调端口化不替代 JWT；两者可叠加（回调端点既可验签又可要求用户态，视场景）。

### 6. 样板迁移

- bone-blueprint：`PaymentSignaturePort` 替换为平台端口适配（或直接基于平台端口实现）；`SimulatedPaymentSignatureVerifier` 的硬编码密钥外部化；失败回调补验签。

## 理由

- **孤例不可复用**：验签逻辑封装在 blueprint 内，integration / masterdata / iam 未来回调端点无现成约定，必然各自实现或裸奔（对照 masterdata `/api/**` permitAll 的前车）。
- **硬编码密钥是真实反模式**：`MOCK_SECRET` 仅为演示，但「样板即规范」的传播效应会让真实接入也硬编码。
- **失败回调不验签是样板缺口**：失败回调同样是外部输入，跳过验签等于开放伪造「取消/失败」通道。
- **端口化让「验签必须」可复用、可强制**，且复用 bone-security 已有密码学基础设施，成本低。
- **§5.5 已定义契约**（防腐层验签、不可信抛 BizException、密钥外部化、防重放），本 ADR 将其从「样板」升级为「平台能力 + 硬规则」。

## 后果

### 正面

- 统一验签实现 / 密钥管理 / 防重放能力，新回调端点零成本接入。
- 修复支付样板两个缺口：失败回调不验签、硬编码密钥。
- 「回调必须验签」从文档约定变为可强制、可复用的平台能力。
- masterdata / iam 未来回调端点有现成安全基线，避免重蹈「全 permitAll」覆辙。

### 负面 / 风险

- **端口归属与包名待评审**（`bone-security` vs `bone-core`）。
- **样板迁移回归**：blueprint 验签路径变更需全量测试（136 个）通过。
- **「外部系统」边界需澄清**：第三方渠道回调 vs 平台内部模块间调用（后者可能走内部鉴权而非报文验签），需在规范中定义判定规则。
- **防重放引入时间戳依赖**：时钟偏移 / 重试回调超时场景需定义窗口（可配）。
- 端口化可能触发对**既有端点的安全审查**（如 IAM SSO callback 未实现即无验签约定），属正面暴露而非风险扩大。

## 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| A. 维持支付样板孤例 + 文档约定 | 无法复用、无强制；硬编码密钥反模式随样板传播；失败回调不验签缺口延续 |
| B. 仅下沉算法工具类到 `bone-core/util`（静态方法） | 只解决算法复用，不解决密钥管理、防重放、失败处理约定——策略仍需端口抽象；工具类可作组件内部实现细节保留 |
| C. 统一在网关层验签 | 支付回调不经网关（blueprint 独立运行），网关统一验签无法覆盖；且不同渠道密钥路由在网关做不现实（业务语义泄漏到网关） |
| D. 仅要求 IAM 处理回调 | 只覆盖 IAM 一个模块，其他模块回调端点仍无约定；不符合「全局硬规则」目标 |

## 合规与迁移

1. **端口定义**：`bone-security` 新增 `CallbackSignatureVerifier` / `CallbackSignatureContext` + HMAC/RSA 实现 + 密钥外部化接入 + 自测（正向、篡改、防重放）。
2. **样板迁移（bone-blueprint 先行）**：`PaymentSignaturePort` → 平台端口；密钥外部化；失败回调补验签；136 测试全绿。
3. **新端点硬规则**：接收外部回调的模块必须接入端口并在 Handler 进入领域前验签；规范登记 + CR 检查清单（ArchUnit 辅助按评审结论决定）。
4. **规范同步**：主规范 §5.5 外置为样板文档（任务 1.14）时，原则部分保留「回调必须验签」硬约束，并引用本 ADR；§19 防腐补「外部回调验签端口」。

**回滚**：端口与实现无强制耦合（样板可回退到应用内端口适配）；密钥外部化后回滚需还原配置项，无代码破坏。
