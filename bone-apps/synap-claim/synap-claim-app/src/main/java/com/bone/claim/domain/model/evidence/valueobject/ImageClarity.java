package com.bone.claim.domain.model.evidence.valueobject;

/**
 * 影像清晰度三态（P0-A，0133 DDL 受控码真源）。
 *
 * <p>与原型影像详情的绿/红角标一一对应：{@link #CLEAR} 可入理算，{@link #UNCLEAR}/{@link #INCOMPLETE} 需补传。 库内形态是 {@code
 * VARCHAR(16) NOT NULL DEFAULT 'CLEAR'}，本枚举守值域，禁止自由文本。
 *
 * <p><b>受控码解析口径（{@link #fromCode}）</b>：
 *
 * <ul>
 *   <li>{@code null}/空白 → {@link #CLEAR}：对齐 DDL 的 {@code DEFAULT 'CLEAR'} 与存量行口径（0133 明确不回填历史判断）；仓储
 *       SQL 漏列时读出的 null 也按此处理，不能让「未标注」把所有锁定都挡死。
 *   <li>未知码 → {@link #INCOMPLETE}（fail-closed）：绝不能把认不出的码静默归为 CLEAR 而放行进理算/锁定； 归到非 CLEAR
 *       态后，锁定守卫会把它计入阻断清单并返回 {@code SC_CLAIM_EVIDENCE_UNCLEAR_BLOCKS_LOCK}， 由人工定位修正。客户端在边界上传未知枚举值由
 *       Jackson 直接拒绝，这里守的是库内值。
 * </ul>
 */
public enum ImageClarity {
  /** 清晰可入理算。 */
  CLEAR,
  /** 不清晰（模糊/反光/遮挡），需补传。 */
  UNCLEAR,
  /** 不完整（缺角/截断/未拍全），需补传。 */
  INCOMPLETE;

  /**
   * 受控码解析：null/空白回落 CLEAR（DDL 默认态），未知码 fail-closed 归 INCOMPLETE。
   *
   * <p>大小写与首尾空白不敏感（受控码从库/接口读入后统一归一）。
   */
  public static ImageClarity fromCode(String code) {
    if (code == null || code.isBlank()) {
      return CLEAR;
    }
    String normalized = code.trim();
    for (ImageClarity value : values()) {
      if (value.name().equalsIgnoreCase(normalized)) {
        return value;
      }
    }
    return INCOMPLETE;
  }
}
