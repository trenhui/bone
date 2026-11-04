package com.bone.claim.application.port.out;

import com.bone.claim.domain.model.evidence.valueobject.DocType;
import java.math.BigDecimal;
import java.util.List;

/**
 * 感知层端口（出站）：把单证原文解析为结构化事实。
 *
 * <p>AI 原生契约的第一环——决策图的输入必须是「字段 + 置信度 + 原文坐标」，而不是调用方自报的几个布尔值。 适配器实现可替换：一期数电票文本层域内解析，二期接 bone-ai C1
 * 文档 IR（Docling/PaddleOCR）处理图像件， 应用与领域层零感知。
 *
 * <p><b>fail-closed</b>：{@code available=false} 表示「本次没有可解析的文本层」，消费方必须转人工补录， 绝不能把空结果解释为「已解析且无异常」。
 */
public interface DocumentParsePort {

  ParsedDocument parse(DocType docType, String rawText);

  /** 单条结构化事实；{@code evidenceRef} 为原文坐标（如 {@code text@12:8}），供核赔员回看与评测集标注。 */
  record Field(String code, String value, BigDecimal confidence, String evidenceRef) {}

  /**
   * 解析结果。
   *
   * @param available 是否存在可解析的文本层；false 时其余字段一律不可采信
   * @param complete 必填字段是否齐备（缺字段即转补件/人工，不允许默写）
   * @param minConfidence 字段级最低置信度（与 AI 决策置信度相互独立）
   * @param fields 结构化事实清单
   * @param extractedJson 落 {@code t_claim_evidence.extracted_json} 的完整结果（含坐标与完整性）
   * @param reason 受控原因码（禁自由文本）
   */
  record ParsedDocument(
      boolean available,
      boolean complete,
      BigDecimal minConfidence,
      List<Field> fields,
      String extractedJson,
      String reason) {

    public ParsedDocument {
      fields = fields == null ? List.of() : List.copyOf(fields);
      extractedJson = extractedJson == null ? "" : extractedJson;
      reason = reason == null ? "" : reason;
    }
  }
}
