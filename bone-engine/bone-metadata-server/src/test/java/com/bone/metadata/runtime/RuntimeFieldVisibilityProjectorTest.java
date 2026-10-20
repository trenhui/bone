package com.bone.metadata.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.metadata.engine.runtime.RuntimeFieldVisibility;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 字段级角色可见性投影器单测（ui-schema JSON → {@link RuntimeFieldVisibility}）。
 *
 * <p>覆盖四类判据：① 多表面（form/detail/variant）提取；② 同字段多处配置取<b>交集</b>； ③ 交集为空 ⇒ {@code
 * denyAll}（fail-closed，矛盾配置不许静默放行）；④ 降级路径（无 roles / 坏 JSON / 空 schema）不阻断。
 */
class RuntimeFieldVisibilityProjectorTest {

  private final RuntimeFieldVisibilityProjector projector = new RuntimeFieldVisibilityProjector();

  private static Map<String, RuntimeFieldVisibility> byField(List<RuntimeFieldVisibility> list) {
    return list.stream().collect(Collectors.toMap(RuntimeFieldVisibility::fieldCode, v -> v));
  }

  @Test
  @DisplayName("form/detail/variant 三表面的 control.props.roles 都被提取")
  void extractsFromAllSurfaces() {
    String json =
        """
        {
          "views": {
            "form": {"sections": [{"controls": [
              {"fieldCode": "salary", "props": {"roles": ["hr"]}}
            ]}]},
            "detail": {"sections": [{"controls": [
              {"fieldCode": "internal_note", "props": {"roles": ["admin"]}}
            ]}]}
          },
          "variants": [
            {"key": "vip", "views": {"form": {"sections": [{"controls": [
              {"fieldCode": "vip_level", "props": {"roles": ["admin", "hr"]}}
            ]}]}}}
          ]
        }
        """;

    Map<String, RuntimeFieldVisibility> byField = byField(projector.project(json));
    assertEquals(List.of("hr"), byField.get("salary").roles());
    assertEquals(List.of("admin"), byField.get("internal_note").roles());
    assertEquals(List.of("admin", "hr"), byField.get("vip_level").roles());
  }

  @Test
  @DisplayName("同字段多处配置取交集（最严格者胜出）")
  void intersectsAcrossOccurrences() {
    String json =
        """
        {
          "views": {
            "form": {"sections": [{"controls": [
              {"fieldCode": "salary", "props": {"roles": ["hr", "finance"]}},
              {"fieldCode": "salary", "props": {"roles": ["hr"]}}
            ]}]},
            "detail": {"sections": [{"controls": [
              {"fieldCode": "salary", "props": {"roles": ["hr", "auditor"]}}
            ]}]}
          }
        }
        """;

    Map<String, RuntimeFieldVisibility> byField = byField(projector.project(json));
    // {hr,finance} ∩ {hr} ∩ {hr,auditor} = {hr}
    assertEquals(List.of("hr"), byField.get("salary").roles());
  }

  @Test
  @DisplayName("交集为空 ⇒ denyAll：矛盾配置对所有人不可见（fail-closed）")
  void contradictoryConfigDeniesAll() {
    String json =
        """
        {
          "views": {
            "form": {"sections": [{"controls": [
              {"fieldCode": "salary", "props": {"roles": ["hr"]}}
            ]}]},
            "detail": {"sections": [{"controls": [
              {"fieldCode": "salary", "props": {"roles": ["admin"]}}
            ]}]}
          }
        }
        """;

    Map<String, RuntimeFieldVisibility> byField = byField(projector.project(json));
    RuntimeFieldVisibility v = byField.get("salary");
    assertTrue(v.denyAll(), "hr 与 admin 交集为空 ⇒ 必须 denyAll；若放行则矛盾配置变成静默泄露");
    assertTrue(v.restricts());
  }

  @Test
  @DisplayName("无 roles / 空 roles 数组的控件不构成限制")
  void skipsControlsWithoutRoles() {
    String json =
        """
        {
          "views": {"form": {"sections": [{"controls": [
            {"fieldCode": "title", "props": {"maxLength": 50}},
            {"fieldCode": "amount", "props": {"roles": []}},
            {"fieldCode": "note"}
          ]}]}}
        }
        """;

    assertTrue(projector.project(json).isEmpty());
  }

  @Test
  @DisplayName("空白码被剔除；null/坏 JSON/空 schema 不阻断（返回空列表）")
  void degradesGracefully() {
    String json =
        """
        {"views": {"form": {"sections": [{"controls": [
          {"fieldCode": "salary", "props": {"roles": ["hr", "  ", null]}}
        ]}]}}}
        """;
    Map<String, RuntimeFieldVisibility> byField = byField(projector.project(json));
    assertEquals(List.of("hr"), byField.get("salary").roles());

    assertTrue(projector.project((String) null).isEmpty());
    assertTrue(projector.project("").isEmpty());
    assertTrue(projector.project("{not json").isEmpty());
    assertTrue(projector.project("{}").isEmpty());
  }

  @Test
  @DisplayName("list 列（无 props）不带 roles 时不受影响；带则同样提取（协议演进前向兼容）")
  void listColumnsTolerated() {
    String json =
        """
        {
          "views": {"list": {"columns": [
            {"fieldCode": "title", "visible": true},
            {"fieldCode": "salary", "props": {"roles": ["hr"]}}
          ]}}
        }
        """;
    Map<String, RuntimeFieldVisibility> byField = byField(projector.project(json));
    assertEquals(1, byField.size());
    assertEquals(List.of("hr"), byField.get("salary").roles());
  }
}
