# bone-extension-studio

扩展引擎管理台：扩展点 / 扩展实现 CRUD，以及运行时路由元数据推送。

## 持久化

| `bone.extension.studio.persistence.mode` | 说明 |
|----------------------------------------|------|
| **`metadata`**（默认） | Bone Metadata SDK + 表 `ext_studio_extension_point`、`ext_studio_extension_impl` |
| `in-memory` | 内存 Store，单进程联调 |

### 本地 H2（默认）

```yaml
bone.extension.studio.persistence.mode: metadata
spring.sql.init.schema-locations: classpath:db/schema-h2.sql
```

### 生产 MySQL

```bash
# 先执行 doc 同目录 schema-mysql.sql 或 classpath:db/schema-mysql.sql
export BONE_DB_URL=jdbc:mysql://localhost:3306/bone
spring.profiles.active=prod
```

DDL 脚本：

- H2：`src/main/resources/db/schema-h2.sql`
- MySQL：`src/main/resources/db/schema-mysql.sql`

## 运行时同步

见 [`../docs/使用指南.md`](../docs/使用指南.md) §4.6。
