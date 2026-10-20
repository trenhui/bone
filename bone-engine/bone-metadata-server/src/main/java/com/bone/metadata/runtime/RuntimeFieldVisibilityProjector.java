package com.bone.metadata.runtime;

import com.bone.metadata.engine.runtime.RuntimeFieldVisibility;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 字段级角色可见性投影器：发布态 ui-schema JSON → {@link RuntimeFieldVisibility} 列表。
 *
 * <p><b>提取规则</b>：遍历整棵 schema 树，收集所有带 {@code fieldCode} 且 {@code props.roles} 为<b>非空数组</b>的节点
 * （list/form/detail/variant 各表面一视同仁——协议演进新增表面无需改本类）。
 *
 * <p><b>同一字段多处配置时取交集</b>（最严格者胜出，fail-closed）：detail 说 {@code [hr]}、form 说 {@code [admin]} ⇒ 交集为空 ⇒
 * 该字段对<b>所有人</b>不可见（{@code denyAll}）。理由：服务端只能对一行数据做全局裁决（不知道响应喂给哪个表面）， 并集会把受限字段从没配 roles
 * 的表面放出去（静默泄露）；交集最坏情况是字段全局消失—— 这是<b>响亮的</b>失败，建模者会立刻发现并修配置， 而泄露是静默的。仅当交集为空时产出 {@link
 * RuntimeFieldVisibility#denyAll} 形态。
 *
 * <p><b>解析失败不阻断</b>：schema JSON 损坏/形状不符时返回空列表（= 无字段级限制），与 {@code
 * UiSchemaApplicationService#schemaForExecution} 的降级口径一致——界面协议问题不应让数据面 500。
 */
@Component
public class RuntimeFieldVisibilityProjector {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * 从原始 JSON 投影；null/空白 → 空列表。
   *
   * <p><b>只吞解析失败</b>（{@link JsonProcessingException}）：schema 是建模侧产物，损坏/形状不符时按「无字段级限制」 降级，与 {@code
   * UiSchemaApplicationService#schemaForExecution} 同口径。行走逻辑自身的缺陷（NPE 等）<b>不吞</b>—— 吞了就会把「配了 roles
   * 但服务端没生效」伪装成「没配 roles」，正是本项目要防的假绿。
   */
  public List<RuntimeFieldVisibility> project(String schemaJson) {
    if (schemaJson == null || schemaJson.isBlank()) {
      return List.of();
    }
    try {
      return project(MAPPER.readTree(schemaJson));
    } catch (JsonProcessingException ex) {
      return List.of();
    }
  }

  /** 从已解析的 schema 树投影。 */
  public List<RuntimeFieldVisibility> project(JsonNode root) {
    if (root == null || root.isEmpty()) {
      return List.of();
    }
    Map<String, List<Set<String>>> occurrences = new LinkedHashMap<>();
    collect(root, occurrences);
    if (occurrences.isEmpty()) {
      return List.of();
    }
    List<RuntimeFieldVisibility> out = new java.util.ArrayList<>(occurrences.size());
    occurrences.forEach(
        (fieldCode, sets) -> {
          if (sets.size() == 1) {
            out.add(new RuntimeFieldVisibility(fieldCode, List.copyOf(sets.get(0))));
            return;
          }
          // 多处配置：交集（保留出现顺序，便于审计与测试稳定）
          Set<String> intersection = new LinkedHashSet<>(sets.get(0));
          for (int i = 1; i < sets.size(); i++) {
            intersection.retainAll(sets.get(i));
          }
          if (intersection.isEmpty()) {
            // 矛盾配置：没有任何角色能同时满足所有表面 ⇒ 对所有人不可见（fail-closed）
            out.add(RuntimeFieldVisibility.denyAll(fieldCode));
          } else {
            out.add(new RuntimeFieldVisibility(fieldCode, List.copyOf(intersection)));
          }
        });
    return out;
  }

  /** 递归收集 (fieldCode → roles) 出现点；只收 props.roles 为非空数组的节点。 */
  private void collect(JsonNode node, Map<String, List<Set<String>>> occurrences) {
    if (node.isObject()) {
      // path() 而非 get()：老版本 Jackson 的 get() 对缺失键返 null，直接调 isTextual() 会 NPE
      JsonNode fieldCode = node.path("fieldCode");
      JsonNode roles = node.path("props").path("roles");
      if (fieldCode.isTextual() && roles.isArray() && roles.size() > 0) {
        Set<String> codes = new LinkedHashSet<>();
        roles.forEach(
            r -> {
              if (r.isTextual() && !r.asText().isBlank()) {
                codes.add(r.asText().trim());
              }
            });
        if (!codes.isEmpty()) {
          occurrences
              .computeIfAbsent(fieldCode.asText(), k -> new java.util.ArrayList<>())
              .add(codes);
        }
      }
      // 老版本 Jackson 无 JsonNode#children()：对象走 fields()、数组走 elements()
      node.fields().forEachRemaining(entry -> collect(entry.getValue(), occurrences));
    } else if (node.isArray()) {
      node.elements().forEachRemaining(child -> collect(child, occurrences));
    }
  }
}
