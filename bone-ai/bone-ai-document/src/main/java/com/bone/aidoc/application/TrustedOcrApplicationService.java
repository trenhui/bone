package com.bone.aidoc.application;

import com.bone.aidoc.domain.gateway.OcrEngine;
import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.TrustedOcrDocument;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 可信 OCR 应用服务（ADR-0046 §七：OCR 与理赔业务分离的薄门面）。
 *
 * <p>只做委托与日志，不持有领域规则（领域规则在 {@link OcrEngine} 及其编排内）。 与既有 {@code DocumentParseApplicationService}
 * 同范式：解析/识别的编排属于域，应用层不加业务判断。
 */
public class TrustedOcrApplicationService {

  private static final Logger log = LoggerFactory.getLogger(TrustedOcrApplicationService.class);

  private final OcrEngine engine;

  public TrustedOcrApplicationService(OcrEngine engine) {
    this.engine = engine;
  }

  /**
   * 对一份理赔材料执行可信 OCR 解析。
   *
   * @param input 文档流
   * @param tenantId 租户
   * @param sourceRef 来源引用
   * @param docId 文档标识；为空自动生成
   * @param docType 文档类型（驱动识别 schema 与规则）
   */
  public TrustedOcrDocument parse(
      InputStream input, String tenantId, String sourceRef, String docId, OcrDocType docType) {
    TrustedOcrDocument doc = engine.parse(input, tenantId, sourceRef, docId, docType);
    log.info(
        "Trusted OCR parsed: doc={}, tenant={}, docType={}, engine={}, autoPassRate={}, needsHuman={}",
        doc.docId(),
        tenantId,
        docType,
        doc.primaryEngineId(),
        String.format("%.2f", doc.autoPassRate()),
        doc.needsHumanAttention());
    return doc;
  }
}
