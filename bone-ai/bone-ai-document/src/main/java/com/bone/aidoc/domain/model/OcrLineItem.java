package com.bone.aidoc.domain.model;

/** OCR 识别出的费用明细行（如「检查费 120.5」），供理算引擎按责任分摊。 */
public record OcrLineItem(String itemName, double amount) {}
