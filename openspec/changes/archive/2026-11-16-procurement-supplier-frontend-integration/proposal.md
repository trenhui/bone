# Proposal

## Why

采购供应商主数据切片的后端已交付 5 个治理端点并通过完整 Spring Boot + JWT + 内存端口的真实 HTTP 联调测试（登记 → Vendor 闸门 → Supplier 准入 → 退出全链路），但前端 `bone-masterdata-app` **零改动**：用户无法从界面登记供应商、查看双角色状态或推进状态机，后端契约从未被真实前端消费验证。缺这一层，"procuraAI 前后端功能联调"始终停留在后端自测，无法证明契约在浏览器侧可用，也无法暴露 `ApiResponse` / `PageResult` 序列化细节（Long→String、`records` 字段）在真实前端的适配问题。

本 change 在不越 R-2/L3/L4 红线（不建 Maven 模块、不写 DDL、不接生产库、不改 CI 判据）的前提下，补齐供应商治理前端并完成真实前后端联调，把已交付的后端能力变成用户可操作、可验证的闭环。

## What Changes

- **前端 API 客户端**：在 `bone-masterdata-app` 新增 `supplierApi`，封装登记、双角色状态迁移、只读详情与分页列表 4 类调用；统一处理 `ApiResponse<T>` 顶层错误字段与 `PageResult` 的 `records` / `total`（Long→String）归一化。
- **前端类型**：新增供应商 DTO 类型（登记结果、状态视图、迁移结果、分页查询），不回传/不展示 USCC、税号、电话、法人名等 PII；未知状态码显示原始码，不改写成"正常"。
- **供应商治理页面**：新增 `SupplierManagement` 页面，提供登记表单、双角色状态分区展示（Supplier 合作资格 / Vendor 交易付款资格）、允许的迁移事件触发、历史摘要时间线、租户/权限失败提示；菜单与路由接入"采购治理"分组。
- **能力开关降级**：当后端 `bone.procurement.supplier-mdm.enabled=false`（端点不注册）时，页面显示"能力未启用"的空态而非报错或空白，避免把 fail-closed 误呈现为前端故障。
- **前端契约测试**：用 vitest + Testing Library 校验 API 客户端的请求形状（路径、body、分页参数）与响应归一化，以及页面的状态呈现、失败提示与 PII 不展示。
- **真实前后端联调**：新增可复现脚本，以 `bone.procurement.supplier-mdm.enabled=true` + 测试端口装配启动后端，前端构建产物经 dev proxy 连真实后端，跑通"登记 → Vendor 闸门 → Supplier 准入 → 退出"全链路，并留存 HTTP 与页面证据。
- **安全优化（不越红线）**：核对并修正前端对统一响应错误、权限 403、租户 fail-closed 的呈现口径；不修改任何后端领域规则、不推翻已批准裁决。

**BREAKING**：无。本 change 只新增前端能力，不改后端 API 契约与既有 capability 需求。

## Capabilities

### New Capabilities

- `procurement/supplier-mdm-console`: 采购供应商主数据的 Web 治理控制台行为——登记、双角色状态与历史的只读呈现、显式迁移事件触发、统一响应与能力开关的可观察降级、PII 边界与权限失败提示。

### Modified Capabilities

（无。后端 `procurement/supplier-mdm` 的需求与场景不变，本 change 只新增前端可观察行为能力。）

## Impact

- **代码**：`bone-frontend/apps/bone-masterdata-app` 新增 `services/supplierApi.ts`、`types/supplier.ts`、`pages/SupplierManagement.tsx` 及对应测试；`App.tsx` 增加路由、`Layout.tsx` 增加菜单；可能新增联调脚本到 `scripts/`（只新增联调用脚本，不改既有门禁判据集合）。
- **API**：消费既有 `/api/v1/masterdata/suppliers` 五个端点，不改后端签名。
- **依赖**：不新增第三方依赖（复用 axios / antd / @bone/ui / vitest / Testing Library）。
- **门禁**：前端 `vitest` + `tsc --noEmit` + `eslint -f json` 全绿；后端模块 `clean test` 复绿；不修改 `scripts/check.sh` / CI 判据集合与 procuraAI 文档族真源。
- **红线声明**：DDL / 生产 JDBC adapter（A-2/A-8，L3）、生产库迁移（L4）、CI 三入口判据、既有已裁决偏差（A-1/A-3/A-6/A-7/A-9/A-10）均不在本 change 范围，继续列为 owner 前置，不在本次"完成"宣称内。
