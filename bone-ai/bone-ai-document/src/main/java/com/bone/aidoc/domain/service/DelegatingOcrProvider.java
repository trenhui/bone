package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.OcrPort;
import com.bone.aidoc.domain.gateway.OcrProvider;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrResult;
import java.io.InputStream;
import java.util.Set;

/**
 * 委托式 OCR 引擎适配器（基础设施层复用既有 {@link OcrPort} 实现的桥）。
 *
 * <p>把既有通用 HTTP OCR 客户端（或 {@code NoOpOcrClient}）包装为带引擎标识与票种能力的 {@link
 * OcrProvider}，无需改动既有识别传输契约即可接入可信 OCR 管线。 将来专用 SDK 适配器（PaddleOCR / GLM-OCR / MiniCPM）可直接实现 {@code
 * OcrProvider} 替换本类。
 */
public class DelegatingOcrProvider implements OcrProvider {

  private final OcrPort port;
  private final String engineId;
  private final Set<OcrDocType> supported;

  public DelegatingOcrProvider(OcrPort port, String engineId, Set<OcrDocType> supported) {
    this.port = port;
    this.engineId = engineId;
    this.supported = supported == null ? Set.of() : supported;
  }

  @Override
  public String engineId() {
    return engineId;
  }

  @Override
  public boolean supports(OcrDocType docType) {
    return OcrProvider.supportsAnyOf(supported, docType);
  }

  @Override
  public OcrResult recognize(InputStream input, String sourceRef, OcrDocType docType) {
    return port.recognize(input, sourceRef, docType);
  }
}
