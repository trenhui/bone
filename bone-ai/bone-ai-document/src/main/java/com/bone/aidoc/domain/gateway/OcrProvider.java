package com.bone.aidoc.domain.gateway;

import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.OcrResult;
import java.io.InputStream;
import java.util.Set;

/**
 * OCR 引擎策略（ADR-0046：PaddleOCR / GLM-OCR / MiniCPM 等适配器的统一抽象）。
 *
 * <p>相较既有 {@link OcrPort}（单引擎识别传输契约），本接口在引擎层面补充两点： 一是 {@link #engineId()} 标识具体引擎（证据链与跨引擎交叉需要）；二是
 * {@link #supports(OcrDocType)} 声明支持的票种，供 {@link OcrEngine} 做引擎路由——专用票种优先、通用模型补充。
 *
 * <p>基础设施层用 {@code DelegatingOcrProvider} 包住既有 {@code OcrPort} 实现， 或将来直接实现专用 SDK 适配器（PaddleOCR /
 * GLM-OCR / MiniCPM）。
 */
public interface OcrProvider {

  /** 引擎标识（如 paddle-ocr / glm-ocr / minicpm-ocr）。 */
  String engineId();

  /** 是否支持该文档类型（空集表示通用支持所有票种）。 */
  boolean supports(OcrDocType docType);

  /** 识别一份文档，返回结构化结果（底层走 {@link OcrPort} 或专用 SDK，不含具体模型调用约定）。 */
  OcrResult recognize(InputStream input, String sourceRef, OcrDocType docType);

  /** 默认支持的便捷判断：空集 → 全部支持。 */
  static boolean supportsAnyOf(Set<OcrDocType> supported, OcrDocType docType) {
    return supported.isEmpty() || supported.contains(docType);
  }
}
