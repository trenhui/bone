package com.bone.metadata.engine.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 字段级角色可见性策略单测（纯策略，无 DB）。
 *
 * <p><b>数据布局的判别性设计</b>：{@code title} 不配 roles（默认所有人可见）、{@code salary} 限 {@code hr}、 {@code
 * internal_note} 限 {@code admin}——三种剖面。任何「全裁」或「不裁」的退化实现都至少红一个断言。
 */
class RuntimeFieldVisibilityPolicyTest {

  private static final List<RuntimeFieldVisibility> VISIBILITIES =
      List.of(
          new RuntimeFieldVisibility("salary", List.of("hr")),
          new RuntimeFieldVisibility("internal_note", List.of("admin")));

  private static PublishedRuntimeEntity entity() {
    return new PublishedRuntimeEntity(
        "bp_employee",
        "bp_employee",
        "id",
        1L,
        List.of(
            new RuntimeFieldColumn("id", "LONG", false, false, true),
            new RuntimeFieldColumn("title", "STRING", true, false, false),
            new RuntimeFieldColumn("salary", "DECIMAL", false, false, false),
            new RuntimeFieldColumn("internal_note", "STRING", false, false, false)),
        null,
        null,
        null,
        VISIBILITIES);
  }

  private static Map<String, Object> row() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", 1L);
    row.put("title", "张三");
    row.put("salary", 50000);
    row.put("internal_note", "试用期");
    return row;
  }

  @Nested
  @DisplayName("可见性判定")
  class Visibility {

    @Test
    @DisplayName("未配 roles 的字段对所有角色可见")
    void unconfiguredFieldVisibleToEveryone() {
      RuntimeFieldVisibilityPolicy policy = RuntimeFieldVisibilityPolicy.of(entity());
      assertTrue(policy.isVisible("title", List.of("hr")));
      assertTrue(policy.isVisible("title", List.of()));
      assertTrue(policy.isVisible("title", null));
      assertTrue(policy.isVisible("title", List.of("anything")));
    }

    @Test
    @DisplayName("受限字段：角色有交集才可见；无角色/空角色一律不可见（fail-closed）")
    void restrictedFieldRequiresIntersection() {
      RuntimeFieldVisibilityPolicy policy = RuntimeFieldVisibilityPolicy.of(entity());
      assertTrue(policy.isVisible("salary", List.of("hr")));
      assertTrue(policy.isVisible("salary", List.of("other", "hr")));
      assertFalse(policy.isVisible("salary", List.of("employee")));
      assertFalse(policy.isVisible("salary", List.of()));
      assertFalse(policy.isVisible("salary", null));
      assertFalse(policy.isVisible("internal_note", List.of("hr")));
      assertTrue(policy.isVisible("internal_note", List.of("admin")));
    }

    @Test
    @DisplayName("无任何限制时 ALLOW_ALL 单例、hasRestrictions=false（零开销快路径）")
    void noRestrictionsFastPath() {
      PublishedRuntimeEntity plain =
          new PublishedRuntimeEntity(
              "bp_plain",
              "bp_plain",
              "id",
              1L,
              List.of(new RuntimeFieldColumn("title", "STRING", true, false, false)));
      RuntimeFieldVisibilityPolicy policy = RuntimeFieldVisibilityPolicy.of(plain);
      assertFalse(policy.hasRestrictions());
      assertSame(RuntimeFieldVisibilityPolicy.ALLOW_ALL, policy);
      // 快路径：原样返回，不复制
      Map<String, Object> r = row();
      assertSame(r, policy.trimRow(r, List.of("hr")));
    }

    @Test
    @DisplayName("系统列配置 roles 也被忽略（防建模误配把 id/审计列裁掉）")
    void systemColumnNeverRestricted() {
      PublishedRuntimeEntity bad =
          new PublishedRuntimeEntity(
              "bp_bad",
              "bp_bad",
              "id",
              1L,
              List.of(new RuntimeFieldColumn("id", "LONG", false, false, true)),
              null,
              null,
              null,
              List.of(new RuntimeFieldVisibility("id", List.of("admin"))));
      RuntimeFieldVisibilityPolicy policy = RuntimeFieldVisibilityPolicy.of(bad);
      assertFalse(policy.hasRestrictions(), "系统列不得进入限制集合");
    }

    @Test
    @DisplayName("空 roles 清单不构成限制（与未配置等价）")
    void emptyRolesNotRestricting() {
      RuntimeFieldVisibilityPolicy policy =
          RuntimeFieldVisibilityPolicy.of(
              new PublishedRuntimeEntity(
                  "bp_empty",
                  "bp_empty",
                  "id",
                  1L,
                  List.of(new RuntimeFieldColumn("title", "STRING", true, false, false)),
                  null,
                  null,
                  null,
                  List.of(new RuntimeFieldVisibility("title", List.of()))));
      assertFalse(policy.hasRestrictions());
    }

    @Test
    @DisplayName("denyAll（矛盾配置交集为空）：对所有人不可见，含 admin")
    void denyAllInvisibleToEveryone() {
      RuntimeFieldVisibilityPolicy policy =
          RuntimeFieldVisibilityPolicy.of(
              new PublishedRuntimeEntity(
                  "bp_contradict",
                  "bp_contradict",
                  "id",
                  1L,
                  List.of(new RuntimeFieldColumn("title", "STRING", true, false, false)),
                  null,
                  null,
                  null,
                  List.of(RuntimeFieldVisibility.denyAll("title"))));
      assertTrue(policy.hasRestrictions());
      assertFalse(policy.isVisible("title", List.of("admin")));
      assertFalse(policy.isVisible("title", List.of("hr", "admin")));
      assertFalse(policy.isVisible("title", List.of()));
      assertFalse(policy.trimRow(row(), List.of("admin")).containsKey("title"));
    }

    @Test
    @DisplayName("denyAll 与 roles 互斥（构造即拒，防语义二义）")
    void denyAllExcludesRoles() {
      assertThrows(
          IllegalArgumentException.class,
          () -> new RuntimeFieldVisibility("f", List.of("hr"), true));
    }
  }

  @Nested
  @DisplayName("行裁剪")
  class Trim {

    @Test
    @DisplayName("hr 只见 salary 不见 internal_note；无角色两个都裁；内部调用不裁")
    void trimsByRoleIntersection() {
      RuntimeFieldVisibilityPolicy policy = RuntimeFieldVisibilityPolicy.of(entity());

      Map<String, Object> asHr = policy.trimRow(row(), List.of("hr"));
      assertEquals(Set.of("id", "title", "salary"), asHr.keySet());

      Map<String, Object> asAdmin = policy.trimRow(row(), List.of("admin"));
      assertEquals(Set.of("id", "title", "internal_note"), asAdmin.keySet());

      Map<String, Object> asNobody = policy.trimRow(row(), List.of());
      assertEquals(Set.of("id", "title"), asNobody.keySet());
    }

    @Test
    @DisplayName("__label 伴生键随基名字段一并裁剪（防显示值从侧门泄露）")
    void labelCompanionTrimmedWithBaseField() {
      RuntimeFieldVisibilityPolicy policy = RuntimeFieldVisibilityPolicy.of(entity());
      Map<String, Object> r = row();
      r.put("salary__label", "薪酬档位A");
      r.put("title__label", "标题显示值");

      Map<String, Object> asHr = policy.trimRow(r, List.of("hr"));
      assertTrue(asHr.containsKey("salary__label"), "hr 可见 salary，其 label 应在");
      assertTrue(asHr.containsKey("title__label"));

      Map<String, Object> asNobody = policy.trimRow(r, List.of());
      assertFalse(asNobody.containsKey("salary__label"), "salary 不可见时 label 必须一并裁掉");
      assertFalse(asNobody.containsKey("internal_note__label"));
      assertTrue(asNobody.containsKey("title__label"), "未受限字段的 label 不受影响");
    }

    @Test
    @DisplayName("裁剪返回新 Map，不改入参（调用方持有原始行）")
    void trimDoesNotMutateInput() {
      RuntimeFieldVisibilityPolicy policy = RuntimeFieldVisibilityPolicy.of(entity());
      Map<String, Object> r = row();
      policy.trimRow(r, List.of());
      assertEquals(Set.of("id", "title", "salary", "internal_note"), r.keySet());
    }
  }

  @Nested
  @DisplayName("写守卫")
  class WriteGuard {

    @Test
    @DisplayName("请求体含角色不可见字段 → 403 语义错误码并点名字段")
    void deniesInvisibleFieldWrite() {
      RuntimeFieldVisibilityPolicy policy = RuntimeFieldVisibilityPolicy.of(entity());
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("title", "李四");
      body.put("salary", 1);
      body.put("internal_note", "x");

      RuntimeRecordException ex =
          assertThrows(
              RuntimeRecordException.class,
              () -> policy.assertWritable(entity(), body, List.of("employee")));
      assertEquals(MetadataErrorCodes.RUNTIME_FIELD_NOT_VISIBLE, ex.getErrorCode());
      assertTrue(ex.getMessage().contains("salary"));
      assertTrue(ex.getMessage().contains("internal_note"));
      assertFalse(ex.getMessage().contains("title"), "可见字段不应出现在拒绝消息里");
    }

    @Test
    @DisplayName("角色命中允许清单 → 放行")
    void allowsVisibleFieldWrite() {
      RuntimeFieldVisibilityPolicy policy = RuntimeFieldVisibilityPolicy.of(entity());
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("salary", 1);
      policy.assertWritable(entity(), body, List.of("hr")); // 不抛即通过
    }

    @Test
    @DisplayName("未建模键不报错（由 filterWritable 静默丢弃，守卫不改变既有错误面）")
    void unknownKeysNotDenied() {
      RuntimeFieldVisibilityPolicy policy = RuntimeFieldVisibilityPolicy.of(entity());
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("not_a_column", 1);
      policy.assertWritable(entity(), body, List.of()); // 不抛即通过
    }

    @Test
    @DisplayName("空 body / 无限制实体直接放行")
    void trivialPassThrough() {
      RuntimeFieldVisibilityPolicy policy = RuntimeFieldVisibilityPolicy.of(entity());
      policy.assertWritable(entity(), new LinkedHashMap<>(), List.of());
      policy.assertWritable(entity(), null, List.of());
    }
  }

  @Nested
  @DisplayName("配置归一化")
  class Normalization {

    @Test
    @DisplayName("null/空白 roles 归一为空集合；空白 fieldCode 拒绝")
    void normalizesAndValidates() {
      RuntimeFieldVisibility v = new RuntimeFieldVisibility("f", null);
      assertEquals(List.of(), v.roles());
      assertFalse(v.restricts());

      RuntimeFieldVisibility withBlanks =
          new RuntimeFieldVisibility("f", Arrays.asList("hr", " ", null, "admin"));
      assertEquals(List.of("hr", "admin"), withBlanks.roles());

      assertThrows(
          IllegalArgumentException.class, () -> new RuntimeFieldVisibility(" ", List.of()));
      assertThrows(
          IllegalArgumentException.class, () -> new RuntimeFieldVisibility(null, List.of()));
    }
  }
}
