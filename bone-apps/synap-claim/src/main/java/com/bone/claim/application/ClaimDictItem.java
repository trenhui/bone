package com.bone.claim.application;

/**
 * 受控码字典项（code → label 单一真源）。
 *
 * <p>所有「受控码 → 中文标签」的映射<b>以此端点返回为准</b>：前端经 codegen 生成字典， 不再手维护第二份真源（详见 {@code
 * doc/ai-product/synapClaim} 单一真源纪律）。{@code label} 为展示用中文；纯展示属性（颜色 / 语气 / 分组 / 提示）仍由前端持有，按 code
 * 与生成的 label 合并。
 */
public record ClaimDictItem(String code, String label) {

  public ClaimDictItem {
    if (code == null) {
      throw new IllegalArgumentException("字典项 code 不可为空");
    }
    code = code;
    label = label == null ? code : label;
  }
}
