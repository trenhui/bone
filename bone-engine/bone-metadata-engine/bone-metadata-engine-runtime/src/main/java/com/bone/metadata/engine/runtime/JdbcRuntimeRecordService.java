package com.bone.metadata.engine.runtime;

import com.bone.core.model.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** 模式 B：基于已发布元数据对物理表执行动态 CRUD（经 bone-metadata-sdk 同源 JDBC）。 */
public class JdbcRuntimeRecordService {

  /** Jackson 单例：解析 auto_number 配置 JSON。 */
  private static final ObjectMapper AUTO_NUMBER_MAPPER = new ObjectMapper();

  /** 日期格式（单据号日期段）。 */
  private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

  /** Jackson 单例：用于解析字段级校验规则（validation_rules JSON）与枚举取值。 */
  /** 系统列（物理名即逻辑名）。包级可见：{@link RuntimeFieldVisibilityPolicy} 需要它做「系统列永不因角色裁剪」的防御。 */
  static final Set<String> SYSTEM_COLUMNS =
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
  private final RuntimeRelationSupport relationSupport;
  private final List<RuntimeWriteHook> writeHooks;
  private final Map<String, Set<String>> physicalColumnsCache = new ConcurrentHashMap<>();
  private final Map<String, Boolean> physicalTableExistsCache = new ConcurrentHashMap<>();
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(JdbcRuntimeRecordService.class);

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
    this(jdbc, catalog, auditSink, stateEntryResolver, null);
  }

  /**
   * 完整构造（G10 关系能力 + 写路径前置钩子）。
   *
   * @param relationSupport 关系支持（引用校验/label 补全/子表级联）；为 null 时降级为「无关系能力」，保持既有测试与调用方兼容
   * @param writeHooks 实体级写前置钩子（Directus Filter 分层，见 {@link RuntimeWriteHook}）；为空列表时不触发
   */
  public JdbcRuntimeRecordService(
      NamedParameterJdbcTemplate jdbc,
      RuntimeEntityCatalog catalog,
      RuntimeAuditSink auditSink,
      RuntimeStateEntryResolver stateEntryResolver,
      RuntimeRelationSupport relationSupport,
      List<RuntimeWriteHook> writeHooks) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.catalog = Objects.requireNonNull(catalog);
    this.auditSink = auditSink == null ? RuntimeAuditSink.NOOP : auditSink;
    this.stateEntryResolver =
        stateEntryResolver == null ? RuntimeStateEntryResolver.NONE : stateEntryResolver;
    this.relationSupport = relationSupport;
    this.writeHooks = writeHooks == null ? List.of() : writeHooks;
  }

  public JdbcRuntimeRecordService(
      NamedParameterJdbcTemplate jdbc,
      RuntimeEntityCatalog catalog,
      RuntimeAuditSink auditSink,
      RuntimeStateEntryResolver stateEntryResolver,
      RuntimeRelationSupport relationSupport) {
    this(jdbc, catalog, auditSink, stateEntryResolver, relationSupport, List.of());
  }

  public PageResult<Map<String, Object>> page(
      String entityCode, long tenantId, int page, int size) {
    return page(entityCode, tenantId, page, size, RuntimePageQuery.EMPTY);
  }

  public PageResult<Map<String, Object>> page(
      String entityCode, long tenantId, int page, int size, RuntimePageQuery query) {
    return page(entityCode, tenantId, page, size, query, null, null);
  }

  /** 无角色码重载：内部/受信调用（不做字段级裁剪），保持既有调用方行为不变。 */
  public PageResult<Map<String, Object>> page(
      String entityCode,
      long tenantId,
      int page,
      int size,
      RuntimePageQuery query,
      Long rowOwnerId) {
    return page(entityCode, tenantId, page, size, query, rowOwnerId, null);
  }

  /**
   * 分页查询（行级 ACL 版本）：{@code rowOwnerId} 非 null 且实体表存在 {@code created_by} 列时， 追加 {@code AND
   * created_by = :rowOwner} 过滤（数据面行级 ACL，Runtime §4.7.1）。
   *
   * <p>内部调用（动作执行、级联等）必须走无参版（不传 {@code rowOwnerId}），避免被数据面 ACL 拦截； 平台管理员豁免由 Controller 层传 null 实现。
   *
   * @param roleCodes 当前用户角色码；null = 内部/受信调用（不按角色裁剪，见 {@link RuntimeFieldVisibilityPolicy} 契约）
   */
  public PageResult<Map<String, Object>> page(
      String entityCode,
      long tenantId,
      int page,
      int size,
      RuntimePageQuery query,
      Long rowOwnerId,
      List<String> roleCodes) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    // 防御：实体已删且物理表已 DROP 时，缓存 TTL 内仍可能命中旧快照并对缺失表查询 → 500。
    // 显式判表存在，缺失则统一返回 404（实体不存在），避免把底层 SQL 错误暴露给用户。
    assertPhysicalTableExists(entity);
    int safePage = Math.max(1, page);
    int safeSize = Math.min(Math.max(1, size), 200);
    int offset = (safePage - 1) * safeSize;

    RuntimePageQuery effective = query == null ? RuntimePageQuery.EMPTY : query;
    String table = quoteTable(entity.physicalTableName());
    MapSqlParameterSource params = tenantParams(entity, tenantId);
    String filterClause = RuntimeQuerySupport.buildFilterClause(entity, effective, params);
    StringBuilder where =
        new StringBuilder(buildTenantWhere(entity, tenantId)).append(filterClause);
    applyRowOwnerFilter(entity, entity.physicalTableName(), where, params, rowOwnerId);

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
    // G10：引用字段显示值批量补全（每「目标实体+label字段」组合至多一次 IN 查询，与行数无关）
    enrichLabels(entity, tenantId, translated);
    // 字段级角色裁剪（N7 服务端闭环）：label 补全之后再裁，受限字段的显示值一并剔除
    if (roleCodes != null) {
      RuntimeFieldVisibilityPolicy policy = RuntimeFieldVisibilityPolicy.of(entity);
      if (policy.hasRestrictions()) {
        translated =
            translated.stream().map(r -> policy.trimRow(r, roleCodes)).collect(Collectors.toList());
      }
    }
    return PageResult.of(translated, total != null ? total : 0L, safePage, safeSize);
  }

  public Map<String, Object> getById(String entityCode, long tenantId, String recordId) {
    return getById(entityCode, tenantId, recordId, null, null);
  }

  /** 无角色码重载：内部/受信调用（不做字段级裁剪），保持既有调用方行为不变。 */
  public Map<String, Object> getById(
      String entityCode, long tenantId, String recordId, Long rowOwnerId) {
    return getById(entityCode, tenantId, recordId, rowOwnerId, null);
  }

  /**
   * 详情查询（行级 ACL 版本）：{@code rowOwnerId} 非 null 且实体表存在 {@code created_by} 列时追加 {@code AND created_by
   * = :rowOwner}，非本人记录自然查不到（404，不暴露行存在性）。
   *
   * @param roleCodes 当前用户角色码；null = 内部/受信调用（不按角色裁剪，含审计取数等需要全量字段的路径）
   */
  public Map<String, Object> getById(
      String entityCode, long tenantId, String recordId, Long rowOwnerId, List<String> roleCodes) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    assertPhysicalTableExists(entity);
    String pk = sanitizeIdentifier(entity.primaryKeyColumn());
    StringBuilder sql =
        new StringBuilder("SELECT * FROM ")
            .append(quoteTable(entity.physicalTableName()))
            .append(buildTenantWhere(entity, tenantId))
            .append(" AND `")
            .append(pk)
            .append("` = :pk");
    MapSqlParameterSource params = tenantParams(entity, tenantId);
    params.addValue("pk", parsePkValue(recordId));
    applyRowOwnerFilter(entity, entity.physicalTableName(), sql, params, rowOwnerId);
    List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), params);
    if (rows.isEmpty()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND, "记录不存在: " + recordId);
    }
    // 机制 B：物理 ext_* 列 → 逻辑 code 回填，保证返回字段名稳定
    Map<String, Object> logicalRow = toLogical(entity, rows.get(0));
    // G10：引用字段显示值补全（详情路径单行）
    enrichLabels(entity, tenantId, logicalRow);
    // 字段级角色裁剪：label 之后裁，受限字段的显示值一并剔除
    if (roleCodes != null) {
      RuntimeFieldVisibilityPolicy policy = RuntimeFieldVisibilityPolicy.of(entity);
      if (policy.hasRestrictions()) {
        logicalRow = policy.trimRow(logicalRow, roleCodes);
      }
    }
    return logicalRow;
  }

  /**
   * 写库前的<b>值形态归一化</b>：多值（ATTACHMENT 对象数组 / 多选 ENUM / JSON 配置）落库必须转成 JSON 文本。
   *
   * <p><b>为什么必须做（真实缺陷，2026-10-21 全模块联调实测）</b>：UI 的 ATTACHMENT 控件产出 {@code [{name:"xxx.pdf"}]}
   * （对象数组）。它若原样进 {@link MapSqlParameterSource}，MySQL 驱动会走 {@code setObject} 做 <b>Java 原生序列化</b>， 写入
   * {@code text} 列时报 {@code Incorrect string value: '\xAC\xED\x00\x05sr...' for column
   * 'evidences'}， 再被catch-all 兜成 <b>500 COMMON_INTERNAL_ERROR</b>——一个纯数据形态问题被当成服务端故障，用户无从下手。
   *
   * <p><b>判据是「值形态」而非「不抛异常」</b>：
   *
   * <ul>
   *   <li>多值（{@link Collection} / {@link Map} / 数组，{@code byte[]} 除外）→ 合法 JSON <b>文本</b>；
   *   <li>标量（String / Number / Boolean / 日期时间 / 枚举）→ <b>原样透传同一实例</b>，绝不被污染成 JSON 文本
   *       （否则数字列、日期列会被写成字符串，排序与比较随之失效）。
   * </ul>
   *
   * <p>{@code byte[]} 是 BLOB 二进制而非「多值」，必须透传，否则附件二进制会被毁成Base64 文本。
   *
   * @param value 原始值
   * @return 可安全交给 JDBC 的值
   */
  static Object toDbBindable(Object value) {
    if (value == null) {
      return null;
    }
    boolean multi =
        value instanceof Collection<?> || value instanceof Map<?, ?> || value.getClass().isArray();
    if (multi && !(value instanceof byte[])) {
      try {
        return AUTO_NUMBER_MAPPER.writeValueAsString(value);
      } catch (JsonProcessingException ex) {
        // 归一化失败必须显式报出「值不合法」，不能退化成 setObject 让驱动抛晦涩的 SQL 错误、更不能被兜成 500。
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
            "字段值无法序列化为 JSON: " + ex.getOriginalMessage());
      }
    }
    return value;
  }

  /** 对整行payload 的每个值做 {@link #toDbBindable(Object)} 归一化（保持列顺序）。 */
  private static Map<String, Object> normalizeValues(Map<String, Object> payload) {
    Map<String, Object> bound = new LinkedHashMap<>();
    payload.forEach((column, value) -> bound.put(column, toDbBindable(value)));
    return bound;
  }

  public Map<String, Object> create(
      String entityCode, long tenantId, Map<String, Object> body, long newId) {
    return create(entityCode, tenantId, body, newId, null, null);
  }

  /** 无角色码重载：内部/受信调用（不守卫、不裁剪），保持既有调用方行为不变。 */
  public Map<String, Object> create(
      String entityCode, long tenantId, Map<String, Object> body, long newId, String operatorId) {
    return create(entityCode, tenantId, body, newId, operatorId, null);
  }

  /**
   * 创建记录。
   *
   * @param roleCodes 当前用户角色码；null = 内部/受信调用（不守卫、不裁剪）
   */
  public Map<String, Object> create(
      String entityCode,
      long tenantId,
      Map<String, Object> body,
      long newId,
      String operatorId,
      List<String> roleCodes) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    assertStateFieldNotWritable(entity, body, "POST");
    // 字段级角色写守卫：请求体含角色不可见字段 → 403 拒绝（读写双侧闭环，见 RuntimeFieldVisibilityPolicy）
    if (roleCodes != null) {
      RuntimeFieldVisibilityPolicy.of(entity).assertWritable(entity, body, roleCodes);
    }
    Map<String, Object> payload = filterWritable(entity, body);
    injectEntryState(entity, entityCode, tenantId, payload);
    injectAutoNumber(entity, tenantId, payload);
    validateForCreate(entity, tenantId, payload);
    assertRelationConstraints(entity, tenantId, payload);
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
    invokeBeforeCreate(entity, tenantId, payload, operatorId);
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

    MapSqlParameterSource params = new MapSqlParameterSource(normalizeValues(sqlPayload));
    jdbc.update(sql, params);
    // 审计：创建整行（source=create，字段 "*" 表示整行新建，to_value=新主键）
    auditSink.record(
        RuntimeAuditEntry.of(
            tenantId,
            entityCode,
            String.valueOf(newId),
            "*",
            null,
            String.valueOf(newId),
            operatorId,
            null,
            "create",
            null));
    // 响应同样按角色裁剪：创建者也不应收到自己不可见的字段回显
    return getById(entityCode, tenantId, String.valueOf(payload.get(pk)), null, roleCodes);
  }

  public Map<String, Object> update(
      String entityCode, long tenantId, String recordId, Map<String, Object> body) {
    return update(entityCode, tenantId, recordId, body, null, null, null);
  }

  public Map<String, Object> update(
      String entityCode,
      long tenantId,
      String recordId,
      Map<String, Object> body,
      Integer expectedVersion) {
    return update(entityCode, tenantId, recordId, body, expectedVersion, null, null);
  }

  /** 无角色码重载：内部/受信调用（不守卫、不裁剪），保持既有调用方行为不变。 */
  public Map<String, Object> update(
      String entityCode,
      long tenantId,
      String recordId,
      Map<String, Object> body,
      Integer expectedVersion,
      String operatorId) {
    return update(entityCode, tenantId, recordId, body, expectedVersion, operatorId, null);
  }

  /**
   * 更新记录。
   *
   * @param roleCodes 当前用户角色码；null = 内部/受信调用（不守卫、不裁剪）
   */
  public Map<String, Object> update(
      String entityCode,
      long tenantId,
      String recordId,
      Map<String, Object> body,
      Integer expectedVersion,
      String operatorId,
      List<String> roleCodes) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    assertStateFieldNotWritable(entity, body, "PUT");
    // 字段级角色写守卫：请求体含角色不可见字段 → 403 拒绝
    if (roleCodes != null) {
      RuntimeFieldVisibilityPolicy.of(entity).assertWritable(entity, body, roleCodes);
    }
    assertRowOwner(entity, tenantId, recordId, operatorId);
    // 审计：逐字段记录数据面 UPDATE 的 from→to（source=update）；
    // 状态机流转由 updateState/adminFixState 单独审计（source=action/admin-fix），避免重复。
    // 注意：before 取全量（不裁剪）——审计轨迹必须完整，「谁改了什么」不能因字段对操作者不可见而丢失。
    Map<String, Object> before = getById(entityCode, tenantId, recordId);
    Map<String, Object> writable = filterWritable(entity, body);
    Map<String, Object> result =
        updateInternal(
            entityCode, tenantId, recordId, body, expectedVersion, operatorId, roleCodes);
    if (!writable.isEmpty()) {
      for (Map.Entry<String, Object> e : writable.entrySet()) {
        String f = e.getKey();
        if (SYSTEM_COLUMNS.contains(f)) {
          continue;
        }
        auditSink.record(
            RuntimeAuditEntry.of(
                tenantId,
                entityCode,
                recordId,
                f,
                str(before.get(f)),
                str(e.getValue()),
                operatorId,
                null,
                "update",
                null));
      }
    }
    return result;
  }

  /**
   * 写路径内部实现（<strong>不做</strong> {@code state_field} 保护）——仅被 {@link #update}（已保护） 与 {@link
   * #updateState} / {@link #adminFixState}（状态机合法入口）调用。
   *
   * @param roleCodes 透传给响应侧 getById 的裁剪；null = 内部/受信调用
   */
  private Map<String, Object> updateInternal(
      String entityCode,
      long tenantId,
      String recordId,
      Map<String, Object> body,
      Integer expectedVersion,
      String operatorId,
      List<String> roleCodes) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    Map<String, Object> payload = filterWritable(entity, body);
    payload.remove(entity.primaryKeyColumn());
    validateForUpdate(entity, tenantId, payload, recordId);
    assertRelationConstraints(entity, tenantId, payload);
    invokeBeforeUpdate(entity, tenantId, recordId, payload, operatorId);

    boolean versioned = entityHasColumn(entity, "version");
    if (expectedVersion != null && !versioned) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_INVALID_QUERY, "物理表无 version 列，不支持 If-Match");
    }

    if (payload.isEmpty() && !(versioned && expectedVersion != null)) {
      return getById(entityCode, tenantId, recordId, null, roleCodes);
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

    MapSqlParameterSource params = new MapSqlParameterSource(normalizeValues(sqlPayload));
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
    return getById(entityCode, tenantId, recordId, null, roleCodes);
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
    assertRowOwner(entity, tenantId, recordId, operatorId);
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
      // G10：父行软删成功后级联软删子行（cascadeDelete=true 时），避免子行成为孤儿
      cascadeChildrenOnDelete(entity, tenantId, recordId);
      return;
    }
    // 无 deleted 列：物理删除（不可恢复）。不静默执行——「删除不可恢复」必须是明示而非默认行为
    // （Runtime §8 R13 / G17）：拒绝并提示启用软删能力，防误删永久丢数据。
    throw new RuntimeRecordException(
        MetadataErrorCodes.RUNTIME_INVALID_QUERY,
        "物理表无 deleted 列（软删未生效），该实体的删除为物理删除且不可恢复；" + "如需归档能力请先为实体启用软删（deleted 列），当前已拒绝删除");
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
    return restore(entityCode, tenantId, recordId, operatorId, null);
  }

  /**
   * 恢复已归档记录（回收站 → 在用）。
   *
   * @param roleCodes 当前用户角色码；null = 内部/受信调用（响应不裁剪）
   */
  public Map<String, Object> restore(
      String entityCode,
      long tenantId,
      String recordId,
      String operatorId,
      List<String> roleCodes) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    assertRowOwner(entity, tenantId, recordId, operatorId);
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
    return getById(entityCode, tenantId, recordId, null, roleCodes);
  }

  /**
   * 回收站视图：只查 {@code deleted = 1} 的行。
   *
   * <p>与 {@link #page} 的唯一差别是租户谓词里的 deleted 取值；复用同一套列投影 / 过滤 / 排序， 避免「回收站和数据列表两套查询逻辑」的漂移。
   */
  public PageResult<Map<String, Object>> pageDeleted(
      String entityCode, long tenantId, int page, int size, RuntimePageQuery query) {
    return pageDeleted(entityCode, tenantId, page, size, query, null);
  }

  /**
   * 回收站视图：只查 {@code deleted = 1} 的行。
   *
   * <p>与 {@link #page} 的唯一差别是租户谓词里的 deleted 取值；复用同一套列投影 / 过滤 / 排序， 避免「回收站和数据列表两套查询逻辑」的漂移。
   *
   * @param roleCodes 当前用户角色码；null = 内部/受信调用（不裁剪）
   */
  public PageResult<Map<String, Object>> pageDeleted(
      String entityCode,
      long tenantId,
      int page,
      int size,
      RuntimePageQuery query,
      List<String> roleCodes) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    assertPhysicalTableExists(entity);
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
    // 字段级角色裁剪：回收站与数据列表同一套约束，不能一个漏一个
    if (roleCodes != null) {
      RuntimeFieldVisibilityPolicy policy = RuntimeFieldVisibilityPolicy.of(entity);
      if (policy.hasRestrictions()) {
        translated =
            translated.stream().map(r -> policy.trimRow(r, roleCodes)).collect(Collectors.toList());
      }
    }
    return PageResult.of(translated, total != null ? total : 0L, safePage, safeSize);
  }

  /**
   * <strong>状态机唯一合法推进入口</strong>（RC-008）：更新受保护的状态字段，并落记录级审计。
   *
   * <p>与 {@link #update} 的区别：{@code update} 对 {@code state_field} 一律 422； 本方法只被 {@code
   * RuntimeActionApplicationService} 在校验过转移矩阵后调用，跳过保护并记录 {@code from -> to}。
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
    return updateState(
        entityCode,
        tenantId,
        recordId,
        stateField,
        toStatus,
        expectedVersion,
        operatorId,
        actionCode,
        reason,
        null);
  }

  /**
   * 状态机推进入口（带角色码）：动作端点是用户面路径，响应同样按角色裁剪。
   *
   * @param roleCodes 当前用户角色码；null = 内部/受信调用（不裁剪）
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
      String reason,
      List<String> roleCodes) {
    PublishedRuntimeEntity entity = requireRuntimeEntity(entityCode, tenantId);
    Map<String, Object> current = getById(entityCode, tenantId, recordId);
    Object before = current.get(stateField);
    String from = before == null ? null : before.toString();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put(stateField, toStatus);
    Map<String, Object> updated =
        updateInternal(
            entityCode, tenantId, recordId, payload, expectedVersion, operatorId, roleCodes);
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
    return adminFixState(
        entityCode, tenantId, recordId, stateField, toStatus, operatorId, reason, null);
  }

  /** 管理员修复直写（带角色码）：响应按调用者角色裁剪。 */
  public Map<String, Object> adminFixState(
      String entityCode,
      long tenantId,
      String recordId,
      String stateField,
      String toStatus,
      String operatorId,
      String reason,
      List<String> roleCodes) {
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
        updateInternal(entityCode, tenantId, recordId, payload, null, operatorId, roleCodes);
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
   * 平台级自动编号注入（0043）：实体声明 {@code auto_number} 配置时，若客户端未提供承载字段（或为空）， 由平台按「前缀-日期-序号」生成可读单据号并注入，替换
   * PAY-<nanoTime> 之类的占位。
   *
   * <p><b>不覆盖客户端值</b>：若客户端已显式传该字段，保留其值（平台不替用户决定单据号，避免破坏既有流程）； 仅作「补默认」式注入。序号原子性由 {@link
   * #nextSequence} 的 INSERT ... ON DUPLICATE KEY UPDATE 行锁保证，无需 SELECT FOR UPDATE。
   */
  private void injectAutoNumber(
      PublishedRuntimeEntity entity, long tenantId, Map<String, Object> payload) {
    String cfgJson = entity.autoNumber();
    if (cfgJson == null || cfgJson.isBlank()) {
      return;
    }
    AutoNumberConfig cfg = parseAutoNumber(cfgJson);
    if (cfg == null) {
      return;
    }
    Object clientVal = payload.get(cfg.field());
    boolean missing = clientVal == null || (clientVal instanceof String s && s.isBlank());
    if (missing) {
      payload.put(cfg.field(), generateAutoNumber(tenantId, entity.entityCode(), cfg));
    }
  }

  /** 生成单据号：daily 重置→{prefix}-{yyyyMMdd}-{NNNN}；否则→{prefix}-{NNNN}（全局单调递增）。 */
  private String generateAutoNumber(long tenantId, String entityCode, AutoNumberConfig cfg) {
    String datePart = cfg.resetDaily() ? LocalDate.now().format(DAY_FMT) : "0";
    String seqKey = tenantId + ":" + entityCode + ":" + cfg.field() + ":" + datePart;
    long seq = nextSequence(seqKey);
    String padded = String.format("%04d", seq);
    return cfg.resetDaily()
        ? cfg.prefix() + "-" + datePart + "-" + padded
        : cfg.prefix() + "-" + padded;
  }

  /**
   * 原子取号：以 {@code seq_key} 为键自增并返回新值。InnoDB 下 INSERT ... ON DUPLICATE KEY UPDATE 对冲突键加 next-key 锁，
   * 并发调用串行化，等价于「读-改-写」原子，避免 SELECT FOR UPDATE（Bone 架构约束）。
   */
  private long nextSequence(String seqKey) {
    jdbc.update(
        "INSERT INTO meta_runtime_sequence (seq_key, current_value) VALUES (:key, 1) "
            + "ON DUPLICATE KEY UPDATE current_value = current_value + 1",
        Map.of("key", seqKey));
    Long v =
        jdbc.queryForObject(
            "SELECT current_value FROM meta_runtime_sequence WHERE seq_key = :key",
            Map.of("key", seqKey),
            Long.class);
    return v != null ? v : 1;
  }

  /** 解析 auto_number JSON 配置；格式非法或字段缺失时返回 null（静默降级为不注入）。 */
  private static AutoNumberConfig parseAutoNumber(String json) {
    try {
      JsonNode n = AUTO_NUMBER_MAPPER.readTree(json);
      String field = n.path("field").asText(null);
      String prefix = n.path("prefix").asText(null);
      String reset = n.path("reset").asText("none");
      if (field == null || prefix == null || field.isBlank() || prefix.isBlank()) {
        return null;
      }
      return new AutoNumberConfig(field, prefix, reset);
    } catch (Exception ignored) {
      return null;
    }
  }

  /** 自动编号配置：承载字段 / 前缀 / 重置粒度。 */
  private record AutoNumberConfig(String field, String prefix, String reset) {
    boolean resetDaily() {
      return "daily".equalsIgnoreCase(reset);
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

  // ===================== G10 关系能力挂钩 =====================

  /** 引用字段显示值批量补全；未装配 relationSupport 或实体无引用字段时零开销返回。 */
  private void enrichLabels(
      PublishedRuntimeEntity entity, long tenantId, List<Map<String, Object>> rows) {
    if (relationSupport == null || rows == null || rows.isEmpty()) {
      return;
    }
    if (entity.columns().stream().noneMatch(c -> RuntimeRelationSpec.of(c).isReference())) {
      return;
    }
    relationSupport.enrichReferenceLabels(entity, tenantId, rows);
  }

  /** 引用字段显示值补全（详情单行）。 */
  private void enrichLabels(PublishedRuntimeEntity entity, long tenantId, Map<String, Object> row) {
    if (relationSupport == null || row == null || row.isEmpty()) {
      return;
    }
    relationSupport.enrichReferenceLabel(entity, tenantId, row);
  }

  /**
   * 写入前的关系校验：引用存在性（阻断悬空引用）+ 附件数量上限。
   *
   * <p>在字段轻校验之后调用，保证「先字段后关系」的错误优先级。
   */
  /** 触发匹配实体的创建前钩子（Directus Filter 分层，见 {@link RuntimeWriteHook}）。 */
  private void invokeBeforeCreate(
      PublishedRuntimeEntity entity,
      long tenantId,
      Map<String, Object> payload,
      String operatorId) {
    for (RuntimeWriteHook hook : writeHooks) {
      if (hook.entityCode().equals(entity.entityCode())) {
        hook.beforeCreate(new RuntimeWriteHookContext(entity, tenantId, null, payload, operatorId));
      }
    }
  }

  /** 触发匹配实体的更新前钩子（可阻断或改写载荷）。 */
  private void invokeBeforeUpdate(
      PublishedRuntimeEntity entity,
      long tenantId,
      String recordId,
      Map<String, Object> payload,
      String operatorId) {
    for (RuntimeWriteHook hook : writeHooks) {
      if (hook.entityCode().equals(entity.entityCode())) {
        hook.beforeUpdate(
            new RuntimeWriteHookContext(entity, tenantId, recordId, payload, operatorId));
      }
    }
  }

  private void assertRelationConstraints(
      PublishedRuntimeEntity entity, long tenantId, Map<String, Object> payload) {
    if (relationSupport == null) {
      return;
    }
    for (RuntimeFieldColumn col : entity.columns()) {
      RuntimeRelationSpec spec = RuntimeRelationSpec.of(col);
      if (!spec.isAttachment()) {
        continue;
      }
      Object value = payload.get(col.code());
      if (value != null) {
        relationSupport.assertAttachmentCount(col, spec, value);
      }
    }
    relationSupport.assertReferencesExist(entity, tenantId, payload);
  }

  /** 父行软删/恢复时级联处理子表（级联软删/ 恢复由 delete 调用；恢复暂不级联，保持保守）。 */
  private void cascadeChildrenOnDelete(
      PublishedRuntimeEntity entity, long tenantId, String recordId) {
    if (relationSupport == null) {
      return;
    }
    for (RuntimeFieldColumn col : entity.columns()) {
      RuntimeRelationSpec spec = RuntimeRelationSpec.of(col);
      if (!spec.isChildTable() || !spec.cascadeDelete()) {
        continue;
      }
      relationSupport.cascadeDeleteChildren(entity, tenantId, recordId, spec);
    }
  }

  // ===================== 写入校验（P0-5 · v0.46 抽公共校验器） =====================

  /**
   * 必填/类型/元数据规则（min/max/ENUM）校验委托 {@link RuntimeWriteValidator}（与子表写路径单一真源）； 唯一性查重因需按行定位查库，留在本类实现。
   */
  private void validateForCreate(
      PublishedRuntimeEntity entity, long tenantId, Map<String, Object> payload) {
    RuntimeWriteValidator.validateForCreate(entity, payload);
    for (RuntimeFieldColumn col : entity.columns()) {
      if (col.unique() && payload.containsKey(col.code()) && payload.get(col.code()) != null) {
        assertUnique(entity, tenantId, col, payload.get(col.code()), null);
      }
    }
  }

  private void validateForUpdate(
      PublishedRuntimeEntity entity, long tenantId, Map<String, Object> payload, String recordId) {
    RuntimeWriteValidator.validateForUpdate(entity, payload);
    for (RuntimeFieldColumn col : entity.columns()) {
      if (col.unique() && payload.containsKey(col.code()) && payload.get(col.code()) != null) {
        assertUnique(entity, tenantId, col, payload.get(col.code()), recordId);
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
   *
   * <p><b>{@code created_by} 只在创建时盖章，更新一律不得覆写</b>：该列同时是行级 ACL 的默认 owner 字段 （{@link RowPolicySpec}
   * 默认 {@code owner_field=created_by}），被覆写即等于「单据归属被偷偷改判」——审批人 approve 一次， 创建人立刻失去对本单及子表明细的访问（子表端点前置
   * {@link #assertRowOwner} 报 404），界面提交规则读到 0 行明细误判 SUB-COUNT-01/SUB-ITEMS-01。创建人归属与 {@code
   * updated_by}（真正的操作人留痕）职责不同，二者不可混用。
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
    // created_by = 行属主：仅创建时写入；更新路径保留原值（见方法注释的归属被改判缺陷）
    if ("system".equals(operatorId)) {
      // 系统操作者（服务/渠道回调身份）：审计留痕为字符串常量（非数值主体不落 BIGINT）
      if (isCreate && hasPhysicalColumn(table, "created_by")) {
        payload.put("created_by", "system");
      }
      if (hasPhysicalColumn(table, "updated_by")) {
        payload.put("updated_by", "system");
      }
      return;
    }
    // 操作者：仅当可解析为数值用户 ID 时注入 BIGINT 列
    Long numericOp = toNumericOperatorId(operatorId);
    if (numericOp != null) {
      if (isCreate && hasPhysicalColumn(table, "created_by")) {
        payload.put("created_by", numericOp);
      }
      if (hasPhysicalColumn(table, "updated_by")) {
        payload.put("updated_by", numericOp);
      }
    }
  }

  /**
   * 行级 ACL 过滤注入（数据面）：{@code rowOwnerId} 非 null 且表存在 {@code created_by} 列时追加 {@code AND created_by =
   * :rowOwner}。无 created_by 列的实体（系统表/主数据）自动豁免； 平台管理员豁免由 Controller 层传 null 实现（Runtime §4.7.1）。
   */
  private void applyRowOwnerFilter(
      PublishedRuntimeEntity entity,
      String table,
      StringBuilder where,
      MapSqlParameterSource params,
      Long rowOwnerId) {
    if (rowOwnerId == null) {
      return;
    }
    RowPolicySpec policy = resolveRowPolicy(entity, table);
    if (policy == null) {
      // 无策略声明 → 默认 owner 语义：存在 created_by 物理列即按本人过滤
      if (hasPhysicalColumn(table, "created_by")) {
        where.append(" AND `created_by` = :rowOwner");
        params.addValue("rowOwner", rowOwnerId);
      }
      return;
    }
    if (!hasPhysicalColumn(table, policy.ownerField())) {
      // 声明了策略但 owner 列不存在：保守跳过（不注入过滤 = 等价于该策略失效），告警暴露配置错误
      log.warn("行策略 owner_field={} 在表 无物理列，跳过行级过滤注入", policy.ownerField(), table);
      return;
    }
    String op = Long.toString(rowOwnerId);
    where.append(" AND ").append(policy.buildWhere(table, true, op));
    policy.bindParams(params, op);
  }

  /** 解析实体行策略（缓存无则每次解析；列校验 = 实体声明 + 物理列存在）。 */
  private RowPolicySpec resolveRowPolicy(PublishedRuntimeEntity entity, String table) {
    String raw = entity.rowPolicies();
    if (raw == null || raw.isBlank()) {
      return null;
    }
    // 白名单 = 物理列集合：when/owner_field 仅允许「物理表真实存在的列」用于 WHERE 注入。
    // 注意不能用 entity.hasColumn —— 审计列（created_by/updated_by）与扩展物理列（ext_*）不在实体声明
    // 列集合中，但行级 ACL 的 WHERE 注入只需要物理列存在即可（读过滤，非写载荷校验）。
    return RowPolicySpec.parse(raw, col -> hasPhysicalColumn(table, col));
  }

  /**
   * 写路径行级 ACL 断言（数据面）：非本人记录拒绝（404，不暴露行存在性）。
   *
   * <p>仅作用于数据面 CRUD（public update/delete/restore 入口）；状态机内部流转（updateState/adminFixState） 走
   * updateInternal 不经此断言，动作面由动作级权限独立管控，避免审批人无法操作他人单据（Runtime §4.7.1）。
   *
   * <p><b>v0.50 扩展用途</b>：子表端点（listChildren/replaceChildren）复用本断言校验<b>主行归属</b>（子表可见性由父行控制， 避免「知道父行
   * id 即可枚举/替换任意明细」的行级 ACL 旁路）。包内 private 提升为 public 供 {@code RuntimeRelationController}
   * 编排复用；豁免口径与主行一致（null 管理员 / "system" 回调 / 非数值主体）。
   */
  public void assertRowOwner(
      PublishedRuntimeEntity entity, long tenantId, String recordId, String operatorId) {
    if (operatorId == null) {
      return; // 未登录/匿名/平台管理员豁免（Controller 传 null）
    }
    if ("system".equals(operatorId)) {
      return; // 系统操作者（服务/渠道回调身份）：豁免行级 ACL，审计留痕见 stampAuditColumns
    }
    Long ownerId = toNumericOperatorId(operatorId);
    if (ownerId == null) {
      return; // 非数值主体：与 BIGINT created_by 无可比性，按既有审计语义跳过
    }
    String table = entity.physicalTableName();
    String op = Long.toString(ownerId);
    RowPolicySpec policy = resolveRowPolicy(entity, table);
    if (policy == null) {
      assertOwnerOnly(entity, table, tenantId, recordId, ownerId);
      return;
    }
    if (!hasPhysicalColumn(table, policy.ownerField())) {
      log.warn("行策略 owner_field={} 在表 无物理列，写路径跳过行级断言", policy.ownerField(), table);
      return;
    }
    String pk = sanitizeIdentifier(entity.primaryKeyColumn());
    // 策略 WHERE：owner_field 规则 + 自定义规则（approver 等）；手写租户过滤（不用 buildTenantWhere：
    // 其自带 deleted=0，会误杀 restore 场景的已归档行）——行级校验是访问控制而非状态过滤
    StringBuilder sql =
        new StringBuilder("SELECT 1 FROM ")
            .append(quoteTable(table))
            .append(
                entityHasColumn(entity, "tenant_id")
                    ? " WHERE `tenant_id` = :tenantId"
                    : " WHERE 1=1")
            .append(" AND `")
            .append(pk)
            .append("` = :pk")
            .append(" AND ")
            .append(policy.buildWhere(table, true, op));
    MapSqlParameterSource params = new MapSqlParameterSource();
    if (entityHasColumn(entity, "tenant_id")) {
      params.addValue("tenantId", tenantId);
    }
    params.addValue("pk", parsePkValue(recordId));
    policy.bindParams(params, op);
    try {
      jdbc.queryForObject(sql.toString(), params, Integer.class);
    } catch (EmptyResultDataAccessException ex) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND, "记录不存在: " + recordId);
    }
  }

  /** 无策略声明的默认 owner 断言（created_by 单列校验）。 */
  private void assertOwnerOnly(
      PublishedRuntimeEntity entity, String table, long tenantId, String recordId, Long ownerId) {
    if (!hasPhysicalColumn(table, "created_by")) {
      return; // 无 owner 语义的实体（系统表/主数据）不启用行级 ACL
    }
    String pk = sanitizeIdentifier(entity.primaryKeyColumn());
    // 手写租户过滤（不用 buildTenantWhere：其自带 deleted=0，会误杀 restore 场景的已归档行）——
    // owner 校验是访问控制而非状态过滤，与 deleted/version 解耦
    StringBuilder sql =
        new StringBuilder("SELECT `created_by` FROM ")
            .append(quoteTable(table))
            .append(
                entityHasColumn(entity, "tenant_id")
                    ? " WHERE `tenant_id` = :tenantId"
                    : " WHERE 1=1")
            .append(" AND `")
            .append(pk)
            .append("` = :pk");
    MapSqlParameterSource params = new MapSqlParameterSource();
    if (entityHasColumn(entity, "tenant_id")) {
      params.addValue("tenantId", tenantId);
    }
    params.addValue("pk", parsePkValue(recordId));
    String rowOwner;
    try {
      rowOwner = jdbc.queryForObject(sql.toString(), params, String.class);
    } catch (EmptyResultDataAccessException ex) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND, "记录不存在: " + recordId);
    }
    // 字符串比较：兼容 BIGINT（数字字符串）与 VARCHAR(64)（测试环境）两类 created_by 物理形态
    if (!ownerId.toString().equals(rowOwner)) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND, "记录不存在: " + recordId);
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

  /**
   * 防御性判表：实体已删且物理表已 DROP 时，缓存 TTL 内仍可能命中旧快照并对缺失表发起查询（500）。 显式判表存在，缺失则统一抛 {@code
   * RUNTIME_ENTITY_NOT_FOUND}（404），把底层 SQL 错误收敛为干净的错误响应。
   *
   * <p>探测失败（如 information_schema 不可达）降级为「表存在」，不影响正常查询——只拦「确定缺失」的情况。
   */
  private void assertPhysicalTableExists(PublishedRuntimeEntity entity) {
    if (!physicalTableExists(entity.physicalTableName())) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_ENTITY_NOT_FOUND, "实体物理表不存在（可能已被删除）: " + entity.entityCode());
    }
  }

  private boolean physicalTableExists(String table) {
    return physicalTableExistsCache.computeIfAbsent(
        table,
        t -> {
          try {
            // LIMIT 1 零全表扫描；空表抛 EmptyResultDataAccessException（仍判定为「存在」），
            // 仅当表本身不存在（SQL 语法错误）时抛其他异常→判缺失，使查询返回 404。
            jdbc.getJdbcTemplate()
                .queryForObject("SELECT 1 FROM " + quoteTable(t) + " LIMIT 1", Integer.class);
            return true;
          } catch (EmptyResultDataAccessException ignored) {
            return true; // 空表也是存在
          } catch (Exception ignored) {
            return false; // 表不存在（已 DROP）→ 缺失
          }
        });
  }

  /**
   * 过滤出「可直接写主行」的列，并<b>显式拒绝 CHILD_TABLE 声明字段</b>（G10 写侧对齐）。
   *
   * <p><b>为什么必须显式拒绝而不是静默丢弃</b>：CHILD_TABLE 是<b>伪列</b>（无物理列）。若随主行载荷直写，它会原样进 INSERT → MySQL {@code
   * Unknown column 'items' in 'field list'} → 被catch-all 兜成 <b>500
   * COMMON_INTERNAL_ERROR</b>（纯数据形态问题被当成服务端故障）。而静默丢弃同样不可接受——那等于调用方提交的明细数据
   * <b>无声丢失</b>，比报错更危险。故口径是：400 + 指引改走子表端点。
   *
   * <p>UI 主表单本就不提交 CHILD_TABLE（明细走子表端点整组替换），本守卫拦的是 API 直写与第三方集成。
   *
   * <p><b>可见性为包内可见</b>：同包回归测试直接调用本方法锁死上述语义（见 {@code RuntimeWriteChildTableGuardTest}）。
   */
  static Map<String, Object> filterWritable(
      PublishedRuntimeEntity entity, Map<String, Object> body) {
    if (body == null || body.isEmpty()) {
      return new LinkedHashMap<>();
    }
    Set<String> childTables =
        entity.columns().stream()
            .filter(column -> RuntimeRelationSpec.TYPE_CHILD_TABLE.equals(column.type()))
            .map(RuntimeFieldColumn::code)
            .collect(Collectors.toSet());
    Set<String> allowed =
        entity.columns().stream().map(RuntimeFieldColumn::code).collect(Collectors.toSet());
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : body.entrySet()) {
      String key = e.getKey();
      if (childTables.contains(key)) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
            "字段 "
                + key
                + " 是 CHILD_TABLE 子表字段（无物理列），不能随主行载荷写入；"
                + "明细请改走子表端点整组替换：POST /api/v1/runtime/entities/{entityCode}/records/{id}/children/{childEntityCode}");
      }
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

  /** 审计值字符串化：null → null；其余取 toString（超长由 sink 截断）。 */
  private static String str(Object v) {
    return v == null ? null : v.toString();
  }
}
