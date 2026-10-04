package com.bone.masterdata.domain.service.quality;

import java.util.HashMap;
import java.util.Map;

/**
 * 单条主数据记录的字段快照（字段编码 → 值），供质量规则求值使用。
 *
 * <p>只承载求值所需的最小信息（记录 ID + 扁平字段值），避免求值器反向依赖 {@code MasterDataRecord} 的持久化形态。
 */
public final class RecordFields {

  private final Long recordId;
  private final String code;
  private final Map<String, String> values;

  private RecordFields(Long recordId, String code, Map<String, String> values) {
    this.recordId = recordId;
    this.code = code;
    // 不能 Map.copyOf：JSON 反序列化可能产生 null 值，copyOf 会直接 NPE。
    this.values = values == null ? new HashMap<>() : new HashMap<>(values);
  }

  public static RecordFields of(Long recordId, Map<String, String> values) {
    return new RecordFields(recordId, null, values);
  }

  /**
   * 带业务编码的快照（推荐入口）。
   *
   * <p>业务编码只用于生成人可读的质检明细文案（"客户 C001 未通过非空校验"）；求值器不读它，因此缺失时退化为 recordId，不影响任何规则判定。
   */
  public static RecordFields of(Long recordId, String code, Map<String, String> values) {
    return new RecordFields(recordId, code, values);
  }

  public Long recordId() {
    return recordId;
  }

  /** 记录业务编码；可能为 {@code null}（存量数据未补录），此时 {@link #label()} 退化为 recordId。 */
  public String code() {
    return code;
  }

  /** 人类可读标识：优先业务编码，缺失时用 recordId。 */
  public String label() {
    return code == null || code.isBlank() ? String.valueOf(recordId) : code;
  }

  /** 取字段值；缺失或空白一律返回 {@code null}，使「空值不参与校验」在求值器内只有一处判定。 */
  public String value(String field) {
    if (field == null) {
      return null;
    }
    String raw = values.get(field);
    return raw == null || raw.isBlank() ? null : raw.trim();
  }
}
