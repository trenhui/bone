package com.bone.metadata.catalog.domain.model.meta;

/**
 * 枚举选项视图（字段 ENUM 控件的一个可选值）。
 *
 * <p>领域层只认业务字段 {@code code/label/state}，不感知 bone-system 的存储与传输形态—— 真源解析的
 * 输出、物化快照的输入、渲染/写校验的比对都以此为唯一形状。
 *
 * <p><b>三态生命周期</b>（界面设计器字典打通 v1.1 §4.5）：{@code state} ∈ ACTIVE / DEPRECATED（DISABLED 不进 {@code
 * /options}）。物化把 DEPRECATED 一并写进快照（存量写放行），渲染侧按 ACTIVE 过滤（新表单不出现）。 存量快照与 inline 枚举无 state 字段，按
 * ACTIVE 兼容。
 *
 * <p><b>为何住 domain.model 而非 domain.service</b>：adapter 控制器直接把它作为响应载荷（{@code GET
 * /fields/{fieldId}/options}），而 ArchUnit 门禁「adapter 不得依赖 domain.service」（DDD P0-7）
 * 会命中泛型实参——值对象是纯领域概念， 放 model 包既守住分层又避免再造一个传输 DTO 与之映射。
 */
public record OptionItem(String code, String label, String state) {

  public OptionItem(String code, String label) {
    this(code, label, "ACTIVE");
  }

  public boolean isDeprecated() {
    return "DEPRECATED".equals(state);
  }
}
