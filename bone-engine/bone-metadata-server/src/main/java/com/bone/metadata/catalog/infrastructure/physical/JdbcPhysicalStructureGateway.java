package com.bone.metadata.catalog.infrastructure.physical;

import com.bone.core.exception.DomainException;
import com.bone.metadata.catalog.domain.gateway.PhysicalStructureGateway;
import com.bone.metadata.catalog.domain.model.MetaEntity;
import com.bone.metadata.catalog.domain.model.MetaField;
import com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MVP-11 物理结构对齐网关 —— JDBC 实现（MySQL / H2-MySQL 模式）。
 *
 * <p>职责：① inspect：按 catalog 模型 diff 物理库（纯只读，读 information_schema）； ② align：执行 CREATE TABLE / ADD
 * COLUMN（幂等，结构一致时 no-op）。
 *
 * <p>安全边界：只做「缺表建 / 缺列加」的非破坏性对齐；不改类型、不删列、不缩长度。 所有表名 / 列名均经 {@link #requireIdentifier(String,
 * String)} 校验，杜绝 SQL 注入。
 */
public class JdbcPhysicalStructureGateway implements PhysicalStructureGateway {

  private static final Pattern IDENT = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");

  private final JdbcTemplate jdbcTemplate;
  private final MetaEntityRepository metaEntityRepository;
  private final MetaFieldRepository metaFieldRepository;

  public JdbcPhysicalStructureGateway(
      JdbcTemplate jdbcTemplate,
      MetaEntityRepository metaEntityRepository,
      MetaFieldRepository metaFieldRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.metaEntityRepository = metaEntityRepository;
    this.metaFieldRepository = metaFieldRepository;
  }

  @Override
  public PhysicalStructurePlan inspect(long tenantId, String entityCode) {
    return diff(tenantId, entityCode, false);
  }

  @Override
  public PhysicalStructurePlan align(long tenantId, String entityCode) {
    return diff(tenantId, entityCode, true);
  }

  private PhysicalStructurePlan diff(long tenantId, String entityCode, boolean execute) {
    MetaEntity entity = requireEntity(tenantId, entityCode);
    List<MetaField> fields =
        metaFieldRepository.where(MetaField::getEntityId).eq(entity.getId()).list();
    String table = requireIdentifier(entity.getTableName(), "tableName");
    boolean exists = tableExists(table);
    List<String> statements = new ArrayList<>();
    if (!exists) {
      statements.add(buildCreateTable(table, fields));
    } else {
      Set<String> existing = readExistingColumns(table);
      for (MetaField field : fields) {
        String col = requireIdentifier(field.getCode(), "field.code");
        if (!existing.contains(col)) {
          statements.add(buildAddColumn(table, field));
        }
      }
    }
    int executed = 0;
    if (execute) {
      for (String ddl : statements) {
        jdbcTemplate.execute(ddl);
        executed++;
      }
    }
    return toPlan(entity.getCode(), table, statements, executed, !exists, execute);
  }

  private MetaEntity requireEntity(long tenantId, String entityCode) {
    return metaEntityRepository
        .where(MetaEntity::getTenantId)
        .eq(tenantId)
        .and(MetaEntity::getCode)
        .eq(entityCode)
        .singleOpt()
        .orElseThrow(() -> new DomainException("元数据实体不存在: " + entityCode));
  }

  private boolean tableExists(String table) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = ?",
            Integer.class,
            table);
    return count != null && count > 0;
  }

  private Set<String> readExistingColumns(String table) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT COLUMN_NAME FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ?",
            table);
    Set<String> cols = new LinkedHashSet<>();
    for (Map<String, Object> row : rows) {
      Object name = row.get("COLUMN_NAME");
      if (name != null) {
        cols.add(name.toString());
      }
    }
    return cols;
  }

  private String buildCreateTable(String table, List<MetaField> fields) {
    StringBuilder sb = new StringBuilder();
    sb.append("CREATE TABLE `").append(table).append("` (");
    sb.append("`id` BIGINT NOT NULL");
    sb.append(", `tenant_id` BIGINT NOT NULL");
    boolean hasIdField = false;
    for (MetaField field : fields) {
      String col = requireIdentifier(field.getCode(), "field.code");
      if ("id".equals(col)) {
        hasIdField = true;
        continue;
      }
      sb.append(", `").append(col).append("` ").append(toSqlType(field));
      if (Boolean.TRUE.equals(field.getRequired())) {
        sb.append(" NOT NULL");
      }
    }
    sb.append(", `version` INT NOT NULL DEFAULT 0");
    sb.append(", `deleted` SMALLINT NOT NULL DEFAULT 0");
    if (!hasIdField) {
      sb.append(", PRIMARY KEY (`id`)");
    }
    sb.append(")");
    return sb.toString();
  }

  private String buildAddColumn(String table, MetaField field) {
    String col = requireIdentifier(field.getCode(), "field.code");
    // 加列不破坏既有数据：不允许 NOT NULL（避免存量行约束失败）
    return "ALTER TABLE `" + table + "` ADD COLUMN `" + col + "` " + toSqlType(field);
  }

  private String toSqlType(MetaField field) {
    String type = field.getType() == null ? "" : field.getType().toUpperCase();
    Integer length = field.getLength();
    switch (type) {
      case "LONG":
      case "BIGINT":
        return "BIGINT";
      case "INT":
      case "INTEGER":
        return "INT";
      case "BOOLEAN":
      case "BOOL":
        return "TINYINT(1)";
      case "DATE":
        return "DATE";
      case "DATETIME":
      case "TIMESTAMP":
        return "DATETIME";
      case "TIME":
        return "TIME";
      case "DECIMAL":
      case "NUMBER":
      case "DOUBLE":
      case "FLOAT":
        return "DECIMAL(20,6)";
      case "TEXT":
      case "JSON":
        return "TEXT";
      case "STRING":
      case "VARCHAR":
      case "EMAIL":
      case "URL":
      default:
        return length != null && length > 0 ? "VARCHAR(" + length + ")" : "VARCHAR(255)";
    }
  }

  private String requireIdentifier(String value, String what) {
    if (value == null || !IDENT.matcher(value).matches()) {
      throw new DomainException("非法标识符 " + what + " = " + value);
    }
    return value;
  }

  private PhysicalStructurePlan toPlan(
      String entityCode,
      String tableName,
      List<String> statements,
      int executed,
      boolean created,
      boolean execute) {
    String status;
    String message;
    if (!execute) {
      if (statements.isEmpty()) {
        status = PhysicalStructurePlan.STATUS_READY;
        message = "物理结构与模型一致";
      } else {
        status = PhysicalStructurePlan.STATUS_DRIFT_DETECTED;
        message = "检测到结构漂移，待执行 " + statements.size() + " 条 DDL";
      }
    } else if (created) {
      status = PhysicalStructurePlan.STATUS_CREATED;
      message = "已创建物理表，执行 " + executed + " 条 DDL";
    } else if (statements.isEmpty()) {
      status = PhysicalStructurePlan.STATUS_READY;
      message = "物理结构与模型一致，无需变更";
    } else {
      status = PhysicalStructurePlan.STATUS_ALIGNED;
      message = "已对齐，执行 " + executed + " 条 ADD COLUMN";
    }
    return new PhysicalStructurePlan(
        entityCode, tableName, created, statements, executed, status, message);
  }
}
