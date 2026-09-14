package com.bone.metadata.sdk.extension.handler;

import com.bone.metadata.sdk.domain.enums.DatabaseType;
import com.bone.metadata.sdk.domain.enums.ExtensionMode;
import com.bone.metadata.sdk.extension.ExtensionContext;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

/** EAV模式扩展字段处理器。 */
public class EavHandler implements ExtensionStorageHandler {
  private final NamedParameterJdbcOperations jdbc;

  public EavHandler(NamedParameterJdbcOperations jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ExtensionMode getMode() {
    return ExtensionMode.EAV;
  }

  @Override
  public void save(ExtensionContext context) {
    String sql = upsertSqlFor(MetadataSdkContext.getDatabaseType());
    List<Map<String, Object>> batchParams =
        context.getExtraProperties().entrySet().stream()
            .map(
                entry ->
                    Map.of(
                        "entityType", context.getEntityType(),
                        "entityId", context.getEntityId(),
                        "attrKey", entry.getKey(),
                        "attrValue", String.valueOf(entry.getValue())))
            .toList();
    @SuppressWarnings({"unchecked", "rawtypes"})
    Map<String, ?>[] paramsArray = batchParams.toArray(new Map[0]);
    jdbc.batchUpdate(sql, paramsArray);
  }

  /** 按方言构建 upsert SQL（对齐 JsonHandler，覆盖全部 5 类方言）。 */
  public static String upsertSqlFor(DatabaseType databaseType) {
    return switch (databaseType) {
      case MYSQL,
          DM,
          OceanBase -> """
          INSERT INTO ext_data_eav (entity_type, entity_id, attr_key, attr_value, deleted)
          VALUES (:entityType, :entityId, :attrKey, :attrValue, FALSE)
          ON DUPLICATE KEY UPDATE attr_value = :attrValue, deleted = FALSE
          """;
      case POSTGRESQL -> """
          INSERT INTO ext_data_eav (entity_type, entity_id, attr_key, attr_value, deleted)
          VALUES (:entityType, :entityId, :attrKey, :attrValue, FALSE)
          ON CONFLICT (entity_type, entity_id, attr_key)
          DO UPDATE SET attr_value = :attrValue, deleted = FALSE
          """;
      case H2 -> """
          MERGE INTO ext_data_eav (entity_type, entity_id, attr_key, attr_value, deleted)
          KEY (entity_type, entity_id, attr_key)
          VALUES (:entityType, :entityId, :attrKey, :attrValue, FALSE)
          """;
      case ORACLE -> """
          MERGE INTO ext_data_eav t
          USING (SELECT :entityType AS entity_type, :entityId AS entity_id, :attrKey AS attr_key FROM dual) s
          ON (t.entity_type = s.entity_type AND t.entity_id = s.entity_id AND t.attr_key = s.attr_key)
          WHEN MATCHED THEN
            UPDATE SET t.attr_value = :attrValue, t.deleted = 0
          WHEN NOT MATCHED THEN
            INSERT (entity_type, entity_id, attr_key, attr_value, deleted)
            VALUES (:entityType, :entityId, :attrKey, :attrValue, 0)
          """;
      case SQLSERVER -> """
          MERGE ext_data_eav AS target
          USING (SELECT :entityType AS entity_type, :entityId AS entity_id, :attrKey AS attr_key) AS source
          ON (target.entity_type = source.entity_type AND target.entity_id = source.entity_id AND target.attr_key = source.attr_key)
          WHEN MATCHED THEN
            UPDATE SET target.attr_value = :attrValue, target.deleted = 0
          WHEN NOT MATCHED THEN
            INSERT (entity_type, entity_id, attr_key, attr_value, deleted)
            VALUES (:entityType, :entityId, :attrKey, :attrValue, 0)
          """;
    };
  }

  @Override
  public Map<String, Object> load(ExtensionContext context) {
    String sql =
        """
            SELECT attr_key, attr_value
            FROM ext_data_eav
            WHERE entity_type = :entityType AND entity_id = :entityId AND deleted = FALSE
            """;
    Map<String, Object> params =
        Map.of(
            "entityType", context.getEntityType(),
            "entityId", context.getEntityId());
    return jdbc.query(
        sql,
        params,
        rs -> {
          Map<String, Object> result = new HashMap<>();
          while (rs.next()) {
            result.put(rs.getString("attr_key"), rs.getString("attr_value"));
          }
          return result;
        });
  }
}
