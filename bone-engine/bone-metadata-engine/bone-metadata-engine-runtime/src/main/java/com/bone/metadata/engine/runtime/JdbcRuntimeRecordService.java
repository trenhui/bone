package com.bone.metadata.engine.runtime;

import com.bone.core.model.PageResult;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** 模式 B：基于已发布元数据对物理表执行动态 CRUD（经 bone-metadata-sdk 同源 JDBC）。 */
public class JdbcRuntimeRecordService {

  private static final Set<String> SYSTEM_COLUMNS =
      Set.of(
          "id",
          "tenant_id",
          "created_at",
          "updated_at",
          "created_by",
          "updated_by",
          "deleted",
          "version");

  private final NamedParameterJdbcTemplate jdbc;
  private final RuntimeEntityCatalog catalog;
  private final Map<String, Set<String>> physicalColumnsCache = new ConcurrentHashMap<>();

  public JdbcRuntimeRecordService(NamedParameterJdbcTemplate jdbc, RuntimeEntityCatalog catalog) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.catalog = Objects.requireNonNull(catalog);
  }

  public PageResult<Map<String, Object>> page(
      String entityCode, long tenantId, int page, int size) {
    return page(entityCode, tenantId, page, size, RuntimePageQuery.EMPTY);
  }

  public PageResult<Map<String, Object>> page(
      String entityCode, long tenantId, int page, int size, RuntimePageQuery query) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    int safePage = Math.max(1, page);
    int safeSize = Math.min(Math.max(1, size), 200);
    int offset = (safePage - 1) * safeSize;

    RuntimePageQuery effective = query == null ? RuntimePageQuery.EMPTY : query;
    String table = quoteTable(entity.physicalTableName());
    MapSqlParameterSource params = tenantParams(entity, tenantId);
    String filterClause = RuntimeQuerySupport.buildFilterClause(entity, effective, params);
    String where = buildTenantWhere(entity, tenantId) + filterClause;

    String countSql = "SELECT COUNT(*) FROM " + table + where;
    List<String> selectCols = RuntimeQuerySupport.resolveSelectColumns(entity, effective);
    String selectList =
        selectCols.size() == 1 && "*".equals(selectCols.get(0))
            ? "*"
            : String.join(", ", selectCols);
    String listSql =
        "SELECT "
            + selectList
            + " FROM "
            + table
            + where
            + RuntimeQuerySupport.buildOrderBy(entity, effective)
            + " LIMIT :limit OFFSET :offset";

    Long total = jdbc.queryForObject(countSql, params, Long.class);
    params.addValue("limit", safeSize);
    params.addValue("offset", offset);
    List<Map<String, Object>> rows = jdbc.queryForList(listSql, params);

    // 机制 B：物理 ext_* 列 → 逻辑 code 回填，保证返回字段名稳定
    List<Map<String, Object>> translated =
        rows.stream().map(r -> toLogical(entity, r)).collect(Collectors.toList());
    return PageResult.of(translated, total != null ? total : 0L, safePage, safeSize);
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
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND, "记录不存在: " + recordId);
    }
    // 机制 B：物理 ext_* 列 → 逻辑 code 回填，保证返回字段名稳定
    return toLogical(entity, rows.get(0));
  }

  public Map<String, Object> create(
      String entityCode, long tenantId, Map<String, Object> body, long newId) {
    return create(entityCode, tenantId, body, newId, null);
  }

  public Map<String, Object> create(
      String entityCode, long tenantId, Map<String, Object> body, long newId, String operatorId) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    Map<String, Object> payload = filterWritable(entity, body);
    validateForCreate(entity, tenantId, payload);
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
    stampAuditColumns(entity, payload, operatorId, true);

    // 机制 B：逻辑 code → 物理 ext_* 列；机制 A：physicalColumn 为 null，物理列即 code
    Map<String, Object> sqlPayload = toPhysical(entity, payload);
    List<String> cols = new ArrayList<>(sqlPayload.keySet());
    String colList =
        cols.stream().map(c -> "`" + sanitizeIdentifier(c) + "`").collect(Collectors.joining(", "));
    String valList = cols.stream().map(c -> ":" + c).collect(Collectors.joining(", "));
    String sql =
        "INSERT INTO "
            + quoteTable(entity.physicalTableName())
            + " ("
            + colList
            + ") VALUES ("
            + valList
            + ")";

    MapSqlParameterSource params = new MapSqlParameterSource(sqlPayload);
    jdbc.update(sql, params);
    return getById(entityCode, tenantId, String.valueOf(payload.get(pk)));
  }

  public Map<String, Object> update(
      String entityCode, long tenantId, String recordId, Map<String, Object> body) {
    return update(entityCode, tenantId, recordId, body, null);
  }

  public Map<String, Object> update(
      String entityCode,
      long tenantId,
      String recordId,
      Map<String, Object> body,
      Integer expectedVersion) {
    return update(entityCode, tenantId, recordId, body, expectedVersion, null);
  }

  public Map<String, Object> update(
      String entityCode,
      long tenantId,
      String recordId,
      Map<String, Object> body,
      Integer expectedVersion,
      String operatorId) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    Map<String, Object> payload = filterWritable(entity, body);
    payload.remove(entity.primaryKeyColumn());
    validateForUpdate(entity, tenantId, payload, recordId);

    boolean versioned = entityHasColumn(entity, "version");
    if (expectedVersion != null && !versioned) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_INVALID_QUERY, "物理表无 version 列，不支持 If-Match");
    }

    if (payload.isEmpty() && !(versioned && expectedVersion != null)) {
      return getById(entityCode, tenantId, recordId);
    }
    stampAuditColumns(entity, payload, operatorId, false);

    // 机制 B：逻辑 code → 物理 ext_* 列；机制 A：physicalColumn 为 null，物理列即 code
    Map<String, Object> sqlPayload = toPhysical(entity, payload);
    List<String> setParts = new ArrayList<>();
    for (String c : sqlPayload.keySet()) {
      setParts.add("`" + sanitizeIdentifier(c) + "` = :" + c);
    }
    if (versioned) {
      setParts.add("`version` = `version` + 1");
    }
    String setClause = String.join(", ", setParts);
    if (setClause.isEmpty() && versioned) {
      setClause = "`version` = `version` + 1";
    }

    String pk = sanitizeIdentifier(entity.primaryKeyColumn());
    StringBuilder where = new StringBuilder(buildTenantWhere(entity, tenantId));
    where.append(" AND `").append(pk).append("` = :pk");
    if (versioned && expectedVersion != null) {
      where.append(" AND `version` = :expectedVersion");
    }

    String sql = "UPDATE " + quoteTable(entity.physicalTableName()) + " SET " + setClause + where;

    MapSqlParameterSource params = new MapSqlParameterSource(sqlPayload);
    params.addValue("pk", parsePkValue(recordId));
    addTenantParam(entity, tenantId, params);
    if (versioned && expectedVersion != null) {
      params.addValue("expectedVersion", expectedVersion);
    }
    int updated = jdbc.update(sql, params);
    if (updated == 0) {
      if (versioned && expectedVersion != null) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.PRECONDITION_FAILED,
            "版本冲突：If-Match v" + expectedVersion + " 与当前记录不一致");
      }
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND, "记录不存在: " + recordId);
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
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND, "记录不存在: " + recordId);
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
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND, "记录不存在: " + recordId);
    }
  }

  // ===================== 写入校验（P0-5） =====================

  private void validateForCreate(
      PublishedRuntimeEntity entity, long tenantId, Map<String, Object> payload) {
    for (RuntimeFieldColumn col : entity.columns()) {
      if (col.primaryKey()) {
        continue;
      }
      boolean present = payload.containsKey(col.code()) && payload.get(col.code()) != null;
      if (col.required() && !present) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, "字段「" + col.code() + "」为必填");
      }
      if (present) {
        validateType(col, payload.get(col.code()));
        if (col.unique()) {
          assertUnique(entity, tenantId, col, payload.get(col.code()), null);
        }
      }
    }
  }

  private void validateForUpdate(
      PublishedRuntimeEntity entity, long tenantId, Map<String, Object> payload, String recordId) {
    for (RuntimeFieldColumn col : entity.columns()) {
      if (!payload.containsKey(col.code())) {
        continue;
      }
      Object value = payload.get(col.code());
      if (col.required() && value == null) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, "字段「" + col.code() + "」为必填，不可置空");
      }
      if (value != null) {
        validateType(col, value);
        if (col.unique()) {
          assertUnique(entity, tenantId, col, value, recordId);
        }
      }
    }
  }

  private void validateType(RuntimeFieldColumn col, Object value) {
    String type = col.type() == null ? "" : col.type().toUpperCase();
    if (type.matches("LONG|BIGINT|INT|INTEGER|DECIMAL|DOUBLE|FLOAT|NUMBER")) {
      if (!(value instanceof Number)) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, "字段「" + col.code() + "」应为数值类型");
      }
    } else if (type.matches("BOOLEAN|BOOL")) {
      if (!(value instanceof Boolean)) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, "字段「" + col.code() + "」应为布尔类型");
      }
    }
  }

  private void assertUnique(
      PublishedRuntimeEntity entity,
      long tenantId,
      RuntimeFieldColumn col,
      Object value,
      String excludeRecordId) {
    String table = quoteTable(entity.physicalTableName());
    // 机制 B：唯一校验必须在物理 ext_* 列上执行
    String colName = sanitizeIdentifier(physName(col));
    StringBuilder sql =
        new StringBuilder("SELECT COUNT(*) FROM ")
            .append(table)
            .append(" WHERE `")
            .append(colName)
            .append("` = :val");
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("val", value);
    if (entityHasColumn(entity, "tenant_id")) {
      sql.append(" AND `tenant_id` = :tenantId");
      params.addValue("tenantId", tenantId);
    }
    if (entityHasColumn(entity, "deleted")) {
      sql.append(" AND `deleted` = 0");
    }
    if (excludeRecordId != null) {
      sql.append(" AND `").append(sanitizeIdentifier(entity.primaryKeyColumn())).append("` <> :pk");
      params.addValue("pk", parsePkValue(excludeRecordId));
    }
    Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
    if (count != null && count > 0) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_DUPLICATE, "字段「" + col.code() + "」值「" + value + "」已存在（唯一约束）");
    }
  }

  /** 物理列名：机制 B 返回 ext_* 预留列，机制 A 返回 code 本身。 */
  private static String physName(RuntimeFieldColumn col) {
    return col.physicalColumn() != null ? col.physicalColumn() : col.code();
  }

  /**
   * 将「逻辑 code 键」的写入负载翻译为「物理列名键」。 机制 A（核心）字段 physicalColumn 为 null → 物理列即 code；机制 B（扩展）字段
   * physicalColumn 指向 ext_* 预留列。 系统列（id/tenant_id/deleted/审计列）本就是物理名，原样透传。
   */
  private static Map<String, Object> toPhysical(
      PublishedRuntimeEntity entity, Map<String, Object> logical) {
    Map<String, Object> physical = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : logical.entrySet()) {
      String key = e.getKey();
      if (SYSTEM_COLUMNS.contains(key)) {
        physical.put(key, e.getValue());
        continue;
      }
      String phys =
          entity.columns().stream()
              .filter(c -> c.code().equals(key))
              .findFirst()
              .map(JdbcRuntimeRecordService::physName)
              .orElse(key);
      physical.put(phys, e.getValue());
    }
    return physical;
  }

  /** 将物理列结果翻译回逻辑 code 键（机制 B 字段 ext_* → code），保证返回给前端的字段名稳定。 */
  private static Map<String, Object> toLogical(
      PublishedRuntimeEntity entity, Map<String, Object> row) {
    Map<String, String> physToLogical = new HashMap<>();
    for (RuntimeFieldColumn c : entity.columns()) {
      if (c.physicalColumn() != null) {
        physToLogical.put(c.physicalColumn(), c.code());
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : row.entrySet()) {
      String logical = physToLogical.getOrDefault(e.getKey(), e.getKey());
      out.put(logical, e.getValue());
    }
    return out;
  }

  // ===================== 操作者审计（P0-2） =====================

  /**
   * 写入审计列。仅当物理表确实存在对应列时才注入（兼容发布前旧表无审计列的情况，避免 Unknown column 报错）。 物理列存在性通过 {@link
   * #hasPhysicalColumn} 探测并按表名缓存。
   *
   * <p><b>时间戳（created_at / updated_at）必须始终注入</b>：这两列通常 NOT NULL 且无默认值，遗漏会导致 INSERT/UPDATE 直接 500；
   * 其取值与时间戳无关，与操作者是否可解析无关，故置于操作者判断之外。
   *
   * <p><b>操作者列（created_by / updated_by）为 BIGINT 用户 ID</b>：仅当 {@code operatorId} 可解析为数值时才注入； 未认证（dev
   * 关闭安全）或主体名为非数值（如 anonymousUser）时跳过，避免把字符串写入 BIGINT 报 "Incorrect integer value"（P0 数据面健壮性）。操作者列本身
   * nullable，跳过不影响落库。
   */
  private void stampAuditColumns(
      PublishedRuntimeEntity entity,
      Map<String, Object> payload,
      String operatorId,
      boolean isCreate) {
    String table = entity.physicalTableName();
    // 时间戳：始终注入（NOT NULL 列，遗漏即 500）
    if (hasPhysicalColumn(table, "updated_at")) {
      payload.put("updated_at", new Timestamp(System.currentTimeMillis()));
    }
    if (isCreate && hasPhysicalColumn(table, "created_at")) {
      payload.put("created_at", new Timestamp(System.currentTimeMillis()));
    }
    // 操作者：仅当可解析为数值用户 ID 时注入 BIGINT 列
    Long numericOp = toNumericOperatorId(operatorId);
    if (numericOp != null) {
      if (hasPhysicalColumn(table, "created_by")) {
        payload.put("created_by", numericOp);
      }
      if (hasPhysicalColumn(table, "updated_by")) {
        payload.put("updated_by", numericOp);
      }
    }
  }

  /** 将操作者标识解析为数值用户 ID（BIGINT 审计列）。无法解析时返回 null（跳过 by 列注入）。 */
  private static Long toNumericOperatorId(String operatorId) {
    if (operatorId == null) {
      return null;
    }
    String s = operatorId.trim();
    if (s.isEmpty()) {
      return null;
    }
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private boolean hasPhysicalColumn(String table, String column) {
    Set<String> cols =
        physicalColumnsCache.computeIfAbsent(
            table,
            t -> {
              try {
                List<Map<String, Object>> rows =
                    jdbc.getJdbcTemplate()
                        .queryForList(
                            "SELECT COLUMN_NAME FROM information_schema.columns WHERE table_name = ?",
                            t);
                Set<String> set = new LinkedHashSet<>();
                for (Map<String, Object> row : rows) {
                  Object name = row.get("COLUMN_NAME");
                  if (name != null) {
                    set.add(name.toString().toLowerCase());
                  }
                }
                return set;
              } catch (Exception ignored) {
                // 探测失败（如 information_schema 不可达）则视为无审计列，审计降级而非报错
                return Set.of();
              }
            });
    return cols.contains(column.toLowerCase());
  }

  private PublishedRuntimeEntity requireRuntimeEntity(String entityCode, long tenantId) {
    return catalog
        .findPublishedRuntime(entityCode, tenantId)
        .orElseThrow(
            () ->
                new RuntimeRecordException(
                    MetadataErrorCodes.RUNTIME_ENTITY_NOT_FOUND,
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
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_INVALID_IDENTIFIER, "非法标识符: " + name);
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
