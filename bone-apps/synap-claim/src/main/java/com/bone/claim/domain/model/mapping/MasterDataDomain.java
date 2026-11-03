package com.bone.claim.domain.model.mapping;

/**
 * 主数据域：映射与别名都按域隔离，避免"医院域的 1001"和"药品域的 1001"互相污染。
 *
 * <p>用枚举而不是自由字符串，是因为域是<b>解析入口的必填维度</b>：写错一个字母（{@code HOSPITAL} 写成 {@code
 * HOSPITAl}）不会报错，只会让该域永远未命中——这类缺陷只有枚举能在写入期拦住。
 */
public enum MasterDataDomain {
  HOSPITAL("HOSPITAL", "医院"),
  DISEASE("DISEASE", "疾病"),
  DRUG("DRUG", "药品"),
  FEE_CATEGORY("FEE_CATEGORY", "费用类别"),
  CERT_TYPE("CERT_TYPE", "证件类型");

  private final String code;
  private final String label;

  MasterDataDomain(String code, String label) {
    this.code = code;
    this.label = label;
  }

  public String code() {
    return code;
  }

  public String label() {
    return label;
  }

  public static MasterDataDomain fromCode(String code) {
    for (MasterDataDomain value : values()) {
      if (value.code.equals(code)) {
        return value;
      }
    }
    throw new IllegalArgumentException("未知主数据域: " + code);
  }
}
