package com.bone.aidoc.domain.model;

import java.util.List;

/**
 * OCR 结构化识别结果（TPA 票据 / 病历识别产物）。
 *
 * <p>面向真实 TPA（亿保 / 普康）第一 AI 场景——把发票 / 病历图像转为结构化字段 + 费用明细，消灭手工录入。 AI 只做「识别与抽取」， 金额分摊 / 限额 /
 * 赔付仍由确定性理算引擎裁决（确定性内核不在 AI 层）。
 *
 * @param provider 识别服务标识（如 http-ocr / noop）
 * @param docType 文档类型
 * @param fields 结构化字段（发票号 / 日期 / 医院 / 金额等）
 * @param lineItems 费用明细行
 * @param rawText 全文文本（无结构化字段时的兜底，可入 RAG / 条款比对）
 * @param pageCount 页数
 * @param confidence 整体置信度（低置信度应转人工复核，不直接落业务）
 */
public record OcrResult(
    String provider,
    OcrDocType docType,
    List<RecognizedField> fields,
    List<OcrLineItem> lineItems,
    String rawText,
    int pageCount,
    double confidence) {

  /** 空结果（未配置 OCR / 无识别内容）：字段与明细为空、置信度 0。 */
  public static OcrResult empty(String provider, OcrDocType docType) {
    return new OcrResult(provider, docType, List.of(), List.of(), "", 0, 0.0);
  }
}
