package com.bone.metadata.engine.runtime;

import com.bone.core.model.PageResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
/**
 * 模式 B：基于已发布元数据对物理表执行动态 CRUD（经 bone-metadata-sdk 同源 JDBC）。
 */
public class JdbcRuntimeRecordService {

  private static final Set<String> SYSTEM_COLUMNS =
      Set.of("id", "tenant_id", "created_at", "updated_at", "created_by", "updated_by", "deleted", "version");

  private final NamedParameterJdbcTemplate jdbc;
  private final RuntimeEntityCatalog catalog;

  public JdbcRuntimeRecordService(NamedParameterJdbcTemplate jdbc, RuntimeEntityCatalog catalog) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.catalog = Objects.requireNonNull(catalog);
  }

  public PageResult<Map<String, Object>> page(
      String entityCode, long tenantId, int page, int size) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    int safePage = Math.max(1, page);
    int safeSize = Math.min(Math.max(1, size), 200);
    int offset = (safePage - 1) * safeSize;

    String table = quoteTable(entity.physicalTableName());
    String where = buildTenantWhere(entity, tenantId);
    String countSql = "SELECT COUNT(*) FROM " + table + where;
    String listSql =
        "SELECT * FROM "
            + table
            + where
            + " ORDER BY `"
            + sanitizeIdentifier(entity.primaryKeyColumn())
            + "` DESC LIMIT :limit OFFSET :offset";

    MapSqlParameterSource params = tenantParams(entity, tenantId);
    Long total = jdbc.queryForObject(countSql, params, Long.class);
    params.addValue("limit", safeSize);
    params.addValue("offset", offset);
    List<Map<String, Object>> rows = jdbc.queryForList(listSql, params);

    return PageResult.of(rows, total != null ? total : 0L, safePage, safeSize);
  }

  public Map<String, Object> getById(String entityCode, long tenantId, String recordId) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    String pk = sanitizeIdentifier(entity.primaryKeyColumn());
    String sql =
        "SELECT * FROM "
            + quoteTable(entity.physicalTableName())
            + buildTenantWhere(entity, tenantId)
            + " AND `"
            + pk
            + "` = :pk";
    MapSqlParameterSource params = tenantParams(entity, tenantId);
    params.addValue("pk", parsePkValue(recordId));
    List<Map<String, Object>> rows = jdbc.queryForList(sql, params);
    if (rows.isEmpty()) {
      throw new RuntimeRecordException("META_RUNTIME_RECORD_NOT_FOUND", "记录不存在: " + recordId);
    }
    return rows.get(0);
  }

  public Map<String, Object> create(
      String entityCode, long tenantId, Map<String, Object> body, long newId) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    Map<String, Object> payload = filterWritable(entity, body);
    String pk = entity.primaryKeyColumn();
    if (!payload.containsKey(pk)) {
      payload.put(pk, newId);
    }
    if (entityHasColumn(entity, "tenant_id")) {
      payload.putIfAbsent("tenant_id", tenantId);
    }
    if (entityHasColumn(entity, "deleted")) {
      payload.putIfAbsent("deleted", 0);
    }

    List<String> cols = new ArrayList<>(payload.keySet());
    String colList = cols.stream().map(c -> "`" + sanitizeIdentifier(c) + "`").collect(Collectors.joining(", "));
    String valList = cols.stream().map(c -> ":" + c).collect(Collectors.joining(", "));
    String sql = "INSERT INTO " + quoteTable(entity.physicalTableName()) + " (" + colList + ") VALUES (" + valList + ")";

    MapSqlParameterSource params = new MapSqlParameterSource(payload);
    jdbc.update(sql, params);
    return getById(entityCode, tenantId, String.valueOf(payload.get(pk)));
  }

  public Map<String, Object> update(
      String entityCode, long tenantId, String recordId, Map<String, Object> body) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    Map<String, Object> payload = filterWritable(entity, body);
    payload.remove(entity.primaryKeyColumn());

    if (payload.isEmpty()) {
      return getById(entityCode, tenantId, recordId);
    }

    String setClause =
        payload.keySet().stream()
            .map(c -> "`" + sanitizeIdentifier(c) + "` = :" + c)
            .collect(Collectors.joining(", "));
    String pk = sanitizeIdentifier(entity.primaryKeyColumn());
    String sql =
        "UPDATE "
            + quoteTable(entity.physicalTableName())
            + " SET "
            + setClause
            + buildTenantWhere(entity, tenantId)
            + " AND `"
            + pk
            + "` = :pk";

    MapSqlParameterSource params = new MapSqlParameterSource(payload);
    params.addValue("pk", parsePkValue(recordId));
    addTenantParam(entity, tenantId, params);
    int updated = jdbc.update(sql, params);
    if (updated == 0) {
      throw new RuntimeRecordException("META_RUNTIME_RECORD_NOT_FOUND", "记录不存在: " + recordId);
    }
    return getById(entityCode, tenantId, recordId);
  }

  public void delete(String entityCode, long tenantId, String recordId) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    String pk = sanitizeIdentifier(entity.primaryKeyColumn());
    if (entityHasColumn(entity, "deleted")) {
      String sql =
          "UPDATE "
              + quoteTable(entity.physicalTableName())
              + " SET `deleted` = 1"
              + buildTenantWhere(entity, tenantId)
              + " AND `"
              + pk
              + "` = :pk";
      MapSqlParameterSource params = tenantParams(entity, tenantId);
      params.addValue("pk", parsePkValue(recordId));
      int updated = jdbc.update(sql, params);
      if (updated == 0) {
        throw new RuntimeRecordException("META_RUNTIME_RECORD_NOT_FOUND", "记录不存在: " + recordId);
      }
      return;
    }
    String sql =
        "DELETE FROM "
            + quoteTable(entity.physicalTableName())
            + buildTenantWhere(entity, tenantId)
            + " AND `"
            + pk
            + "` = :pk";
    MapSqlParameterSource params = tenantParams(entity, tenantId);
    params.addValue("pk", parsePkValue(recordId));
    int deleted = jdbc.update(sql, params);
    if (deleted == 0) {
      throw new RuntimeRecordException("META_RUNTIME_RECORD_NOT_FOUND", "记录不存在: " + recordId);
    }
  }

  private PublishedRuntimeEntity requireRuntimeEntity(String entityCode, long tenantId) {
    return catalog
        .findPublishedRuntime(entityCode, tenantId)
        .orElseThrow(
            () ->
                new RuntimeRecordException(
                    "META_RUNTIME_ENTITY_NOT_FOUND",
                    "未找到已发布的 RUNTIME 实体: " + entityCode));
  }

  private static Map<String, Object> filterWritable(
      PublishedRuntimeEntity entity, Map<String, Object> body) {
    if (body == null || body.isEmpty()) {
      return new LinkedHashMap<>();
    }
    Set<String> allowed =
        entity.columns().stream().map(RuntimeFieldColumn::code).collect(Collectors.toSet());
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : body.entrySet()) {
      String key = e.getKey();
      if (allowed.contains(key) && !SYSTEM_COLUMNS.contains(key)) {
        out.put(key, e.getValue());
      }
    }
    return out;
  }

  private static boolean entityHasColumn(PublishedRuntimeEntity entity, String name) {
    return entity.hasColumn(name) || SYSTEM_COLUMNS.contains(name);
  }

  private static String buildTenantWhere(PublishedRuntimeEntity entity, long tenantId) {
    StringBuilder sb = new StringBuilder(" WHERE 1=1");
    if (entityHasColumn(entity, "deleted")) {
      sb.append(" AND `deleted` = 0");
    }
    if (entityHasColumn(entity, "tenant_id")) {
      sb.append(" AND `tenant_id` = :tenantId");
    }
    return sb.toString();
  }

  private static MapSqlParameterSource tenantParams(PublishedRuntimeEntity entity, long tenantId) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    addTenantParam(entity, tenantId, params);
    return params;
  }

  private static void addTenantParam(
      PublishedRuntimeEntity entity, long tenantId, MapSqlParameterSource params) {
    if (entityHasColumn(entity, "tenant_id")) {
      params.addValue("tenantId", tenantId);
    }
  }

  private static String quoteTable(String tableName) {
    return "`" + sanitizeIdentifier(tableName) + "`";
  }

  private static String sanitizeIdentifier(String name) {
    if (name == null || !name.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
      throw new RuntimeRecordException("META_RUNTIME_INVALID_IDENTIFIER", "非法标识符: " + name);
    }
    return name;
  }

  private static Object parsePkValue(String recordId) {
    try {
      return Long.parseLong(recordId);
    } catch (NumberFormatException ex) {
      return recordId;
    }
  }
}
