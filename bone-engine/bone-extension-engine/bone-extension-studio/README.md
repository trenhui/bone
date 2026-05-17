# bone-extension-studio

扩展引擎管理台：扩展点 / 扩展实现 CRUD，插件版本与执行日志，以及运行时路由元数据推送。

## 持久化

| Profile | `persistence.mode` | 数据源 | DDL |
|---------|-------------------|--------|-----|
| **`in-memory`**（默认） | `in-memory` | 无 | — |
| **`metadata`** | `metadata` | H2 文件 `./data/extension-studio-metadata` | `schema-h2.sql`（仅本地） |
| **`metadata-mysql`** / **`prod`** | `metadata` | MySQL `bone` 库（`BONE_DB_*`） | **[`bone-init.sql`](../../../bone-init.sql)** `exts_*`，**禁止** `spring.sql.init` |

### 内存模式（默认联调）

```bash
cd bone-extension-studio
mvn spring-boot:run -Dmaven.test.skip=true
```

### Metadata + H2（单机持久化）

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=metadata -Dmaven.test.skip=true
```

首次启动会执行 `db/schema-h2.sql`；换库时删除 `./data/extension-studio-metadata*`。

### Metadata + MySQL（与平台库一致）

```bash
# 仓库根目录
mysql -u root -p < bone-init.sql

source ../../../scripts/dev/load-env.sh   # 或 export BONE_DB_*
cd bone-extension-studio
mvn spring-boot:run -Dspring-boot.run.profiles=metadata-mysql -Dmaven.test.skip=true
```

`prod` profile 等价导入 `application-metadata-mysql.yml`，仅附加生产日志级别。

## API

- 路径：`/api/v1/extension/*`（见 [Bone-API-规范](../../../doc/architecture/Bone-API-规范.md) §13）
- 健康检查：`GET /actuator/health`

## 运行时 SDK 执行日志上报（可选）

```yaml
bone:
  extension:
    studio:
      report:
        enabled: true
        base-url: http://localhost:8088
```

上报：`POST /api/v1/extension/execution-logs/ingest`

## 运行时同步

见 [`../docs/使用指南.md`](../docs/使用指南.md) §4.6。
