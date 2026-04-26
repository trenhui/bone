package com.bone.tpa.sdk.adjustment.model.liability;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 赔付比例
 */
@Data
public class PayPercent {
  private String type; // 赔付比例类型

  /**
   * 同一比例
   * 这里前端是有百分号的，所以只需要int就行了，取两位
   */
  private Integer percent;
  private List<String> factor; // 不同比例因素
  private Map<String, List<RangeObject>> rangeMap; // 区间设置
}
