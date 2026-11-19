package com.bone.procurement.governance.domain.model.labeling;

import java.util.Objects;

/**
 * 生成合成内容标识（D3-v2 §3 E-5 / C-1 / 蓝图 F4）。
 *
 * <p>生成合成内容（M7 起草稿 / M2 审查报告 / M5 问答 / M6 投标响应草稿）须同时带：
 *
 * <ul>
 *   <li><b>EXPLICIT</b>：显式标识——面向阅读者的「本内容由 AI 生成/辅助」文案；
 *   <li><b>IMPLICIT</b>：隐式元数据标识——文件元数据里的生成标记（机器可读）。
 * </ul>
 *
 * <p>缺一禁止出域（LABEL-01：`missingImplicitLabel_blocksDelivery`）。标识是否强制取决于法务对《标识办法》主体适用性的结论（C-1 标
 * voluntaryAdoption）， 本值对象只落实技术载体与「缺一阻断」的工程动作，不代替法务定性。
 */
public record ContentLabel(
    String contentRef,
    String capabilityCode,
    String explicitLabel,
    String implicitMetadata,
    boolean aiGenerated) {

  public ContentLabel {
    Objects.requireNonNull(contentRef, "contentRef");
    Objects.requireNonNull(capabilityCode, "capabilityCode");
    if (contentRef.isBlank()) {
      throw new IllegalArgumentException("contentRef 不得为空");
    }
    if (explicitLabel == null) {
      explicitLabel = "";
    }
    if (implicitMetadata == null) {
      implicitMetadata = "";
    }
  }

  public boolean hasExplicit() {
    return !explicitLabel.isBlank();
  }

  public boolean hasImplicit() {
    return !implicitMetadata.isBlank();
  }

  /** 双标识齐备才允许出域（LABEL-01 核心不变量）。 */
  public boolean canRelease() {
    return hasExplicit() && hasImplicit();
  }
}
