package com.bone.metadata.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行时关系配置（G10 · 关联/子表/附件）。
 *
 * <p><b>零 DDL 的配置承载</b>：关系声明复用 {@code meta_field.validation_rules} 这一已有 JSON 列，<b>不新增任何表或列</b>。
 * 三种字段类型各占一个顶层键，互不干扰：
 *
 * <pre>
 *   REFERENCE    {"reference":    {"targetEntityCode":"t_expense_category","labelField":"name","filterField":"enabled","filterValue":"Y"}}
 *   CHILD_TABLE  {"childTable":   {"childEntityCode":"t_expense_claim_item","foreignKey":"claim_id","orderColumn":"sort_order","cascadeDelete":true,"minRows":0,"maxRows":200}}
 *   ATTACHMENT   {"attachment":   {"maxCount":10,"maxSizeMB":10,"accept":["image/*","application/pdf"]}}
 * </pre>
 *
 * <p><b>为什么不放 {@code enum_values}</b>：该列语义是「枚举取值白名单」，写入路径 {@code
 * JdbcRuntimeRecordService#validateRules} 会消费它做ENUM 越界校验；关系配置放进去会被误当作枚举候选。
 *
 * <p><b>解析失败一律降级为「无关系声明」</b>（返回 {@link #none()}）而非抛错：建模层保证配置合法性，运行期因脏数据拒绝整表读写不可接受——这与既有 {@code
 * validateRules} / {@code isEnumValueAllowed} 的降级策略一致（Runtime 设计 §0.3「诚实性」原则的代码侧对应）。
 *
 * <p><b>缓存</b>：解析结果按 {@code type + validationRules} 缓存。catalog 快照本身已有 Caffeine/Redis 二级缓存，故此处只需进程内
 * Map 承接重复解析，避免每行每字段反复走 Jackson。
 */
public final class RuntimeRelationSpec {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 类型：单值引用（lookup）。物理列存目标记录主键值（通常 BIGINT）。 */
  public static final String TYPE_REFERENCE = "REFERENCE";

  /** 类型：子表（hasMany）。父表行与子表行通过「子表实体上的外键列」关联。 */
  public static final String TYPE_CHILD_TABLE = "CHILD_TABLE";

  /** 类型：附件列表。物理列存 JSON 数组字符串（对象元数据，字节存 bone-file/MinIO）。 */
  public static final String TYPE_ATTACHMENT = "ATTACHMENT";

  private static final ConcurrentHashMap<String, RuntimeRelationSpec> CACHE =
      new ConcurrentHashMap<>();

  private static final RuntimeRelationSpec NONE =
      new RuntimeRelationSpec(null, null, null, null, null, null, null, null, false, 0, 0, null);

  private final String type;
  private final String targetEntityCode;
  private final String labelField;
  private final String filterField;
  private final String filterValue;
  private final String childEntityCode;
  private final String foreignKey;
  private final String orderColumn;
  private final boolean cascadeDelete;
  private final int maxRows;
  private final int maxCount;
  private final java.util.List<String> accept;

  private RuntimeRelationSpec(
      String type,
      String targetEntityCode,
      String labelField,
      String filterField,
      String filterValue,
      String childEntityCode,
      String foreignKey,
      String orderColumn,
      boolean cascadeDelete,
      int maxRows,
      int maxCount,
      java.util.List<String> accept) {
    this.type = type;
    this.targetEntityCode = targetEntityCode;
    this.labelField = labelField;
    this.filterField = filterField;
    this.filterValue = filterValue;
    this.childEntityCode = childEntityCode;
    this.foreignKey = foreignKey;
    this.orderColumn = orderColumn;
    this.cascadeDelete = cascadeDelete;
    this.maxRows = maxRows;
    this.maxCount = maxCount;
    this.accept = accept;
  }

  /** 无关系声明（普通字段，或配置解析失败降级）。 */
  public static RuntimeRelationSpec none() {
    return NONE;
  }

  /** 按字段类型 + validation_rules 解析（带进程内缓存）。 */
  public static RuntimeRelationSpec of(RuntimeFieldColumn col) {
    if (col == null) {
      return NONE;
    }
    String type = col.type() == null ? "" : col.type().toUpperCase();
    boolean interesting =
        TYPE_REFERENCE.equals(type)
            || TYPE_CHILD_TABLE.equals(type)
            || TYPE_ATTACHMENT.equals(type);
    if (!interesting) {
      return NONE;
    }
    String rules = col.validationRules();
    if (rules == null || rules.isBlank()) {
      // 类型已声明但无配置：REFERENCE/CHILD_TABLE 缺目标实体无法工作，按降级处理（不抛错）
      return NONE;
    }
    return CACHE.computeIfAbsent(type + "|" + rules, key -> parse(type, rules));
  }

  private static RuntimeRelationSpec parse(String type, String rules) {
    try {
      JsonNode root = MAPPER.readTree(rules);
      if (root == null || !root.isObject()) {
        return NONE;
      }
      switch (type) {
        case TYPE_REFERENCE:
          return parseReference(root.path("reference"));
        case TYPE_CHILD_TABLE:
          return parseChildTable(root.path("childTable"));
        case TYPE_ATTACHMENT:
          return parseAttachment(root.path("attachment"));
        default:
          return NONE;
      }
    } catch (Exception ignored) {
      return NONE;
    }
  }

  private static RuntimeRelationSpec parseReference(JsonNode node) {
    if (node == null || !node.isObject()) {
      return NONE;
    }
    String target = text(node, "targetEntityCode");
    if (target == null || target.isBlank()) {
      return NONE;
    }
    String labelField = text(node, "labelField");
    return new RuntimeRelationSpec(
        TYPE_REFERENCE,
        target,
        labelField == null || labelField.isBlank() ? "id" : labelField,
        text(node, "filterField"),
        text(node, "filterValue"),
        null,
        null,
        null,
        false,
        0,
        0,
        null);
  }

  private static RuntimeRelationSpec parseChildTable(JsonNode node) {
    if (node == null || !node.isObject()) {
      return NONE;
    }
    String child = text(node, "childEntityCode");
    String fk = text(node, "foreignKey");
    if (child == null || child.isBlank() || fk == null || fk.isBlank()) {
      return NONE;
    }
    String order = text(node, "orderColumn");
    return new RuntimeRelationSpec(
        TYPE_CHILD_TABLE,
        null,
        null,
        null,
        null,
        child,
        fk,
        order == null || order.isBlank() ? null : order,
        node.path("cascadeDelete").asBoolean(true),
        node.path("maxRows").asInt(500),
        0,
        null);
  }

  private static RuntimeRelationSpec parseAttachment(JsonNode node) {
    if (node == null || !node.isObject()) {
      return NONE;
    }
    java.util.List<String> accept = new java.util.ArrayList<>();
    JsonNode acceptNode = node.path("accept");
    if (acceptNode.isArray()) {
      acceptNode.forEach(n -> accept.add(n.asText()));
    }
    return new RuntimeRelationSpec(
        TYPE_ATTACHMENT,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        0,
        node.path("maxCount").asInt(20),
        java.util.List.copyOf(accept));
  }

  private static String text(JsonNode node, String field) {
    JsonNode v = node.get(field);
    return v == null || v.isNull() ? null : v.asText();
  }

  // ===================== 访问器 =====================

  public String type() {
    return type;
  }

  public boolean isReference() {
    return TYPE_REFERENCE.equals(type);
  }

  public boolean isChildTable() {
    return TYPE_CHILD_TABLE.equals(type);
  }

  public boolean isAttachment() {
    return TYPE_ATTACHMENT.equals(type);
  }

  public boolean declared() {
    return type != null;
  }

  public String targetEntityCode() {
    return targetEntityCode;
  }

  public String labelField() {
    return labelField;
  }

  public String filterField() {
    return filterField;
  }

  public String filterValue() {
    return filterValue;
  }

  public String childEntityCode() {
    return childEntityCode;
  }

  public String foreignKey() {
    return foreignKey;
  }

  public String orderColumn() {
    return orderColumn;
  }

  public boolean cascadeDelete() {
    return cascadeDelete;
  }

  public int maxRows() {
    return maxRows;
  }

  public int maxCount() {
    return maxCount;
  }

  public java.util.List<String> accept() {
    return accept;
  }

  /**
   * 引用字段在响应中的显示值键名约定。
   *
   * <p><b>为什么不 JOIN</b>：列表 SQL 一旦 JOIN，分页 {@code COUNT} 与 {@code LIMIT/OFFSET} 都要按目标表重算，且 JOIN
   * 会绕过既有 「列白名单 + 单表SQL」这道注入防线（Runtime 设计 §2）。因此改为「按值批量补全」——一次 {@code IN} 查询取回本页所有 不同引用值的
   * label，前端拿到 {@code {category_id: 12, category_id__label: "差旅"}}。分页语义不变、注入面不变。
   */
  public static String labelKey(String fieldCode) {
    return fieldCode + "__label";
  }

  /** 仅供测试清空缓存（同包可见已足够，此处显式 public 以免跨模块测试访问受限）。 */
  public static void clearCache() {
    CACHE.clear();
  }
}
