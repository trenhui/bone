package com.bone.aidoc.domain.model;

/**
 * OCR 识别出的单个字段（如发票号 / 金额 / 日期 / 医院名），带模型置信度。
 *
 * <p>除基础三要素外，真实多页票据识别会携带字段所在的 {@link #page()}（从 1 计，{@code null} 表示未提供） 与原始图像定位框 {@link
 * #bbox()}（{@code null} 表示引擎未回传坐标）。二者用于把字段级证据精确锚定到原图页码与位置， 满足「关键字段证据定位覆盖率 100%」的验收目标；缺失时由下游（质检 /
 * 证据补全）降级处理，不臆造坐标。
 */
public record RecognizedField(
    String name, String value, double confidence, Integer page, BoundingBox bbox) {

  /** 兼容构造：仅基础三要素（单页假设 / 无坐标）。 */
  public RecognizedField(String name, String value, double confidence) {
    this(name, value, confidence, null, null);
  }
}
