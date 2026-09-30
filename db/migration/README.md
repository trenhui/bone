# 数据库迁移版本化（Flyway 接入）— DRAFT，待架构师审批 (L3: DDL)

## 现状（差距）
`bone-init.sql` 头部注释明确写道：

> 内部开发库可 DROP DATABASE 后全量重建，**无增量迁移**

这意味着当前没有任何数据库变更版本管理：多环境（dev/test/staging/prod）之间、多人协作、回滚、灰度都缺乏可控手段。这是上生产的 P0 阻断项。

## 目标
引入 **Flyway**，把 `bone-init.sql` 的全量 schema 作为 **V1 基线**，后续所有变更以 `V2__*.sql`、`V3__*.sql` 增量交付，支持可重复校验与失败回滚。

## 接入步骤
1. **依赖 / 插件**（在需要建表的 Spring Boot 服务 `pom.xml` 增加，属 L3 依赖变更，需审批）：
   ```xml
   <dependency>
     <groupId>org.flywaydb</groupId>
     <artifactId>flyway-core</artifactId>
   </dependency>
   <dependency>
     <groupId>org.flywaydb</groupId>
     <artifactId>flyway-mysql</artifactId>
   </dependency>
   ```
2. **配置**（`application.yml`，沿用现有环境变量注入方式，不硬编码）：
   ```yaml
   spring:
     flyway:
       enabled: true
       locations: classpath:db/migration
       baseline-on-migrate: true        # 首次在已有库上启用时打基线
       baseline-version: 1
       validate-on-migrate: true
   ```
3. **迁移脚本目录**：`src/main/resources/db/migration/`（本目录为方案说明；实际脚本按模块放入各自服务资源目录，或统一放在共享 SDK 由服务引用）。
4. **基线拆分**：把 `bone-init.sql` 按**模块**（iam / system / masterdata / integration / file / notification / blueprint / metadata / extension）拆分为若干 `V1_1__iam.sql` … `V1_N__*.sql`，或保留单 `V1__baseline.sql`。拆分时务必保持 `DROP TABLE IF EXISTS` 的幂等语义移除（Flyway 不重复执行已成功的版本）。

## 回滚策略
- Flyway 社区版不支持自动 `undo`，采用**补偿式迁移**：回滚写 `Vx__rollback_<reason>.sql`（反向 DDL），随版本一起评审入库。
- 生产数据变更（UPDATE/DELETE）必须先在 staging 用备份演练。

## 待办（需架构师/DBA 确认）
- [ ] registry 与多租户库（当前 iam/system/metadata/integration/extension 默认同库 `bone`）的迁移归属：单库统一迁移 vs 每服务独立 schema。
- [ ] 是否引入 Flyway Teams 的 `undo` / 干运行能力。
- [ ] `bone-init.sql` 最终归档为“本地开发一键重建脚本”，与生产 Flyway 流程解耦。

> 注：`V1__init_baseline.sql` 为占位骨架，正式 DDL 拆分需由 DBA + 架构师评审后落地（DDL 属 L3）。
