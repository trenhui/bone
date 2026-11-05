package com.bone.claim.domain.model.decision;

/**
 * 知识检索核验基据（受控码）：回答「知识层为什么给不出结论」。
 *
 * <p><b>为什么必须细分</b>：能力缺失此前只落一个 {@code KNOWLEDGE_UNAVAILABLE}，而它的成因有四种、处置动作互斥——
 * 票面类材料没解析出事实（改解析）、解析出了但没有临床字段（换材料或换核验口径）、目录里查不到编码（补目录）、
 * 能力仍是降级桩（接能力）。四种都报同一个码，运维唯一能做的就是「去问开发」，这正是「原因码形同虚设」的典型形态。
 *
 * <p><b>它与 {@link AiDegradationReason} 的分工</b>：那个回答「模型为什么没跑成」，本枚举回答「知识层为什么判不了」。 二者可以同时出现（模型超时 +
 * 知识库未接），故在轨迹上是两个独立字段，不得互相覆盖。
 *
 * <p><b>不要把 {@link #CATALOG_ADMISSION_VERIFIED} 读成「临床合理」</b>：一期核验范围只有「诊断 / 药品编码在租户目录中的准入」
 * 与「解析事实齐备性」，不含临床推理与适应症图谱。该码的含义严格是「在本范围内未发现差异」。
 */
public enum KnowledgeBasis {

  /** 事实齐备且全部命中租户目录：范围内未发现差异（<b>不是</b>「确认为临床合理」）。 */
  CATALOG_ADMISSION_VERIFIED("核验通过", "无需处置。注意其含义是「目录准入范围内未发现差异」，不能对外宣称「确认临床合理」。"),

  /** 材料里一条可用的结构化事实都没有（解析失败 / 空 / 损坏）。 */
  NO_PARSED_FACTS("材料无可用的结构化事实", "查文档解析：材料是否已材料化、解析是否报错、extracted_json 是否为空。这一步不通，后面所有临床核验都不会发生。"),

  /** 有事实，但没有临床口径编码（只有票面事实），做不了任何临床核验。 */
  INSUFFICIENT_CLINICAL_FACTS(
      "只有票面事实，无临床编码", "属材料口径问题而非故障：纯票据类案件本就不该由临床目录核验。要提升自动化率须补病历/处方类材料，" + "或另建条款/责任口径的核验，不是配个开关。"),

  /** 有临床编码但租户目录里查不到。 */
  CATALOG_UNKNOWN(
      "目录未收录该编码", "补租户目录（诊断 t_ref_disease / 药品 t_ref_drug 或对应主数据），编码对齐后再跑；不要放宽为「查不到也放行」。"),

  /** 知识能力仍是降级桩（{@code bone.claim.knowledge.mode=stub}，演练用）。 */
  STUB_NOT_INTEGRATED(
      "知识能力为降级桩", "把 bone.claim.knowledge.mode 从 stub 切回 catalog（或缺省）后重启；桩存在的意义就是让这种缺失可见。"),

  /** 码不在本枚举内：版本错配或异构写入。不把自由文本带进审计面。 */
  UNRECOGNIZED("未登记的基据码", "该码不在本构建的受控枚举内：查是否为更新版本写入、或异构侧自由文本；按版本升级或对齐码表。");

  private final String description;
  private final String action;

  KnowledgeBasis(String description, String action) {
    this.description = description;
    this.action = action;
  }

  /** 落库用的码（唯一真源）：轨迹 {@code knowledgeBasis} 字段即取此值。 */
  public String code() {
    return name();
  }

  public String description() {
    return description;
  }

  /** 该基据对应的处置动作（受控文案，供运维端点 / 文档使用，禁止拼接用户输入）。 */
  public String action() {
    return action;
  }

  /**
   * 归类：接受历史 / 异构写入的原始串，未知值归 {@link #UNRECOGNIZED} 而不是抛异常。
   *
   * <p>这是读侧口径——不能因为一条历史数据的码不认识，就让整个健康度端点 500。
   */
  public static KnowledgeBasis of(String raw) {
    if (raw == null || raw.isBlank()) {
      return UNRECOGNIZED;
    }
    for (KnowledgeBasis basis : values()) {
      if (basis.name().equalsIgnoreCase(raw.trim())) {
        return basis;
      }
    }
    return UNRECOGNIZED;
  }
}
