package com.bone.metadata.catalog.domain.model.template;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.util.Date;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 平台模型模板的<b>状态机动作定义</b>（G3 扩展 · 迁移 0040）。
 *
 * <p><b>为什么需要它</b>：实例化此前只产出「对象 + 字段」，不产出 {@code meta_entity_action_def}。而 {@code state_field}
 * 只能从动作定义推导 ⇒ 模板实例的 stateField 恒为 null，连锁导致：
 *
 * <ol>
 *   <li>{@code injectEntryState} 不生效 → 必填的受保护 status 无人注入 → 创建记录必 400「state 为必填」 （浏览器实测 UI 新建记录失败）；
 *   <li>{@code assertStateFieldNotWritable} 不生效 → 可绕过状态机直写终态（实测 status=PAID 201）。
 * </ol>
 *
 * <p>平台没有动作定义的写入 API/UI，用户无法自救 ⇒ 必须在模板实例化这条唯一的自动生成路径上产出。
 *
 * <p><b>字段语义与 meta_entity_action_def 一一对应</b>（避免两套方言），只是归属从 {@code entity_code} 变为 {@code
 * template_id}；实例化时按新实体 code 展开。
 */
@Getter
@NoArgsConstructor
@Table("meta_model_template_action")
public class MetaModelTemplateAction extends AbstractEntity<Long> {

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "template_id", nullable = false)
  private Long templateId;

  @Column(name = "action_code", nullable = false)
  private String actionCode;

  @Column(name = "label", nullable = false)
  private String label;

  /** 状态机边的起点；NULL 表示「任意状态均可触发」（与 meta_entity_action_def.from_status 同语义）。 */
  @Column(name = "from_status")
  private String fromStatus;

  /** 状态机边的终点。 */
  @Column(name = "to_status")
  private String toStatus;

  /** 被流转的状态字段 code（默认 'status'）。 */
  @Column(name = "status_field", nullable = false)
  private String statusField;

  /** 逃逸动作（有副作用，如外部支付），需动作级细粒度授权且常带幂等键。 */
  @Column(name = "is_escape", nullable = false)
  private Boolean escape;

  @Column(name = "handler_bean")
  private String handlerBean;

  /** 动作入参 JSON Schema（前端据此渲染表单，后端据此校验必填）。 */
  @Column(name = "params_schema")
  private String paramsSchema;

  @Column(name = "requires_idempotency", nullable = false)
  private Boolean requiresIdempotency;

  @Column(name = "sort_order", nullable = false)
  private Integer sortOrder;

  /**
   * 入口动作标记（0041）：{@code true} 时其 {@code fromStatus} 即实例化实体的初始状态。
   *
   * <p>全环状态机（如设备巡检 {@code OPEN↔CLOSED}）没有「无入边」状态，图推导不出入口 ⇒ 创建记录死锁； 模板侧必须显式声明，实例化时原样展开到 {@code
   * meta_entity_action_def.is_entry}。与 {@code fromStatus=null}（任意态可触发）正交。
   */
  @Column(name = "is_entry", nullable = false)
  private Boolean entry;

  public static MetaModelTemplateAction create(
      Long id,
      Long tenantId,
      Long templateId,
      String actionCode,
      String label,
      String fromStatus,
      String toStatus,
      String statusField,
      Boolean escape,
      String handlerBean,
      String paramsSchema,
      Boolean requiresIdempotency,
      Integer sortOrder,
      Boolean entry) {
    MetaModelTemplateAction a = new MetaModelTemplateAction();
    a.setId(id);
    a.tenantId = tenantId;
    a.templateId = templateId;
    a.actionCode = actionCode;
    a.label = label;
    a.fromStatus = fromStatus;
    a.toStatus = toStatus;
    a.statusField = statusField;
    a.escape = escape != null ? escape : false;
    a.handlerBean = handlerBean;
    a.paramsSchema = paramsSchema;
    a.requiresIdempotency = requiresIdempotency != null ? requiresIdempotency : false;
    a.sortOrder = sortOrder != null ? sortOrder : 0;
    a.entry = entry != null ? entry : false;
    Date now = new Date();
    a.setCreatedAt(now);
    a.setUpdatedAt(now);
    a.setDeleted(false);
    return a;
  }
}
