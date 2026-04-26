package com.bone.tpa.push.dto;

import lombok.Data;

import java.util.List;

/**
 * @Author feihaiming
 * @create 2025/10/16 17:04
 */
@Data
public class ClaimDetailPushDTO {
    private ClaimDTO claim;
    private List<ClaimImageDTO> images;
    private List<ClaimDetailDTO> details;
    private List<ClaimConclusionDTO> conclusions;
}
