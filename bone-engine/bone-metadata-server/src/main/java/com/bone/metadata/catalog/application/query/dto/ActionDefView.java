package com.bone.metadata.catalog.application.query.dto;

import com.bone.metadata.catalog.domain.gateway.ActionDefSpec;

/**
 * 实体动作定义的对外读视图（建模面板数据源）。
 *
 * <p><b>为什么不能直出 {@link ActionDefSpec}</b>：那是 {@code ..domain.gateway..}
 * 端口契约类型（domain↔infrastructure 协作协议），直出 web 边界等于把内部协议固化成对外 API——HC-003 门禁对此阻断（见 {@code
 * controller-response-baseline.json} 的 payloadViolations 口径）。
 *
 * <p><b>与端口契约的分化点</b>：{@code permissionCode} 是<b>派生字段</b>（{@code
 * runtime:{entityCode}:{actionCode}}， 动作级细粒度授权命名空间，Runtime 数据面设计 §4.6）——端口契约不感知 entityCode
 * 给不出该值，而建模面板需要它核对 权限码，故在应用层补齐后对外。两个布尔标志（{@code escape} / {@code requiresIdempotency}）归一为非 null，
 * 线格式稳定（前端 {@code === false} 判定可用）。后续对外契约若需与内部协议进一步分化（裁剪 / 更名 / 合并），只改本类。
 */
public record ActionDefView(
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
    Boolean entry,
    String permissionCode) {

  /** 由端口契约 + 实体编码派生对外视图（permissionCode 在此补齐，布尔标志归一）。 */
  public static ActionDefView of(ActionDefSpec spec, String entityCode) {
    return new ActionDefView(
        spec.actionCode(),
        spec.label(),
        spec.fromStatus(),
        spec.toStatus(),
        spec.statusField(),
        Boolean.TRUE.equals(spec.escape()),
        spec.handlerBean(),
        spec.paramsSchema(),
        Boolean.TRUE.equals(spec.requiresIdempotency()),
        spec.sortOrder(),
        Boolean.TRUE.equals(spec.entry()),
        spec.permissionCodeOf(entityCode));
  }
}
