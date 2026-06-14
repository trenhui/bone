package com.bone.metadata.sdk.extension.handler;

import com.bone.core.util.JsonUtil;
import com.bone.metadata.sdk.domain.enums.ExtensionMode;
import com.bone.metadata.sdk.extension.ExtensionContext;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

/** JSON模式扩展字段处理器。 */
@Slf4j
public class JsonHandler implements ExtensionStorageHandler {

  private final NamedParameterJdbcOperations jdbc;

  public JsonHandler(NamedParameterJdbcOperations jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ExtensionMode getMode() {
    return ExtensionMode.JSON;
  }

  @Override
  public void save(ExtensionContext context) {
    try {
      String json = JsonUtil.toJson(context.getExtraProperties());
      log.info(
          "Saving JSON for entity {}#{}: {}", context.getEntityType(), context.getEntityId(), json);

      Map<String, Object> params =
          Map.of(
              "entityType",
              context.getEntityType(),
              "entityId",
              context.getEntityId(),
              "dataJson",
              json,
              "deleted",
              false);

      String sql =
          switch (MetadataSdkContext.getDatabaseType()) {
            case MYSQL,
                DM,
                OceanBase -> """
                        INSERT INTO ext_data_json (entity_type, entity_id, data_json, deleted)
                        VALUES (:entityType, :entityId, :dataJson, :deleted)
                        ON DUPLICATE KEY UPDATE data_json = :dataJson, deleted = FALSE
                        """;
            case POSTGRESQL -> """
                        INSERT INTO ext_data_json (entity_type, entity_id, data_json, deleted)
                        VALUES (:entityType, :entityId, :dataJson, :deleted)
                        ON CONFLICT (entity_type, entity_id)
                        DO UPDATE SET data_json = :dataJson, deleted = FALSE
                        """;
            case H2 -> """
                        MERGE INTO ext_data_json (entity_type, entity_id, data_json, deleted)
                        KEY (entity_type, entity_id)
                        VALUES (:entityType, :entityId, :dataJson, :deleted)
                        """;
            case ORACLE -> """
                        MERGE INTO ext_data_json t
                        USING (SELECT :entityType AS entity_type, :entityId AS entity_id FROM dual) s
                        ON (t.entity_type = s.entity_type AND t.entity_id = s.entity_id)
                        WHEN MATCHED THEN
                          UPDATE SET data_json = :dataJson, deleted = 0
                        WHEN NOT MATCHED THEN
                          INSERT (entity_type, entity_id, data_json, deleted)
                          VALUES (:entityType, :entityId, :dataJson, 0)
                        """;
            case SQLSERVER -> """
                        MERGE ext_data_json AS target
                        USING (SELECT :entityType AS entity_type, :entityId AS entity_id) AS source
                        ON (target.entity_type = source.entity_type AND target.entity_id = source.entity_id)
                        WHEN MATCHED THEN
                          UPDATE SET data_json = :dataJson, deleted = 0
                        WHEN NOT MATCHED THEN
                          INSERT (entity_type, entity_id, data_json, deleted)
                          VALUES (:entityType, :entityId, :dataJson, 0)
                        """;
          };

      jdbc.update(sql, params);
    } catch (Exception e) {
      throw new RuntimeException("Failed to save JSON extension fields", e);
    }
  }

  @Override
  public Map<String, Object> load(ExtensionContext context) {
    String sql =
        """
                SELECT data_json FROM ext_data_json
                WHERE entity_type = :entityType
                  AND entity_id = :entityId
                  AND deleted = FALSE
                """;

    Map<String, Object> params =
        Map.of(
            "entityType", context.getEntityType(),
            "entityId", context.getEntityId());

    try {
      List<String> result = jdbc.query(sql, params, (rs, rowNum) -> rs.getString("data_json"));
      if (result.isEmpty()) {
        return Map.of();
      }

      String rawJson = result.get(0);
      if (rawJson.startsWith("\"") && rawJson.endsWith("\"")) {
        rawJson =
            rawJson
                .substring(1, rawJson.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\n", "")
                .replace("\\\\", "\\");
      }

      return JsonUtil.fromJson(rawJson, new TypeReference<>() {});

    } catch (Exception e) {
      throw new RuntimeException("Failed to deserialize JSON extension fields", e);
    }
  }
}
