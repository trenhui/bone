# 数据库变更

**唯一真源**：仓库根目录 [`bone-init.sql`](../../../../../bone-init.sql)  
**规范**：[`doc/architecture/数据库开发规范.md`](../../../../../doc/architecture/数据库开发规范.md)

本目录不再存放 Flyway 增量脚本。改表请直接修改 `bone-init.sql`，开发库重建：

```bash
mysql -u root -p -e "DROP DATABASE IF EXISTS bone;"
mysql -u root -p < bone-init.sql
```

`spring.flyway.enabled: false`
