package com.bone.aidoc.domain.gateway;

import java.util.List;

/**
 * 页级解析结果（解析器 SPI 输出，IR 组装前的中转模型）。
 *
 * @param pageCount 页数
 * @param pageTexts 逐页文本（index = page - 1）
 * @param provider 解析器标识
 */
public record ParsedDocument(int pageCount, List<String> pageTexts, String provider) {

  public static ParsedDocument of(int pageCount, List<String> pageTexts, String provider) {
    return new ParsedDocument(pageCount, List.copyOf(pageTexts), provider);
  }
}
