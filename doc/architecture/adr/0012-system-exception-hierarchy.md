# ADR-0012：SystemException 归属基础设施异常链

| 项 | 内容 |
|----|------|
| **状态** | 已接受 |
| **日期** | 2026-05-21 |
| **决策者** | 架构组 |

---

## 背景

`SystemException` 曾继承 `BizException`，导致 `catch (BizException)` 误捕系统故障，与 §16.3「业务 vs 基础设施」语义冲突。

## 决策

`com.bone.core.exception.SystemException` 改为继承 `InfrastructureException`；`GlobalExceptionHandler` 增加 `SystemException` / `InfrastructureException` 处理器，映射 5xx。

## 理由

- 业务异常与系统故障在全局处理器中分流。
- 保留 `SystemException.of(...)` 静态工厂，减少调用方改动。

## 后果

### 正面

- `BizException` 仅表示用例级业务失败。

### 负面 / 风险

- 原依赖 `instanceof BizException` 捕获系统异常的代码需改为 `SystemException` 或 `InfrastructureException`。
- 各模块自建 `SystemException`（如 `bone-system`）不受影响。

## 合规与迁移

- 更新 DDD E-7.3 与 `bone-web` `GlobalExceptionHandler`。
- 模块级 `GlobalExceptionHandler` 若只处理 `BizException`，补充 `SystemException` 分支。
