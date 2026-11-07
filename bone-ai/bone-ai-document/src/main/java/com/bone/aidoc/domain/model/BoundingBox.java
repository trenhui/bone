package com.bone.aidoc.domain.model;

/**
 * 原图证据定位框（ADR-0046 §三.3：每个字段至少保存坐标，形成可审计证据链）。
 *
 * <p>坐标为图像像素坐标（左上角原点）。OCR 引擎无法给出坐标时可为 {@code null}—— 此时该字段不满足「关键字段证据定位覆盖率 100%」的验收目标，应降级处理。
 */
public record BoundingBox(int x1, int y1, int x2, int y2) {

  /**
   * 是否为有效（非退化且位于第一象限）框。
   *
   * <p>负坐标属于引擎返回的非法证据，不能计入「关键字段证据定位覆盖率」， 更不能据此给启发式质量分的覆盖信号打满分。
   */
  public boolean valid() {
    return x1 >= 0 && y1 >= 0 && x2 > x1 && y2 > y1;
  }
}
