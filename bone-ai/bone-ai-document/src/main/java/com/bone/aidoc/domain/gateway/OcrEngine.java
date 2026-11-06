package com.bone.aidoc.domain.gateway;

import com.bone.aidoc.domain.model.OcrDocType;
import com.bone.aidoc.domain.model.TrustedOcrDocument;
import java.io.InputStream;

/**
 * 可信 OCR 引擎——统一编排端口（ADR-0046 §七：将 OCR 能力独立为领域边界）。
 *
 * <p>对一份理赔材料执行完整七层管线：文档接入 → 预处理/分类 → 多引擎识别 → 结构化抽取 → 可信度与质量校验 → 可信度决策路由 → 理赔业务数据层。产出 {@link
 * TrustedOcrDocument} （字段级证据链 + 质检报告 + 复核路由），下游理赔领域只消费本对象，不感知具体引擎。
 *
 * <p>实现（如 {@code TrustedOcrEngine}）只编排领域端口，不直接调用任何模型 SDK， 从而保证更换引擎 / 接入商业 OCR 不影响理赔核心领域模型。
 */
public interface OcrEngine {

  /**
   * 可信 OCR 解析。
   *
   * @param input 文档流（图像 / 扫描件 / PDF 扫描页）
   * @param tenantId 租户
   * @param sourceRef 来源引用（文件 key / URL）
   * @param docId 文档标识；为空时由实现自动生成
   * @param docType 文档类型（驱动识别 schema 与规则；分类器可在实现内覆盖）
   * @return 可信 OCR 文档（字段级证据 + 质检 + 复核路由）
   */
  TrustedOcrDocument parse(
      InputStream input, String tenantId, String sourceRef, String docId, OcrDocType docType);
}
