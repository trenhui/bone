# HC-004 待审批修复草案（diff draft）

> 状态：**DRAFT，未落盘**。源码未改动。审批后执行 `git apply doc/design/_pending-fixes-HC004.patch`。
> 校验：`git apply --check` 已通过（待人工/L3 最终审批）。

## 背景（来自 `_global-contracts.yaml`）
- `bone-engine/bone-metadata-server/.../AuthController.java:34` 硬编码 `passwordEncoder.encode("password")` 演示凭据 → `hc_coverage.HC-004 = violated`。
- `bone-platform/bone-gateway/.../GatewayJwtProperties.java:21-22` 内嵌 `DEFAULT_SECRET` / `DEV_FALLBACK_SECRET` 兜底密钥 → `hc_coverage.HC-004 = violated`。

## 修复 A：删除 metadata-server 演示登录端点
整文件删除 `AuthController.java`（仅暴露 `/v1/auth/login` 演示端点，全仓无 import、无测试引用）。
- 前端 `authService.ts:23` 的 `/api/v1/auth/login` 调用的是 **bone-iam** 登录端点（网关白名单 `/api/v1/iam/auth/login`），与 metadata-server 的 `/v1/auth` 路径不同，删除不影响主鉴权链路。
- 风险：若开发期确有依赖 metadata-server 本地签发 JWT 的流程，删除后将无法本地登录该服务。替代方案（未采用）：保留端点但改为 `throw new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED)` 并移除硬编码凭据。如需此替代，请告知。

## 修复 B：移除 gateway 兜底 JWT 密钥
删除 `DEFAULT_SECRET` / `DEV_FALLBACK_SECRET` 两个常量及其引用；默认密钥检测收紧为 `startsWith("change-me")` + `contains("dev-only")`。
- 效果：任何未注入真实密钥（`BONE_IAM_JWT_SECRET_KEY` / `BONE_JWT_SECRET`）的启动都会 `validate()` 期 fail-fast 拒启（prod 与非 prod 一致收紧）。本地 dev 需确保已注入 ≥32 字节密钥。
- 原 `if (isDefault) log.warn(...)` 分支随常量移除而失效——因为已无任何内嵌默认密钥可落入该分支；保留 `change-me`/`dev-only` 字面串检测以拦截误用。

## 回滚
`git apply -R doc/design/_pending-fixes-HC004.patch`

## 建议落地顺序
1. 审批（你 / 架构师，L3）。
2. `git apply doc/design/_pending-fixes-HC004.patch`。
3. `mvn -q -pl bone-gateway,bone-metadata-server -am compile` 确认编译通过。
4. 将这两处从 `_global-contracts.yaml` 的 `hc_coverage.HC-004 = violated` 改为 `implemented` 并移除对应 warning（下一轮锚定刷新时一并处理）。
