package com.bone.metadata.catalog.domain.gateway;

/**
 * 实体动作定义的<b>建模期读写单元</b>（{@code meta_entity_action_def} 一行的完整视图）。
 *
 * <p><b>为什么需要它</b>：动作定义此前只有模板实例化一条自动生成路径（{@code ActionDefMaterializePort#materialize}），平台没有面向
 * 单实体的写入入口。后果是 UI Schema {@code actionBar[].actionCode} 引用不到任何动作（{@code UI-REF-02} 恒 ERROR）、
 * 状态机无法流转（{@code stateField} 无从推导），样板场景（报销 submit/approve/reject、理赔 pay/appeal）根本走不到运行期。
 *
 * <p><b>为何不复用 {@code MetaModelTemplateAction}</b>：那带 {@code templateId}
 * 归属，语义是「模板侧动作」，与「实体侧动作」是两份数据； 复用会让调用方有机会把 templateId 写进实体侧表。字段名与 {@code meta_entity_action_def}
 * 严格一一对应（同方言、不重复映射）。
 *
 * @param actionCode 动作编码（实体内唯一，如 {@code confirmPayout}）
 * @param label 动作中文名
 * @param fromStatus 状态机边起点；{@code null} = 任意状态可触发
 * @param toStatus 状态机边终点（纯声明式 state_transition 的目标态）
 * @param statusField 被流转的状态字段 code；{@code null} 时落库为 {@code status}
 * @param escape 逃逸动作（有资金/外部副作用），须命中动作级细码 {@code runtime:{entityCode}:{actionCode}}
 * @param handlerBean 逃逸动作处理器 bean；非逃逸动作留空（走内置 state_transition）
 * @param paramsSchema 动作入参 JSON Schema
 * @param requiresIdempotency 是否要求 {@code Idempotency-Key} 头（资金类动作必须为 true）
 * @param sortOrder 同状态内动作排序（前端动作条 order）
 * @param entry 入口动作标记（0041）：{@code true} 时其 {@code fromStatus} 即新记录初始状态；全环状态机（无无入边态）必须显式声明，
 *     否则运行期创建记录死锁。与 {@code fromStatus=null}（任意态可触发）正交，互不替代
 */
public record ActionDefSpec(
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

  /**
   * 落库用的 status_field 缺省值（DDL DEFAULT 'status'，与引擎 {@code CatalogRuntimeEntityProvider} 推导口径一致）。
   */
  public static final String DEFAULT_STATUS_FIELD = "status";

  /** 动作级细粒度权限码形状：{@code runtime:{entityCode}:{actionCode}}（Runtime 数据面设计 §4.6）。 */
  public String permissionCodeOf(String entityCode) {
    return "runtime:" + entityCode + ":" + actionCode;
  }
}
