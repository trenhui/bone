package com.bone.aidoc.domain.gateway;

import com.bone.aidoc.domain.model.OcrDocType;
import java.io.InputStream;

/**
 * 文档分类端口（ADR-0046 七层管线 ② 票种分类）。
 *
 * <p>对上传材料判定票种（身份证 / 银行卡 / 医疗发票 / 费用清单 / 病历 / 出院记录 / 保单）， 驱动后续引擎路由与字段规则。初版可由调用方显式传入 {@code
 * docType}（见 {@link OcrEngine#parse}）， 分类器作为可选增强：实现后可覆盖显式类型。骨架提供 {@code NoOpDocumentClassifier} 占位。
 */
public interface DocumentClassifier {

  /** 分类文档，返回票种。 */
  OcrDocType classify(InputStream input, String sourceRef);
}
