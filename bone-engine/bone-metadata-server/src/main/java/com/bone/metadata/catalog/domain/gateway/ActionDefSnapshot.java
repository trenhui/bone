package com.bone.metadata.catalog.domain.gateway;

/**
 * 实体动作定义的只读快照（发布 onboarding 用，P1）。
 *
 * <p>只带 UI 展示所需的三元组：动作编码、中文名、动作级细码。不引入 runtime 界的 {@code RuntimeActionDef} 领域对象——catalog
 * 的发布预览只需要「要授哪些码、对应什么动作」， 跨界传染完整领域模型会让依赖方向失控（ADR-0030 读写边界）。
 *
 * @param actionCode 动作编码（如 {@code confirmPayout}）
 * @param label 动作中文名（如「确认打款」），供清单可读展示
 * @param permissionCode 动作级细码 {@code runtime:{entityCode}:{actionCode}}，即需授予的权限码
 */
public record ActionDefSnapshot(String actionCode, String label, String permissionCode) {}
