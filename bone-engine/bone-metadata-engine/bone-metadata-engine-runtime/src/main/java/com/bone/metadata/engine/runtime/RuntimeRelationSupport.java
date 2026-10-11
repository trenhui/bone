package com.bone.metadata.engine.runtime;

import com.bone.core.util.DistributedIdGenerator;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * 运行时关系能力（G10）：引用（lookup）候选查询与显示值补全、引用存在性校验、子表（hasMany）CRUD 与级联。
 *
 * <p><b>设计取向</b>（对标 Airtable / NocoBase / Directus 的 collection relations）：
 *
 * <ul>
 *   <li><b>子表即独立实体</b>：子表声明为一个独立 RUNTIME 实体 + 独立物理表（发布时自动建表），父表通过「子表实体上的外键列」持有关系。不做「一张宽表塞 JSON
 *       数组」——那会让子行无法被独立查询、索引与校验。
 *   <li><b>不 JOIN，只批量补全</b>：见 {@link RuntimeRelationSpec#labelKey} 的理由。分页语义与注入防线均不变。
 *   <li><b>引用完整性由写入路径校验</b>：写入父记录时校验每个 REFERENCE 值在目标实体中真实存在（同租户、未软删），避免悬空引用（对标 Directus M2O）。
 * </ul>
 *
 * <p>事务：子表写入与父记录写入由调用方（Controller）置于同一事务；本类方法自身不加事务边界，保持与 {@link JdbcRuntimeRecordService}
 * 一致的「单表行事务由上层编排」 范式。
 */
public class RuntimeRelationSupport {

  /** 与 {@code JdbcRuntimeRecordService.SYSTEM_COLUMNS} 保持一致：物理表必备但不可经业务字段写入的系统列。 */
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

  /** 物理表列缓存（v0.46 子行审计列守卫用；口径与 {@code JdbcRuntimeRecordService.physicalColumnsCache} 一致）。 */
  private final Map<String, Set<String>> physicalColumnsCache = new ConcurrentHashMap<>();

  public RuntimeRelationSupport(NamedParameterJdbcTemplate jdbc, RuntimeEntityCatalog catalog) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.catalog = Objects.requireNonNull(catalog);
  }

  // ===================== 引用（REFERENCE）=====================

  /**
   * 候选列表：给前端关联选择器用（一次拉取上限 safeLimit，防「一次拉全表」）。
   *
   * @param keyword 对 labelField 的模糊前缀匹配（可空 → 返回前 N 条）
   */
  public List<Map<String, Object>> lookupOptions(
      PublishedRuntimeEntity source,
      RuntimeFieldColumn field,
      long tenantId,
      String keyword,
      int limit,
      String rowOwnerId) {
    RuntimeRelationSpec spec = requireSpec(source, field, RuntimeRelationSpec.TYPE_REFERENCE);
    PublishedRuntimeEntity target = requireEntity(spec.targetEntityCode(), tenantId);
    if (!target.hasColumn(spec.labelField())) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RELATION_CONFIG_INVALID,
          "引用字段「"
              + field.code()
              + "」的 labelField「"
              + spec.labelField()
              + "」在目标实体「"
              + spec.targetEntityCode()
              + "」中不存在");
    }
    int safeLimit = Math.min(Math.max(1, limit), 200);
    StringBuilder sql =
        new StringBuilder("SELECT * FROM ")
            .append(quote(target.physicalTableName()))
            .append(whereFragment(target, tenantId));
    MapSqlParameterSource params = new MapSqlParameterSource();
    addTenantParam(target, tenantId, params);
    // v0.50：REFERENCE 候选行行级过滤——目标实体若有行策略（含默认 owner），仅返回操作者可见的候选行，
    // 防「引用字段指向他人私有单据时候选列表泄露全部行」（与主行列表 page 同口径）。豁免：管理员（null）/非数值主体。
    applyLookupRowFilter(target, target.physicalTableName(), sql, params, rowOwnerId);
    if (keyword != null && !keyword.isBlank()) {
      sql.append(" AND `")
          .append(sanitize(physicalOf(target, spec.labelField())))
          .append("` LIKE :kw");
      params.addValue("kw", keyword.trim() + "%");
    }
    sql.append(" ORDER BY `")
        .append(sanitize(target.primaryKeyColumn()))
        .append("` DESC LIMIT :lim");
    params.addValue("lim", safeLimit);
    return jdbc.queryForList(sql.toString(), params);
  }

  /**
   * 候选行行级过滤（lookup-options）：无 created_by 物理列（主数据/系统表）豁免；有策略（含默认 owner）按 操作者过滤候选行。与主行 page
   * 的行级过滤同口径（策略解析 + buildWhere 参数化）。
   */
  private void applyLookupRowFilter(
      PublishedRuntimeEntity target,
      String table,
      StringBuilder sql,
      MapSqlParameterSource params,
      String rowOwnerId) {
    if (rowOwnerId == null || !rowOwnerId.matches("\\d+")) {
      return; // 管理员/匿名/非数值主体豁免
    }
    if (!hasPhysicalColumn(table, "created_by")) {
      return; // 无 owner 语义实体（类别主数据等）不启用行级过滤
    }
    RowPolicySpec policy =
        RowPolicySpec.parse(target.rowPolicies(), col -> hasPhysicalColumn(table, col));
    if (policy == null) {
      // 无策略声明 → 默认 owner 语义（与主行 page 同口径）
      sql.append(" AND `created_by` = :rowOwner");
      params.addValue("rowOwner", rowOwnerId);
      return;
    }
    if (!hasPhysicalColumn(table, policy.ownerField())) {
      return; // 声明了策略但 owner 列不存在：保守跳过（等价策略失效），不抛错不泄露
    }
    sql.append(" AND ").append(policy.buildWhere(table, true, rowOwnerId));
    policy.bindParams(params, rowOwnerId);
  }

  /**
   * 批量补全引用显示值：把一批行里的 REFERENCE 字段值翻译为 {@code code__label}。
   *
   * <p><b>防 N+1</b>：每个「(目标实体, label 字段)」组合在整个批次内最多查一次（{@code IN} 查询），查询次数与行数无关。
   *
   * @return 同一 list（元素为可变 Map，原地增强）
   */
  public List<Map<String, Object>> enrichReferenceLabels(
      PublishedRuntimeEntity source, long tenantId, List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      return rows;
    }
    for (RuntimeFieldColumn col : source.columns()) {
      RuntimeRelationSpec spec = RuntimeRelationSpec.of(col);
      if (!spec.isReference()) {
        continue;
      }
      Set<Object> distinct = new LinkedHashSet<>();
      for (Map<String, Object> row : rows) {
        Object v = row.get(col.code());
        if (v != null) {
          distinct.add(v);
        }
      }
      if (distinct.isEmpty()) {
        continue;
      }
      Optional<PublishedRuntimeEntity> maybeTarget =
          catalog.findPublishedRuntime(spec.targetEntityCode(), tenantId);
      if (maybeTarget.isEmpty()) {
        // 目标实体未发布：不报错（与「配置解析失败降级」一致），label 缺失由前端显式降级展示
        continue;
      }
      Map<Object, Object> labels = loadLabels(maybeTarget.get(), spec, tenantId, distinct);
      String labelKey = RuntimeRelationSpec.labelKey(col.code());
      for (Map<String, Object> row : rows) {
        Object v = row.get(col.code());
        if (v != null) {
          row.put(labelKey, labels.get(normalizeKey(v)));
        }
      }
    }
    return rows;
  }

  /** 单行补全（详情用；内部复用批量路径，保持同一套 SQL 与降级行为）。 */
  public Map<String, Object> enrichReferenceLabel(
      PublishedRuntimeEntity source, long tenantId, Map<String, Object> row) {
    if (row != null && !row.isEmpty()) {
      List<Map<String, Object>> batch = new ArrayList<>(1);
      batch.add(row);
      enrichReferenceLabels(source, tenantId, batch);
    }
    return row;
  }

  /** 一次 IN 查询取回一批引用值的显示值（分批以规避 IN 元素上限）。 */
  private Map<Object, Object> loadLabels(
      PublishedRuntimeEntity target, RuntimeRelationSpec spec, long tenantId, Set<Object> values) {
    Map<Object, Object> result = new LinkedHashMap<>();
    String pk = sanitize(target.primaryKeyColumn());
    String labelPhysical = sanitize(physicalOf(target, spec.labelField()));
    List<Object> keys = new ArrayList<>(values);
    int batch = 200;
    for (int from = 0; from < keys.size(); from += batch) {
      int to = Math.min(from + batch, keys.size());
      List<Object> slice = keys.subList(from, to);
      MapSqlParameterSource params = new MapSqlParameterSource();
      addTenantParam(target, tenantId, params);
      StringBuilder in = new StringBuilder();
      for (int i = 0; i < slice.size(); i++) {
        String name = "k" + (from + i);
        in.append(i == 0 ? "" : ", ").append(":").append(name);
        params.addValue(name, coercePk(slice.get(i), target));
      }
      String sql =
          "SELECT `"
              + pk
              + "` AS ref_id, `"
              + labelPhysical
              + "` AS ref_label FROM "
              + quote(target.physicalTableName())
              + whereFragment(target, tenantId)
              + " AND `"
              + pk
              + "` IN ("
              + in
              + ")";
      for (Map<String, Object> row : jdbc.queryForList(sql, params)) {
        result.put(normalizeKey(row.get("ref_id")), row.get("ref_label"));
      }
    }
    return result;
  }

  /**
   * 写入路径的引用存在性校验：逐个 REFERENCE 字段确认目标记录真实存在（同租户、未软删）。
   *
   * <p><b>为什么必须校验</b>：允许悬空引用会让「类别已删除但报销单仍指向它」这类脏数据进入统计与对账，且删除目标时无拦截。
   *
   * @param payload 已过滤的写入负载（逻辑 code 键）
   */
  public void assertReferencesExist(
      PublishedRuntimeEntity source, long tenantId, Map<String, Object> payload) {
    if (payload == null || payload.isEmpty()) {
      return;
    }
    for (RuntimeFieldColumn col : source.columns()) {
      RuntimeRelationSpec spec = RuntimeRelationSpec.of(col);
      if (!spec.isReference()) {
        continue;
      }
      Object value = payload.get(col.code());
      if (value == null) {
        continue;
      }
      PublishedRuntimeEntity target = requireEntity(spec.targetEntityCode(), tenantId);
      String pk = sanitize(target.primaryKeyColumn());
      MapSqlParameterSource params = new MapSqlParameterSource();
      addTenantParam(target, tenantId, params);
      params.addValue("pk", coercePk(value, target));
      Long count =
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM "
                  + quote(target.physicalTableName())
                  + whereFragment(target, tenantId)
                  + " AND `"
                  + pk
                  + "` = :pk",
              params,
              Long.class);
      if (count == null || count == 0) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_REFERENCE_NOT_FOUND,
            "字段「" + col.code() + "」引用的记录不存在（" + spec.targetEntityCode() + "#" + value + "）");
      }
    }
  }

  // ===================== 子表（CHILD_TABLE）=====================

  /** 列出一行父记录的全部子行（按 orderColumn 排序，无则按主键）。 */
  public List<Map<String, Object>> listChildren(
      PublishedRuntimeEntity parent, long tenantId, String parentId, RuntimeRelationSpec spec) {
    PublishedRuntimeEntity child = requireEntity(spec.childEntityCode(), tenantId);
    String fkPhysical = requireChildForeignKey(child, spec);
    String order =
        spec.orderColumn() != null && child.hasColumn(spec.orderColumn())
            ? " ORDER BY `" + sanitize(physicalOf(child, spec.orderColumn())) + "` ASC"
            : " ORDER BY `" + sanitize(child.primaryKeyColumn()) + "` ASC";
    MapSqlParameterSource params = new MapSqlParameterSource();
    addTenantParam(child, tenantId, params);
    params.addValue("parentId", coercePk(parentId, parent));
    return jdbc.queryForList(
        "SELECT * FROM "
            + quote(child.physicalTableName())
            + whereFragment(child, tenantId)
            + " AND `"
            + fkPhysical
            + "` = :parentId"
            + order,
        params);
  }

  /**
   * 子表批量替换（父记录表单一次性提交明细行）。
   *
   * <p><b>为何「替换」而非「增量 diff」</b>：低代码子表在表单里就是「一张可增删改的表」，用户提交的是完整期望状态。替换语义与前端模型一致且幂等；增量 diff
   * 需维护行标识与顺序，易产生「幽灵行」。
   *
   * <p><b>级联</b>：替换时对「不再出现的旧子行」执行<b>软删</b>（非物理删），保持可恢复，与实体软删语义一致。
   *
   * @param rows 子行（逻辑字段键）；每行注入外键值与顺序值
   * @return 替换后的子行
   */
  public List<Map<String, Object>> replaceChildren(
      PublishedRuntimeEntity parent,
      long tenantId,
      String parentId,
      RuntimeRelationSpec spec,
      List<Map<String, Object>> rows) {
    PublishedRuntimeEntity child = requireEntity(spec.childEntityCode(), tenantId);
    String fkPhysical = requireChildForeignKey(child, spec);
    Object fkValue = coercePk(parentId, parent);
    List<Map<String, Object>> payloadRows = rows == null ? List.of() : rows;
    if (payloadRows.size() > spec.maxRows()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "子表「" + spec.childEntityCode() + "」行数超限（最多 " + spec.maxRows() + " 行）");
    }
    assertParentExists(parent, tenantId, parentId);

    Set<Object> keptIds = new LinkedHashSet<>();
    List<Map<String, Object>> result = new ArrayList<>(payloadRows.size());
    int order = 1;
    for (Map<String, Object> rawRow : payloadRows) {
      Map<String, Object> row = new LinkedHashMap<>(rawRow == null ? Map.of() : rawRow);
      row.put(spec.foreignKey(), fkValue);
      if (spec.orderColumn() != null && child.hasColumn(spec.orderColumn())) {
        row.putIfAbsent(spec.orderColumn(), order);
      }
      Object childId = row.get(child.primaryKeyColumn());
      if (childId == null || String.valueOf(childId).isBlank()) {
        long newId = DistributedIdGenerator.generateLongId();
        row.put(child.primaryKeyColumn(), newId);
        insertChildRow(child, tenantId, row);
        keptIds.add(String.valueOf(newId));
      } else {
        updateChildRow(child, tenantId, row, String.valueOf(childId), spec.foreignKey());
        keptIds.add(String.valueOf(childId));
      }
      result.add(row);
      order++;
    }
    if (spec.cascadeDelete()) {
      softDeleteOrphanChildren(
          child, tenantId, fkPhysical, fkValue, keptIds, spec.childEntityCode());
    }
    return result;
  }

  private void insertChildRow(
      PublishedRuntimeEntity child, long tenantId, Map<String, Object> row) {
    Map<String, Object> payload = filterChildColumns(child, row);
    // 主键由 replaceChildren 注入（DistributedIdGenerator），filter 剔除系统列后补回
    Object pkValue = row.get(child.primaryKeyColumn());
    if (pkValue != null && !payload.containsKey(child.primaryKeyColumn())) {
      payload.put(child.primaryKeyColumn(), pkValue);
    }
    // v0.46：子行写路径与主行共用同一校验器——必填/类型/min·max/ENUM 越界服务端强制，
    // 不信任前端（明细金额 min:0.01 在此生效）；唯一性查重由 replaceChildren 整批语义天然覆盖。
    RuntimeWriteValidator.validateForCreate(child, payload);
    if (entityHasColumn(child, "tenant_id")) {
      payload.putIfAbsent("tenant_id", tenantId);
    }
    if (entityHasColumn(child, "deleted")) {
      payload.putIfAbsent("deleted", 0);
    }
    Timestamp now = new Timestamp(System.currentTimeMillis());
    // 审计列按物理表实际列守卫（与主行写路径 hasPhysicalColumn 口径一致）：
    // 外部子表无审计列时容错不报错（information_schema 物理列，非实体声明列）
    if (hasPhysicalColumn(child.physicalTableName(), "created_at")) {
      payload.putIfAbsent("created_at", now);
    }
    if (hasPhysicalColumn(child.physicalTableName(), "updated_at")) {
      payload.putIfAbsent("updated_at", now);
    }
    Map<String, Object> physical = toPhysical(child, payload);
    List<String> cols = new ArrayList<>(physical.keySet());
    String sql =
        "INSERT INTO "
            + quote(child.physicalTableName())
            + " ("
            + cols.stream().map(c -> "`" + sanitize(c) + "`").collect(Collectors.joining(", "))
            + ") VALUES ("
            + cols.stream().map(c -> ":" + c).collect(Collectors.joining(", "))
            + ")";
    jdbc.update(sql, new MapSqlParameterSource(physical));
  }

  private void updateChildRow(
      PublishedRuntimeEntity child,
      long tenantId,
      Map<String, Object> row,
      String childId,
      String foreignKeyCode) {
    Map<String, Object> payload = filterChildColumns(child, row);
    payload.remove(child.primaryKeyColumn());
    // 外键列是「挂在哪个父行」的归属，禁止被前端载荷改写（否则可把行挂到别的父记录）
    payload.remove(foreignKeyCode);
    // v0.46：增量字段校验——必填不可置空/类型/min·max/ENUM 越界（与主行 update 同语义）
    RuntimeWriteValidator.validateForUpdate(child, payload);
    // v0.46：updated_at 按物理列守卫（同 insert/软删口径），外部子表无审计列时容错
    if (hasPhysicalColumn(child.physicalTableName(), "updated_at")) {
      payload.putIfAbsent("updated_at", new Timestamp(System.currentTimeMillis()));
    }
    if (payload.isEmpty()) {
      return;
    }
    Map<String, Object> physical = toPhysical(child, payload);
    MapSqlParameterSource params = new MapSqlParameterSource(physical);
    params.addValue("__pk", coercePk(childId, child));
    String setClause =
        physical.keySet().stream()
            .map(c -> "`" + sanitize(c) + "` = :" + c)
            .collect(Collectors.joining(", "));
    jdbc.update(
        "UPDATE "
            + quote(child.physicalTableName())
            + " SET "
            + setClause
            + " WHERE `"
            + sanitize(child.primaryKeyColumn())
            + "` = :__pk",
        params);
  }

  /**
   * 软删「本次提交中已不存在」的旧子行。
   *
   * <p>子表实体若无 {@code deleted} 列，则<b>拒绝</b>替换而非静默留孤儿行——残留行会被后续查询当成有效数据，比报错更难发现。
   */
  private void softDeleteOrphanChildren(
      PublishedRuntimeEntity child,
      long tenantId,
      String fkPhysical,
      Object fkValue,
      Set<Object> keptIds,
      String childEntityCode) {
    if (!entityHasColumn(child, "deleted")) {
      List<Map<String, Object>> existing = listAllChildRows(child, tenantId, fkPhysical, fkValue);
      boolean hasOrphan =
          existing.stream()
              .anyMatch(r -> !keptIds.contains(String.valueOf(r.get(child.primaryKeyColumn()))));
      if (hasOrphan) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_RELATION_CONFIG_INVALID,
            "子表实体「" + childEntityCode + "」物理表无 deleted 列，无法安全替换（会残留孤儿行）；请为子表实体启用软删列，或改为逐行增量编辑");
      }
      return;
    }
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("fk", fkValue);
    StringBuilder sql =
        new StringBuilder("UPDATE " + quote(child.physicalTableName()) + " SET `deleted` = 1");
    // v0.46：updated_at 按物理列守卫（与 insertChildRow 同口径），外部子表无审计列时容错
    if (hasPhysicalColumn(child.physicalTableName(), "updated_at")) {
      sql.append(", `updated_at` = :now");
      params.addValue("now", new Timestamp(System.currentTimeMillis()));
    }
    sql.append(" WHERE `").append(fkPhysical).append("` = :fk AND `deleted` = 0");
    addTenantClause(child, tenantId, sql, params);
    if (!keptIds.isEmpty()) {
      List<Long> numeric =
          keptIds.stream()
              .map(RuntimeRelationSupport::stringOf)
              .map(RuntimeRelationSupport::parseLongSafe)
              .filter(Objects::nonNull)
              .toList();
      if (numeric.isEmpty()) {
        return;
      }
      sql.append(" AND `").append(sanitize(child.primaryKeyColumn())).append("` NOT IN (:keep)");
      params.addValue("keep", numeric);
    }
    jdbc.update(sql.toString(), params);
  }

  private List<Map<String, Object>> listAllChildRows(
      PublishedRuntimeEntity child, long tenantId, String fkPhysical, Object fkValue) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    addTenantParam(child, tenantId, params);
    params.addValue("fk", fkValue);
    return jdbc.queryForList(
        "SELECT * FROM "
            + quote(child.physicalTableName())
            + whereFragment(child, tenantId)
            + " AND `"
            + fkPhysical
            + "` = :fk",
        params);
  }

  /** 父行删除时级联软删子行（由 {@code JdbcRuntimeRecordService#delete} 调用）。 */
  public void cascadeDeleteChildren(
      PublishedRuntimeEntity parent, long tenantId, String parentId, RuntimeRelationSpec spec) {
    PublishedRuntimeEntity child;
    try {
      child = requireEntity(spec.childEntityCode(), tenantId);
    } catch (RuntimeRecordException ignored) {
      return; // 子表实体未发布/已下线：父行删除不应被子表配置拖住
    }
    String fkPhysical = requireChildForeignKey(child, spec);
    if (!entityHasColumn(child, "deleted")) {
      return; // 无软删能力则跳过（与既有 delete 策略一致：宁可留数据也不物理删）
    }
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("fk", coercePk(parentId, parent));
    StringBuilder sql =
        new StringBuilder("UPDATE " + quote(child.physicalTableName()) + " SET `deleted` = 1");
    // v0.46：updated_at 按物理列守卫（同 insert/softDeleteOrphan 口径），外部子表无审计列时容错
    if (hasPhysicalColumn(child.physicalTableName(), "updated_at")) {
      sql.append(", `updated_at` = :now");
      params.addValue("now", new Timestamp(System.currentTimeMillis()));
    }
    sql.append(" WHERE `").append(fkPhysical).append("` = :fk AND `deleted` = 0");
    addTenantClause(child, tenantId, sql, params);
    jdbc.update(sql.toString(), params);
  }

  // ===================== 附件（ATTACHMENT）=====================

  /**
   * 附件数量上限校验（字节大小与 MIME 由 bone-file 上传端点负责；此处只管「一条记录挂几个」）。
   *
   * <p>字段物理值形态：JSON 数组字符串，元素形如 {@code
   * {"objectName":"t/1/x.png","originalName":"发票.png","contentType":"image/png","size":1024}}。
   */
  public void assertAttachmentCount(
      RuntimeFieldColumn col, RuntimeRelationSpec spec, Object value) {
    if (value == null || !spec.isAttachment()) {
      return;
    }
    List<?> items = asList(value);
    if (items.size() > spec.maxCount()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
          "字段「" + col.code() + "」附件数超限（最多 " + spec.maxCount() + " 个）");
    }
  }

  /** 把附件字段值统一成 List 形态（容忍 JSON 数组字符串 / 单值 / List）。 */
  public static List<?> asList(Object value) {
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> list) {
      return list;
    }
    if (value instanceof Object[] arr) {
      return List.of(arr);
    }
    String s = String.valueOf(value).trim();
    if (s.isEmpty()) {
      return List.of();
    }
    if (s.startsWith("[")) {
      try {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(s, List.class);
      } catch (Exception ignored) {
        return List.of(s);
      }
    }
    return List.of(s);
  }

  // ===================== 内部工具 =====================

  private void assertParentExists(PublishedRuntimeEntity parent, long tenantId, String parentId) {
    String pk = sanitize(parent.primaryKeyColumn());
    MapSqlParameterSource params = new MapSqlParameterSource();
    addTenantParam(parent, tenantId, params);
    params.addValue("pk", coercePk(parentId, parent));
    Long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM "
                + quote(parent.physicalTableName())
                + whereFragment(parent, tenantId)
                + " AND `"
                + pk
                + "` = :pk",
            params,
            Long.class);
    if (count == null || count == 0) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RECORD_NOT_FOUND, "父记录不存在: " + parentId);
    }
  }

  private PublishedRuntimeEntity requireEntity(String entityCode, long tenantId) {
    return catalog
        .findPublishedRuntime(entityCode, tenantId)
        .orElseThrow(
            () ->
                new RuntimeRecordException(
                    MetadataErrorCodes.RUNTIME_ENTITY_NOT_FOUND,
                    "关系目标实体未发布或非 RUNTIME: " + entityCode));
  }

  private RuntimeRelationSpec requireSpec(
      PublishedRuntimeEntity source, RuntimeFieldColumn field, String expectedType) {
    RuntimeRelationSpec spec = RuntimeRelationSpec.of(field);
    if (spec == null || !expectedType.equals(spec.type())) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RELATION_CONFIG_INVALID,
          "字段「"
              + source.entityCode()
              + "."
              + field.code()
              + "」未声明 "
              + expectedType
              + " 关系（请在该字段 validation_rules 中补 "
              + jsonKeyOf(expectedType)
              + " 配置）");
    }
    return spec;
  }

  private static String jsonKeyOf(String type) {
    return switch (type) {
      case RuntimeRelationSpec.TYPE_REFERENCE -> "reference";
      case RuntimeRelationSpec.TYPE_CHILD_TABLE -> "childTable";
      default -> "attachment";
    };
  }

  /** 按字段编码取关系声明；未声明该类型则抛配置错误（供 Controller 显式分派用）。 */
  public RuntimeRelationSpec specOf(
      PublishedRuntimeEntity entity, String fieldCode, String expectedType) {
    RuntimeFieldColumn col =
        entity.columns().stream()
            .filter(c -> c.code().equals(fieldCode))
            .findFirst()
            .orElseThrow(
                () ->
                    new RuntimeRecordException(
                        MetadataErrorCodes.RUNTIME_INVALID_QUERY, "字段不存在: " + fieldCode));
    return requireSpec(entity, col, expectedType);
  }

  private String requireChildForeignKey(PublishedRuntimeEntity child, RuntimeRelationSpec spec) {
    if (!child.hasColumn(spec.foreignKey())) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_RELATION_CONFIG_INVALID,
          "子表实体「" + spec.childEntityCode() + "」缺少外键列「" + spec.foreignKey() + "」");
    }
    return sanitize(physicalOf(child, spec.foreignKey()));
  }

  /**
   * 物理表实际列探测（v0.46 子行审计列守卫，口径与 {@code JdbcRuntimeRecordService.hasPhysicalColumn} 一致）： 查
   * information_schema，避免外部已有子表缺审计列时 INSERT 报 BadSqlGrammar。结果缓存避免每行查询。
   */
  private boolean hasPhysicalColumn(String table, String column) {
    return physicalColumnsCache
        .computeIfAbsent(
            table,
            t -> {
              try {
                List<Map<String, Object>> rows =
                    jdbc.getJdbcTemplate()
                        .queryForList(
                            "SELECT COLUMN_NAME FROM information_schema.columns WHERE table_name = ?",
                            t.toLowerCase());
                return rows.stream()
                    .map(r -> String.valueOf(r.get("COLUMN_NAME")))
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
              } catch (Exception e) {
                return Set.of();
              }
            })
        .contains(column.toLowerCase());
  }

  private static Map<String, Object> filterChildColumns(
      PublishedRuntimeEntity child, Map<String, Object> row) {
    Set<String> allowed =
        child.columns().stream().map(RuntimeFieldColumn::code).collect(Collectors.toSet());
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : row.entrySet()) {
      if (allowed.contains(e.getKey()) && !SYSTEM_COLUMNS.contains(e.getKey())) {
        out.put(e.getKey(), e.getValue());
      }
    }
    return out;
  }

  private static Map<String, Object> toPhysical(
      PublishedRuntimeEntity entity, Map<String, Object> logical) {
    Map<String, Object> physical = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : logical.entrySet()) {
      String key = e.getKey();
      if (SYSTEM_COLUMNS.contains(key)) {
        physical.put(key, e.getValue());
        continue;
      }
      physical.put(physicalOf(entity, key), e.getValue());
    }
    return physical;
  }

  private static String physicalOf(PublishedRuntimeEntity entity, String code) {
    return entity.columns().stream()
        .filter(c -> c.code().equals(code))
        .findFirst()
        .map(c -> c.physicalColumn() != null ? c.physicalColumn() : c.code())
        .orElse(code);
  }

  private static String whereFragment(PublishedRuntimeEntity entity, long tenantId) {
    StringBuilder sb = new StringBuilder(" WHERE 1=1");
    if (entityHasColumn(entity, "deleted")) {
      sb.append(" AND `deleted` = 0");
    }
    if (tenantId > 0 && entityHasColumn(entity, "tenant_id")) {
      sb.append(" AND `tenant_id` = :tenantId");
    }
    return sb.toString();
  }

  private static void addTenantParam(
      PublishedRuntimeEntity entity, long tenantId, MapSqlParameterSource params) {
    if (tenantId > 0 && entityHasColumn(entity, "tenant_id")) {
      params.addValue("tenantId", tenantId);
    }
  }

  private static void addTenantClause(
      PublishedRuntimeEntity entity,
      long tenantId,
      StringBuilder sql,
      MapSqlParameterSource params) {
    if (tenantId > 0 && entityHasColumn(entity, "tenant_id")) {
      sql.append(" AND `tenant_id` = :tenantId");
      params.addValue("tenantId", tenantId);
    }
  }

  private static boolean entityHasColumn(PublishedRuntimeEntity entity, String name) {
    return entity.hasColumn(name) || SYSTEM_COLUMNS.contains(name);
  }

  private static Object coercePk(Object raw, PublishedRuntimeEntity entity) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof Number) {
      return raw;
    }
    String s = String.valueOf(raw).trim();
    Long asLong = parseLongSafe(s);
    return asLong != null ? asLong : s;
  }

  private static Long parseLongSafe(String s) {
    try {
      return Long.parseLong(s.trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String stringOf(Object raw) {
    return raw == null ? "" : String.valueOf(raw);
  }

  private static Object normalizeKey(Object raw) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof Number) {
      return raw;
    }
    Long asLong = parseLongSafe(String.valueOf(raw));
    return asLong != null ? asLong : String.valueOf(raw).trim();
  }

  private static String sanitize(String name) {
    if (name == null || !name.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_INVALID_IDENTIFIER, "非法标识符: " + name);
    }
    return name;
  }

  private static String quote(String tableName) {
    return "`" + sanitize(tableName) + "`";
  }
}
