# bone-extension-studio

扩展引擎管理台：扩展点 / 扩展实现 CRUD，插件版本与执行日志，以及运行时路由元数据推送。

## 持久化

| Profile / `bone.extension.studio.persistence.mode` | 说明 |
|--------------------------------------------------|------|
| **`in-memory`**（默认，`spring.profiles.default=in-memory`） | 内存 Store，单进程联调，不加载 Metadata 自动配置 |
| **`metadata`** | Bone Metadata SDK + H2 文件库，落表扩展点/插件/版本/执行日志 |

### 内存模式（默认联调）

```bash
cd bone-extension-studio
mvn spring-boot:run -Dmaven.test.skip=true
# 或显式：-Dspring-boot.run.profiles=in-memory
```

### Metadata 模式（H2 文件持久化）

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=metadata -Dmaven.test.skip=true
```

数据目录：`./data/extension-studio-metadata`（可在 `application-metadata.yml` 调整）。

### 生产 MySQL

1. 执行仓库根目录 [`bone-init.sql`](../../../bone-init.sql)（含 `ext_studio_*` 四表，见 [数据库开发规范](../../../doc/architecture/数据库开发规范.md)）
2. 配置数据源并激活 `metadata` profile：

```bash
export BONE_DB_URL=jdbc:mysql://localhost:3306/bone
mvn spring-boot:run -Dspring-boot.run.profiles=metadata
```

DDL 脚本：

- H2：`src/main/resources/db/schema-h2.sql`
- MySQL：`src/main/resources/db/schema-mysql.sql`

## 运行时 SDK 执行日志上报（可选）

业务应用启用扩展 SDK 后，可配置将调用结果异步上报 Studio：

```yaml
bone:
  extension:
    studio:
      report:
        enabled: true
        base-url: http://localhost:8088
```

上报接口：`POST /api/extension/execution-logs/ingest`（按插件 `className` 匹配）。

## 运行时同步

见 [`../docs/使用指南.md`](../docs/使用指南.md) §4.6。
