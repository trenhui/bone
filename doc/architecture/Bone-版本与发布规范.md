# Bone 版本与发布规范

> **文档性质**：API 版本、应用 SemVer、数据库变更与发布顺序的**门禁**。  
> **更新**：2026-05-17  
> **关联**：[Bone-API-规范.md](./Bone-API-规范.md) §2.2、[数据库开发规范.md](./数据库开发规范.md)、[adr/](./adr/)

---

## 1. 版本体系

| 对象 | 策略 |
|------|------|
| 平台 Maven | `1.0.0`（`bone-parent`）；破坏性升 minor/major 按团队约定 |
| REST API | URL `/api/v1`；破坏性升 `v2` + Sunset |
| 消息 Topic | `.v1` → `.v2`（见 [消息规范](./Bone-消息与事件规范.md)） |
| OpenAPI | `info.version` 随服务发布递增 |
| 数据库 | **单轨** `bone-init.sql`（无 Flyway 增量，见 ADR-0001） |

---

## 2. API 变更（摘要）

| 类型 | 处理 |
|------|------|
| Additive（可选字段、新端点） | v1 内允许 |
| Breaking（删字段、改类型、改必填） | 新 `v2` 或 ADR；v1 标 `Deprecation` + `Sunset` |
| 错误码语义变更 | 视为 Breaking（[错误码登记](./Bone-错误码登记.md)） |

登记：[Bone-API-规范 §13.2](./Bone-API-规范.md#132-废弃与迁移)。

---

## 3. 发布顺序（推荐）

```
1. 合并 DDL 变更（bone-init.sql）→ 文档
2. 合并后端（兼容旧前端）
3. 部署后端
4. db-init / 重建（开发）或运维脚本（生产按 ADR）
5. 部署前端微应用
6. 验证契约测试 + 冒烟
```

**禁止**：先部署依赖新字段的前端，后部署后端。

---

## 4. 分支与 PR

| 项 | 规则 |
|----|------|
| 分支 | `feature/{name}@{owner}` / `fix/{desc}@{owner}` |
| PR | 通过 CI（见 [ci.yml](../../.github/workflows/ci.yml)）：Spotless、`mvn verify`、OWASP 依赖扫描、前端 lint/build |
| 提交 | `type(scope): description`（见根 `CLAUDE.md`） |

---

## 5. 功能开关发布

- 新功能默认 **关闭** 或仅 dev 开启  
- 生产开启须配置中心/环境变量 + 回滚预案  
- 见 [Bone-配置与环境规范](./Bone-配置与环境规范.md)  

---

## 6. SLO 与发布冻结

错误预算耗尽时（见 [可观测性规范](./Bone-可观测性规范.md)）：

- 仅允许 hotfix / 安全补丁  
- 大型功能推迟至下一窗口  

---

## 7. 回滚

| 层 | 回滚 |
|----|------|
| 应用 | 上一版本镜像/JAR |
| 数据库 | 单轨 init：**无自动回滚**；须 forward-fix SQL 或重建（开发） |
| 消息 | 新旧 consumer 兼容 `schemaVersion` |

---

## 8. 检查清单

- [ ] OpenAPI / §13.2 已更新  
- [ ] 无未登记 breaking  
- [ ] DDL 已入 `bone-init.sql`  
- [ ] 迁移说明在 PR 描述  
- [ ] 回滚步骤已写（若高风险）  

---

## 9. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-17 | 初版 |
