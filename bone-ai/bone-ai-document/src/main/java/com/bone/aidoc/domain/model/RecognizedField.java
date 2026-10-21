package com.bone.aidoc.domain.model;

/** OCR 识别出的单个字段（如发票号 / 金额 / 日期 / 医院名），带模型置信度。 */
public record RecognizedField(String name, String value, double confidence) {}
