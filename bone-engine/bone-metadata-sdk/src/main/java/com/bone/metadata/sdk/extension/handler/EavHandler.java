package com.bone.metadata.sdk.extension.handler;

import com.bone.metadata.sdk.domain.enums.ExtensionMode;
import com.bone.metadata.sdk.extension.ExtensionContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EAV模式扩展字段处理器。
 */
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
        String sql = """
            INSERT INTO ext_data_eav (entity_type, entity_id, attr_key, attr_value)
            VALUES (:entityType, :entityId, :attrKey, :attrValue)
            ON CONFLICT (entity_type, entity_id, attr_key) DO UPDATE SET attr_value = :attrValue
            """;
        List<Map<String, Object>> batchParams = context.getExtraProperties().entrySet().stream()
                .map(entry -> Map.of(
                        "entityType", context.getEntityType(),
                        "entityId", context.getEntityId(),
                        "attrKey", entry.getKey(),
                        "attrValue", String.valueOf(entry.getValue())
                ))
                .toList();
        @SuppressWarnings({"unchecked", "rawtypes"})
        Map<String, ?>[] paramsArray = batchParams.toArray(new Map[0]);
        jdbc.batchUpdate(sql, paramsArray);
    }

    @Override
    public Map<String, Object> load(ExtensionContext context) {
        String sql = """
            SELECT attr_key, attr_value 
            FROM ext_data_eav 
            WHERE entity_type = :entityType AND entity_id = :entityId
            """;
        Map<String, Object> params = Map.of(
                "entityType", context.getEntityType(),
                "entityId", context.getEntityId()
        );
        return jdbc.query(sql, params, rs -> {
            Map<String, Object> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getString("attr_key"), rs.getString("attr_value"));
            }
            return result;
        });
    }
}