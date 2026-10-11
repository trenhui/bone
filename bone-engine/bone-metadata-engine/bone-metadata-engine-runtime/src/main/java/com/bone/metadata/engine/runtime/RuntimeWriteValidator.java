package com.bone.metadata.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

/**
 * 运行时写路径公共校验器（RC-007 接线 · v0.46）。
 *
 * <p><b>为什么独立成类</b>：主行写路径（{@link JdbcRuntimeRecordService}）与子表写路径（{@link
 * RuntimeRelationSupport}）必须共用同一套「必填 / 类型 / 元数据规则（min/max/ENUM 越界）」校验——
 * 服务端强制、不信任前端，且单一真源，避免两处实现漂移（文档 G1「契约即真源」的代码侧对应）。
 *
 * <p><b>不校验唯一性</b>：唯一查重需要按行定位查库，与调用方的事务/行语义耦合，留在调用方各自实现。
 *
 * <p><b>错误码</b>：统一 {@code META_RUNTIME_VALIDATION_FAILED}（422），与 OpenAPI v1.2.3 错误码登记一致。
 *
 * <p><b>降级口径</b>（与既有 {@code JdbcRuntimeRecordService} 一致）：ENUM 声明 / 规则 JSON 无法解析时
 * 按「未配置」处理，不阻断写入——建模层保证合法性，运行期不因脏配置拒全表。
 */
public final class RuntimeWriteValidator {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private RuntimeWriteValidator() {}

  /**
   * 全字段校验（create 语义）：遍历实体全部列，必填缺失即拒绝。
   *
   * <p>主行 {@code validateForCreate} 与子表 insert 行共用。
   */
  public static void validateForCreate(PublishedRuntimeEntity entity, Map<String, Object> payload) {
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
        validateRules(col, payload.get(col.code()));
      }
    }
  }

  /**
   * 增量字段校验（update 语义）：只校验载荷中出现的字段；必填字段不可被显式置空。
   *
   * <p>主行 {@code validateForUpdate} 与子表 update 行共用。
   */
  public static void validateForUpdate(PublishedRuntimeEntity entity, Map<String, Object> payload) {
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
        validateRules(col, value);
      }
    }
  }

  private static void validateType(RuntimeFieldColumn col, Object value) {
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

  /**
   * 元数据规则校验（RC-007 接线子集）：消费 {@code validation_rules}（min/max）与 {@code enum_values}（ENUM
   * 越界）。完整业务规则引擎（复杂管线）仍属 v1.1；本方法只做轻量子集，保证「服务端强制」。
   */
  private static void validateRules(RuntimeFieldColumn col, Object value) {
    String type = col.type() == null ? "" : col.type().toUpperCase();
    if ("ENUM".equals(type) && col.enumValues() != null && !col.enumValues().isBlank()) {
      if (!isEnumValueAllowed(col.enumValues(), value)) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
            "字段「" + col.code() + "」取值 " + value + " 不在允许枚举内");
      }
    }
    if (col.validationRules() != null && !col.validationRules().isBlank()) {
      applyNumericBounds(col, value);
    }
  }

  private static boolean isEnumValueAllowed(String enumValuesJson, Object value) {
    try {
      String raw = enumValuesJson.trim();
      String normalized = raw;
      if (normalized.startsWith("[")) {
        Object parsed = MAPPER.readValue(normalized, Object.class);
        if (parsed instanceof java.util.List<?> list) {
          for (Object item : list) {
            String candidate = null;
            if (item instanceof Map<?, ?> map && map.containsKey("code")) {
              candidate = String.valueOf(map.get("code"));
            } else if (item != null) {
              candidate = String.valueOf(item);
            }
            if (candidate != null && candidate.equals(String.valueOf(value))) {
              return true;
            }
          }
        }
        return false;
      }
      for (String part : normalized.split(",")) {
        if (part.trim().equals(String.valueOf(value))) {
          return true;
        }
      }
      return false;
    } catch (Exception e) {
      return true; // 枚举声明无法解析时按未配置处理（不阻断写入）
    }
  }

  private static void applyNumericBounds(RuntimeFieldColumn col, Object value) {
    String type = col.type() == null ? "" : col.type().toUpperCase();
    if (!type.matches("LONG|BIGINT|INT|INTEGER|DECIMAL|DOUBLE|FLOAT|NUMBER")) {
      return;
    }
    if (!(value instanceof Number number)) {
      return; // 类型校验已单独处理
    }
    try {
      JsonNode rules = MAPPER.readTree(col.validationRules());
      double v = number.doubleValue();
      JsonNode min = rules.get("min");
      if (min != null && min.isNumber() && v < min.asDouble()) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
            "字段「" + col.code() + "」不能小于 " + min.asDouble());
      }
      JsonNode max = rules.get("max");
      if (max != null && max.isNumber() && v > max.asDouble()) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_VALIDATION_FAILED,
            "字段「" + col.code() + "」不能大于 " + max.asDouble());
      }
    } catch (RuntimeRecordException e) {
      throw e;
    } catch (Exception e) {
      // 规则 JSON 无法解析时不阻断写入（建模层保证合法性）
    }
  }
}
