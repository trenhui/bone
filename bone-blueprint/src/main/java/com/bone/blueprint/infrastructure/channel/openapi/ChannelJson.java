package com.bone.blueprint.infrastructure.channel.openapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 渠道响应体导航工具 —— 让渠道扩展实现读字段时像 {@code map.get("buyer_nick")} 那样直白。
 *
 * <p><b>为什么不让扩展实现直接用 {@code JsonNode}</b>：{@code JsonNode} 逐层 {@code .get("x").get("y").asText()}
 * 会把渠道字段的层级 结构抄进 12 份扩展实现里（{@code orders.trade.order} 这类），一处拼错就是一个 {@code NullPointerException}
 * 而不是一句人话报错。 本工具的 {@code path} 用点号表达层级，取不到时返回 {@code null} 并保留原始路径，便于定位。
 *
 * <p><b>为什么不让扩展实现直接操作 {@code Map}</b>：{@code Map.get("x")} 拿到的是 {@code Object}，向下转型时抛的 {@code
 * ClassCastException} 不带字段名，排障时得回到渠道文档比对。
 */
public final class ChannelJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private ChannelJson() {}

  /** 解析渠道响应体；解析失败返回 {@code null}（由调用方转成业务错误码，不在这里抛）。 */
  public static Map<String, Object> parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return MAPPER.readValue(raw, MAP_TYPE);
    } catch (Exception ex) {
      return null;
    }
  }

  /** 按点号路径取值（中间层缺失时返回 {@code null}，不抛异常）。 */
  @SuppressWarnings("unchecked")
  public static Object path(Map<String, Object> root, String dottedPath) {
    if (root == null || dottedPath == null) {
      return null;
    }
    Object current = root;
    for (String key : dottedPath.split("\\.")) {
      if (current == null) {
        return null;
      }
      if (current instanceof Map<?, ?> map) {
        current = map.get(key);
      } else if (current instanceof List<?> list && tryIndex(key)) {
        int idx = Integer.parseInt(key);
        current = idx >= 0 && idx < list.size() ? list.get(idx) : null;
      } else {
        return null;
      }
    }
    return current;
  }

  public static String str(Map<String, Object> root, String path) {
    Object value = path(root, path);
    return value == null ? null : String.valueOf(value);
  }

  public static Long longOf(Map<String, Object> root, String path) {
    Object value = path(root, path);
    if (value == null) {
      return null;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value).trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  public static Integer intOf(Map<String, Object> root, String path) {
    Long value = longOf(root, path);
    return value == null ? null : value.intValue();
  }

  public static BigDecimal decimal(Map<String, Object> root, String path) {
    Object value = path(root, path);
    if (value == null) {
      return null;
    }
    try {
      return new BigDecimal(String.valueOf(value).trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  /** 取数组；渠道缺该字段时返回空列表（调用方按「空」处理，而不是 NPE）。 */
  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> list(Map<String, Object> root, String path) {
    Object value = path(root, path);
    if (value == null) {
      return Collections.emptyList();
    }
    if (value instanceof List<?> raw) {
      List<Map<String, Object>> rows = new ArrayList<>();
      for (Object item : raw) {
        if (item instanceof Map<?, ?> map) {
          rows.add((Map<String, Object>) map);
        }
      }
      return rows;
    }
    if (value instanceof Map<?, ?> map) {
      // 淘宝把明细包成 {"trade":{"order":[...]}}：单层包裹视为一个元素，避免扩展实现再写一层分支。
      List<Map<String, Object>> rows = new ArrayList<>();
      rows.add((Map<String, Object>) map);
      return rows;
    }
    return Collections.emptyList();
  }

  private static boolean tryIndex(String key) {
    return key.chars().allMatch(c -> c >= '0' && c <= '9');
  }
}
