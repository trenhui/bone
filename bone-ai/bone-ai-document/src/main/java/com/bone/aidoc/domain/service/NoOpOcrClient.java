package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.OcrPort;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrResult;
import java.io.InputStream;

/**
 * 默认 OCR 实现（未接入识别服务时的安全回退）：返回空识别结果，不抛错。
 *
 * <p>与 RAG 的 {@code NoOpReranker} 同策略——缺 OCR 服务时不阻断装配与调用，由产品侧按 {@code provider="noop"} 与空字段判断
 * 「未识别」而非「服务故障」。接入共享识别服务（{@code bone.ai.document.ocr.enabled=true}）后自动被替换。
 */
public class NoOpOcrClient implements OcrPort {

  @Override
  public OcrResult recognize(InputStream input, String sourceRef, OcrDocType docType) {
    return OcrResult.empty("noop", docType);
  }

  @Override
  public String provider() {
    return "noop";
  }
}
