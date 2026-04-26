package com.bone.tpa.task.service;

import com.bone.tpa.sdk.claim.model.ClaimImage;
import com.bone.tpa.task.service.vo.ImageOcrChangeResult;

public interface ImageOcrChangeService {
    /**
     * 通过ocr识别，把图片变成发票和消费项目的对象（不落库）
     * 如果有异常，则直接抛出
     * @param claimNumber
     * @param image
     * @return
     */
    ImageOcrChangeResult changeImageOcr(Long claimNumber,ClaimImage image);
}
