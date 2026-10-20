package com.bone.metadata.engine.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 字段级角色可见性策略（数据面列级裁剪 + 写守卫的唯一裁决处）。
 *
 * <p><b>判定语义</b>：字段配置了非空 {@code roles} 允许清单时，用户角色码与清单<b>有任一交集</b>才可见；
 * 无角色（空集合）用户对受限字段一律不可见——fail-closed，与「没配 roles 的字段所有人可见」形成默认开放、显式收紧的剖面。
 *
 * <p><b>与调用方的契约（关键）</b>：
 *
 * <ul>
 *   <li>{@code roleCodes == null}：内部/受信调用（动作执行、级联、审计取数）——<b>不裁剪、不守卫</b>，全量数据原样返回；
 *   <li>{@code roleCodes != null}（含空集合）：用户面调用——按角色裁剪响应、按角色守卫写入。
 * </ul>
 *
 * 这条契约由 {@link JdbcRuntimeRecordService} 的各重载保证：内部路径一律传 null。
 *
 * <p><b>为什么裁剪要连 {@code __label} 伴生键一起裁</b>：引用字段的显示值由 {@code enrichLabels} 以 {@code code__label}
 * 补进行内——只裁字段不裁 label，等于把受限字段的<b>显示名原文</b>从侧门放出去。
 */
public final class RuntimeFieldVisibilityPolicy {

  private static final String LABEL_SUFFIX = "__label";

  /** 无任何限制时的单例（绝大多数实体落此分支，零额外分配）。 */
  static final RuntimeFieldVisibilityPolicy ALLOW_ALL = new RuntimeFieldVisibilityPolicy(Map.of());

  /** 受限字段 → 允许角色集合。只收真正构成限制的字段；系统列永不纳入（见 {@link #of}）。 */
  private final Map<String, Set<String>> restrictedByField;

  private RuntimeFieldVisibilityPolicy(Map<String, Set<String>> restrictedByField) {
    this.restrictedByField = restrictedByField;
  }

  /** 从实体快照构建策略；无配置（或配置全部不构成限制）时返回 {@link #ALLOW_ALL}。 */
  public static RuntimeFieldVisibilityPolicy of(PublishedRuntimeEntity entity) {
    if (entity == null) {
      return ALLOW_ALL;
    }
    List<RuntimeFieldVisibility> visibilities = entity.fieldVisibilities();
    if (visibilities == null || visibilities.isEmpty()) {
      return ALLOW_ALL;
    }
    Map<String, Set<String>> restricted = new HashMap<>();
    for (RuntimeFieldVisibility v : visibilities) {
      if (!v.restricts()) {
        continue;
      }
      // 系统列（id/tenant_id/审计列/version…）永不裁剪：行主键与审计列是前端渲染与
      // 行级 ACL（created_by）的基石，且 UI 协议里本就不可对它们配 roles——防建模误配放大成事故。
      if (JdbcRuntimeRecordService.SYSTEM_COLUMNS.contains(v.fieldCode())) {
        continue;
      }
      restricted.put(v.fieldCode(), Set.copyOf(v.roles()));
    }
    return restricted.isEmpty() ? ALLOW_ALL : new RuntimeFieldVisibilityPolicy(restricted);
  }

  /** 是否存在任何字段级限制（false 时调用方应走零开销快路径）。 */
  public boolean hasRestrictions() {
    return !restrictedByField.isEmpty();
  }

  /** 字段对给定角色集合是否可见。未受限字段恒 true。 */
  public boolean isVisible(String fieldCode, Collection<String> userRoleCodes) {
    Set<String> allowed = restrictedByField.get(fieldCode);
    if (allowed == null) {
      return true;
    }
    if (userRoleCodes == null) {
      return false;
    }
    for (String role : userRoleCodes) {
      if (role != null && allowed.contains(role)) {
        return true;
      }
    }
    return false;
  }

  /**
   * 裁剪单行：剔除角色不可见字段及其 {@code __label} 伴生键。
   *
   * <p>无限制时原样返回入参（不复制）；有限制时返回新 Map，不改入参。
   */
  public Map<String, Object> trimRow(Map<String, Object> row, Collection<String> userRoleCodes) {
    if (row == null || row.isEmpty() || !hasRestrictions()) {
      return row;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : row.entrySet()) {
      String key = e.getKey();
      if (!isVisible(key, userRoleCodes)) {
        continue;
      }
      // 伴生显示值：仅当基名字段确实受限且不可见时才裁（避免误伤真名就叫 xxx__label 的字段）
      if (key.endsWith(LABEL_SUFFIX)) {
        String base = key.substring(0, key.length() - LABEL_SUFFIX.length());
        if (restrictedByField.containsKey(base) && !isVisible(base, userRoleCodes)) {
          continue;
        }
      }
      out.put(key, e.getValue());
    }
    return out;
  }

  /**
   * 写守卫：请求体含角色不可见字段时 fail-closed 拒绝（403 语义错误码）。
   *
   * <p><b>为什么拒绝而不是静默丢弃</b>：静默 drop 会让调用方以为字段已写入（与 {@code RUNTIME_FIELD_PROTECTED}
   * 拒绝状态字段直写同一理由）——「显示没保存」与「保存被拒」必须可区分。
   *
   * <p>只对实体真实列报错：未建模键本就由 {@code filterWritable} 静默丢弃（既有契约）， 本守卫不改变那一面的错误形态。
   */
  public void assertWritable(
      PublishedRuntimeEntity entity, Map<String, Object> body, Collection<String> userRoleCodes) {
    if (body == null || body.isEmpty() || !hasRestrictions()) {
      return;
    }
    List<String> denied = new ArrayList<>();
    for (String key : body.keySet()) {
      if (isVisible(key, userRoleCodes) || JdbcRuntimeRecordService.SYSTEM_COLUMNS.contains(key)) {
        continue;
      }
      if (entity != null && entity.hasColumn(key)) {
        denied.add(key);
      }
    }
    if (!denied.isEmpty()) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_FIELD_NOT_VISIBLE,
          "字段不在当前角色可见范围内，拒绝写入: " + String.join(", ", denied));
    }
  }
}
