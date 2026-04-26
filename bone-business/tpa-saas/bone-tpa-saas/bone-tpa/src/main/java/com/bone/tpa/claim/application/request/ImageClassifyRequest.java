package com.bone.tpa.claim.application.request;

import com.bone.tpa.claim.application.dto.ClaimImageDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 影像件更新
 */
@Data
public class ImageClassifyRequest {

    /**
     * 要处理的影像件id
     */
    List<ClaimImageDTO> claimImageDTOList = new ArrayList<>();

}
