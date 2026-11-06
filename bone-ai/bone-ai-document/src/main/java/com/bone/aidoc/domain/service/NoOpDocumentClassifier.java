package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.DocumentClassifier;
import com.bone.aidoc.domain.model.OcrDocType;
import java.io.InputStream;

/**
 * 占位文档分类器（无分类模型时的安全默认）：返回 {@link OcrDocType#GENERAL}。
 *
 * <p>初版管线以调用方显式传入的 {@code docType} 为主，分类器作为可选增强。 真实分类器（CNN/VLM 票种识别）接入后由基础设施层覆盖本实现。
 */
public class NoOpDocumentClassifier implements DocumentClassifier {

  @Override
  public OcrDocType classify(InputStream input, String sourceRef) {
    return OcrDocType.GENERAL;
  }
}
