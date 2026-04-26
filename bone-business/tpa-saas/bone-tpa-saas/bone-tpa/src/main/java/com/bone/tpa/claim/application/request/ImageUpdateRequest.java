package com.bone.tpa.claim.application.request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 影像件更新
 */
@Data
public class ImageUpdateRequest {

    /**
     * 要处理的影像件id
     */
    private List<Long> idList = new ArrayList<>();


    /**
     * 是否被ocr处理过了
     */
    private Boolean ocrFlag;

    /**
     * 是否需要推送标记
     */
    private Boolean pushFlag;
}
