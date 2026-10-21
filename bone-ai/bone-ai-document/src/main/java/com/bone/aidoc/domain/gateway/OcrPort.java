package com.bone.aidoc.domain.gateway;

import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrResult;
import java.io.InputStream;

/**
 * OCR / 多模态识别端口（TPA 票据 / 病历 → 结构化）。
 *
 * <p>与 {@link DocumentParser}（PDF 文本层抽取）互补：本端口面向<b>图像 / 扫描件 / 多模态</b>识别。 实现走共享视觉 / OCR 推理服务（TEI /
 * 通用 vision LLM，多副本友好），进程内 ONNX 作 L4 备选，消费方可 {@code @Bean} 覆盖。
 *
 * <p>识别失败（provider 故障）抛错，交由上层按 SLA 转 HITL——不伪造空结果把「服务挂了」伪装成「图上没字」。
 */
public interface OcrPort {

  /**
   * 识别一份票据 / 病历图像，返回结构化字段 + 费用明细。
   *
   * @param input 图像 / 扫描件流（jpg / png / pdf 扫描页）
   * @param sourceRef 来源引用（错误信息携带）
   * @param docType 文档类型（驱动识别 schema）
   */
  OcrResult recognize(InputStream input, String sourceRef, OcrDocType docType);

  /** 识别服务标识（如 http-ocr / noop）。 */
  String provider();
}
