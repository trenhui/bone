package com.bone.tpa.intelligent.adjustment.dto.request;

import com.bone.tpa.intelligent.adjustment.model.PolicyInfoModel;
import lombok.Data;

/**
 * 绑定保单请求
 */
@Data
public class PolicyBindRequest {

    private Long claimId;

    private PolicyInfoModel policyInfoModel;
}
