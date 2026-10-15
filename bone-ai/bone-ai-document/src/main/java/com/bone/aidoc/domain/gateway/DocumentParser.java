package com.bone.aidoc.domain.gateway;

import java.io.InputStream;

/**
 * 文档解析 SPI（解析器解耦：PDFBox / Tika / Docling 可互换——详设 §2.5“双解析器互换实证”）。
 *
 * <p>第一批只冻结契约：实现方返回页文本 + 页码，条款切片与 span 由应用层统一完成， 保证 IR 模型与解析器私有实现解耦。
 */
public interface DocumentParser {

  /**
   * 解析文档为页级文本。
   *
   * @param input 文档流（PDF / 文本；OCR 类解析器后续批次支持图片）
   * @param sourceRef 来源引用（错误信息携带）
   * @return 页级解析结果
   */
  ParsedDocument parse(InputStream input, String sourceRef);

  /** 解析器标识（IR.provider，如 pdfbox / tika / docling）。 */
  String provider();
}
