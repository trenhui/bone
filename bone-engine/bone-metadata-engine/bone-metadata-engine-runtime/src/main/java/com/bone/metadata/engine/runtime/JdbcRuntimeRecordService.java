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
  private final RuntimeAuditSink auditSink;
  private final RuntimeStateEntryResolver stateEntryResolver;
  private final Map<String, Set<String>> physicalColumnsCache = new ConcurrentHashMap<>();

  public JdbcRuntimeRecordService(NamedParameterJdbcTemplate jdbc, RuntimeEntityCatalog catalog) {
    this(jdbc, catalog, RuntimeAuditSink.NOOP, RuntimeStateEntryResolver.NONE);
  }

  public JdbcRuntimeRecordService(
      NamedParameterJdbcTemplate jdbc, RuntimeEntityCatalog catalog, RuntimeAuditSink auditSink) {
    this(jdbc, catalog, auditSink, RuntimeStateEntryResolver.NONE);
  }

  public JdbcRuntimeRecordService(
      NamedParameterJdbcTemplate jdbc,
      RuntimeEntityCatalog catalog,
      RuntimeAuditSink auditSink,
      RuntimeStateEntryResolver stateEntryResolver) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.catalog = Objects.requireNonNull(catalog);
    this.auditSink = auditSink == null ? RuntimeAuditSink.NOOP : auditSink;
    this.stateEntryResolver =
        stateEntryResolver == null ? RuntimeStateEntryResolver.NONE : stateEntryResolver;
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
    assertStateFieldNotWritable(entity, body, "POST");
    Map<String, Object> payload = filterWritable(entity, body);
    injectEntryState(entity, entityCode, tenantId, payload);
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
    assertStateFieldNotWritable(entity, body, "PUT");
    return updateInternal(entityCode, tenantId, recordId, body, expectedVersion, operatorId);
  }

  /**
   * 写路径内部实现（<strong>不做</strong> {@code state_field} 保护）——仅被 {@link #update}（已保护） 与 {@link
   * #updateState} / {@link #adminFixState}（状态机合法入口）调用。
   */
  private Map<String, Object> updateInternal(
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
    delete(entityCode, tenantId, recordId, null, null);
  }

  /**
   * 软删（归档）。
   *
   * <p><b>为什么支持 {@code If-Match}</b>：归档是不可逆操作（无 undelete 时是永久丢数据； 有 undelete
   * 时也会短暂影响下游），与「更新」同样需要防「基于陈旧快照的误删」。 判据（Runtime §8 R13）：{@code If-Match}
   * <strong>可选但生效</strong>——携带时冲突返回 412； 未携带时按无并发保护处理，由前端在确认弹层明示「归档不可恢复」承担告知责任。
   *
   * @param expectedVersion 可选；非 null 且表有 version 列时作为并发前置条件
   */
  public void delete(
      String entityCode,
      long tenantId,
      String recordId,
      Integer expectedVersion,
      String operatorId) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    String pk = sanitizeIdentifier(entity.primaryKeyColumn());
    boolean versioned = entityHasColumn(entity, "version");
    if (expectedVersion != null && !versioned) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_INVALID_QUERY, "物理表无 version 列，不支持 If-Match");
    }
    if (entityHasColumn(entity, "deleted")) {
      StringBuilder where =
          new StringBuilder(buildTenantWhere(entity, tenantId))
              .append(" AND `")
              .append(pk)
              .append("` = :pk");
      if (versioned && expectedVersion != null) {
        where.append(" AND `version` = :expectedVersion");
      }
      String sql =
          "UPDATE "
              + quoteTable(entity.physicalTableName())
              + " SET `deleted` = 1"
              + (versioned ? ", `version` = `version` + 1" : "")
              + where;
      MapSqlParameterSource params = tenantParams(entity, tenantId);
      params.addValue("pk", parsePkValue(recordId));
      if (versioned && expectedVersion != null) {
        params.addValue("expectedVersion", expectedVersion);
      }
      int updated = jdbc.update(sql, params);
      if (updated == 0) {
        throw resolveDeleteFailure(entity, tenantId, recordId, expectedVersion, versioned);
      }
      auditSink.record(
          RuntimeAuditEntry.of(
              tenantId,
              entityCode,
              recordId,
              "deleted",
              "0",
              "1",
              operatorId,
              null,
              "delete",
              null));
      return;
    }
    // 无 deleted 列：物理删除（不可恢复）。此分支不写审计行——行本身已不存在，审计无对象可指。
    StringBuilder where =
        new StringBuilder(buildTenantWhere(entity, tenantId))
            .append(" AND `")
            .append(pk)
            .append("` = :pk");
    if (versioned && expectedVersion != null) {
      where.append(" AND `version` = :expectedVersion");
    }
    String sql = "DELETE FROM " + quoteTable(entity.physicalTableName()) + where;
    MapSqlParameterSource params = tenantParams(entity, tenantId);
    params.addValue("pk", parsePkValue(recordId));
    if (versioned && expectedVersion != null) {
      params.addValue("expectedVersion", expectedVersion);
    }
    int deleted = jdbc.update(sql, params);
    if (deleted == 0) {
      throw resolveDeleteFailure(entity, tenantId, recordId, expectedVersion, versioned);
    }
  }

  /**
   * 恢复已归档记录（回收站 → 在用）。
   *
   * <p><b>为什么必须补这个口子</b>：Runtime §0.3 实测——软删单向不可逆，且列探测失败会静默降级为 <strong>物理
   * DELETE</strong>。只做「不可恢复明示」而不给恢复能力，等于把「误归档」永久化。 本方法仅在物理表确实存在 {@code deleted}
   * 列时可用；无该列时记录已被物理删除，无从恢复。
   */
  public Map<String, Object> restore(
      String entityCode, long tenantId, String recordId, String operatorId) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    if (!entityHasColumn(entity, "deleted")) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_INVALID_QUERY, "物理表无 deleted 列（软删未生效），该实体的归档为物理删除，不可恢复");
    }
    String pk = sanitizeIdentifier(entity.primaryKeyColumn());
    String sql =
        "UPDATE "
            + quoteTable(entity.physicalTableName())
            + " SET `deleted` = 0"
            + (entityHasColumn(entity, "version") ? ", `version` = `version` + 1" : "")
            + " WHERE `tenant_id` = :tenantId AND `"
            + pk
            + "` = :pk AND `deleted` = 1";
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("tenantId", tenantId);
    params.addValue("pk", parsePkValue(recordId));
    int updated = jdbc.update(sql, params);
    if (updated == 0) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND, "归档记录不存在或已恢复: " + recordId);
    }
    auditSink.record(
        RuntimeAuditEntry.of(
            tenantId,
            entityCode,
            recordId,
            "deleted",
            "1",
            "0",
            operatorId,
            null,
            "restore",
            null));
    return getById(entityCode, tenantId, recordId);
  }

  /**
   * 回收站视图：只查 {@code deleted = 1} 的行。
   *
   * <p>与 {@link #page} 的唯一差别是租户谓词里的 deleted 取值；复用同一套列投影 / 过滤 / 排序， 避免「回收站和数据列表两套查询逻辑」的漂移。
   */
  public PageResult<Map<String, Object>> pageDeleted(
      String entityCode, long tenantId, int page, int size, RuntimePageQuery query) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    if (!entityHasColumn(entity, "deleted")) {
      return PageResult.of(List.of(), 0L, page, size);
    }
    int safePage = Math.max(1, page);
    int safeSize = Math.min(Math.max(1, size), 200);
    int offset = (safePage - 1) * safeSize;
    RuntimePageQuery effective = query == null ? RuntimePageQuery.EMPTY : query;
    String table = quoteTable(entity.physicalTableName());
    MapSqlParameterSource params = tenantParams(entity, tenantId);
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    if (entityHasColumn(entity, "tenant_id")) {
      where.append(" AND `tenant_id` = :tenantId");
    }
    where.append(" AND `deleted` = 1");
    where.append(RuntimeQuerySupport.buildFilterClause(entity, effective, params));

    Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + where, params, Long.class);
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
    params.addValue("limit", safeSize);
    params.addValue("offset", offset);
    List<Map<String, Object>> rows = jdbc.queryForList(listSql, params);
    List<Map<String, Object>> translated =
        rows.stream().map(r -> toLogical(entity, r)).collect(Collectors.toList());
    return PageResult.of(translated, total != null ? total : 0L, safePage, safeSize);
  }

  /**
   * <strong>状态机唯一合法推进入口</strong>（RC-008）：更新受保护的状态字段，并落记录级审计。
   *
   * <p>与 {@link #update} 的区别：{@code update} 对 {@code state_field} 一律 422； 本方法只被 {@code
   * RuntimeActionService} 在校验过转移矩阵后调用，跳过保护并记录 {@code from -> to}。
   * 这样「能改状态字段的代码路径」在全局只有一处，审计与转移校验无法被绕过。
   *
   * @param expectedVersion 可选；资金类动作建议带（端到端 If-Match）
   */
  public Map<String, Object> updateState(
      String entityCode,
      long tenantId,
      String recordId,
      String stateField,
      String toStatus,
      Integer expectedVersion,
      String operatorId,
      String actionCode,
      String reason) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    Map<String, Object> current = getById(entityCode, tenantId, recordId);
    Object before = current.get(stateField);
    String from = before == null ? null : before.toString();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(stateField, toStatus);
    Map<String, Object> updated =
        updateInternal(entityCode, tenantId, recordId, payload, expectedVersion, operatorId);
    auditSink.record(
        RuntimeAuditEntry.of(
            tenantId,
            entityCode,
            recordId,
            stateField,
            from,
            toStatus,
            operatorId,
            reason,
            "action",
            actionCode));
    return updated;
  }

  /**
   * 平台管理员修复直写（唯一允许绕过 {@code state_field} 保护的写路径，Runtime §4.5 例外）。
   *
   * <p><b>为什么必须强制 {@code reason}</b>：这是唯一能绕过状态机的口子，若无理由即可使用，
   * 它就退化成「换个字段名的新后门」。理由入审计表，使每次修复都可回溯「谁、为何改」。
   */
  public Map<String, Object> adminFixState(
      String entityCode,
      long tenantId,
      String recordId,
      String stateField,
      String toStatus,
      String operatorId,
      String reason) {
    if (reason == null || reason.isBlank()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED, "管理员修复直写状态字段必须填写 reason（审计要求）");
    }
    Map<String, Object> current = getById(entityCode, tenantId, recordId);
    Object before = current.get(stateField);
    String from = before == null ? null : before.toString();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(stateField, toStatus);
    Map<String, Object> updated =
        updateInternal(entityCode, tenantId, recordId, payload, null, operatorId);
    auditSink.record(
        RuntimeAuditEntry.of(
            tenantId,
            entityCode,
            recordId,
            stateField,
            from,
            toStatus,
            operatorId,
            reason,
            "admin-fix",
            null));
    return updated;
  }

  private RuntimeRecordException resolveDeleteFailure(
      PublishedRuntimeEntity entity,
      long tenantId,
      String recordId,
      Integer expectedVersion,
      boolean versioned) {
    if (versioned && expectedVersion != null) {
      return new RuntimeRecordException(
          MetadataErrorCodes.PRECONDITION_FAILED,
          "版本冲突：If-Match v" + expectedVersion + " 与当前记录不一致");
    }
    return new RuntimeRecordException(
        MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND, "记录不存在: " + recordId);
  }

  /**
   * 创建时由<strong>平台</strong>注入状态机入口状态（客户端不传时）。
   *
   * <p>为什么必须在这里而不是 Controller：受保护状态字段通常是必填列，若只禁直写而不注入入口状态， 「创建记录」会死锁（不传 → 必填失败；传 →
   * 422）。把注入放在写路径唯一出口内， 可保证任何调用方创建出来的记录都从<strong>合法入口态</strong>起步，而不是终态。
   */
  private void injectEntryState(
      PublishedRuntimeEntity entity,
      String entityCode,
      long tenantId,
      Map<String, Object> payload) {
    String stateField = entity.stateField();
    if (stateField == null || stateField.isBlank() || payload.containsKey(stateField)) {
      return;
    }
    String entry = stateEntryResolver.entryState(entityCode, tenantId);
    if (entry != null && !entry.isBlank()) {
      payload.put(stateField, entry);
    }
  }

  /**
   * RC-008（Must）：实体声明 {@code state_field} 后，通用写路径禁止直写该字段。
   *
   * <p><b>为什么显式 422 而不是静默忽略</b>：静默 drop 会让调用方误以为状态已写入，
   * 从而在「以为已打款」的前提下继续下游业务；显式拒绝把「状态只能经状态机推进」变成可观测契约。
   */
  private static void assertStateFieldNotWritable(
      PublishedRuntimeEntity entity, Map<String, Object> body, String channel) {
    String stateField = entity.stateField();
    if (stateField == null
        || stateField.isBlank()
        || body == null
        || !body.containsKey(stateField)) {
      return;
    }
    throw new RuntimeRecordException(
        MetadataErrorCodes.RUNTIME_FIELD_PROTECTED,
        "字段「"
            + stateField
            + "」为受保护的状态字段，不可经 "
            + channel
            + " 直写；请改用 POST /api/v1/runtime/entities/"
            + entity.entityCode()
            + "/records/{id}/actions/{actionCode} 经状态机推进");
  }

  // ===================== 写入校验（P0-5） =====================

  private void validateForCreate(
      PublishedRuntimeEntity entity, long tenantId, Map<String, Object> payload) {
    // G14：state_field 独占写入保护（Runtime 设计 §4.5）——CRUD 直写一律显式拒绝（勿静默忽略，
    // 静默忽略会让调用方误以为写入成功；唯一合法入口是 POST …/actions/{code}）
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
