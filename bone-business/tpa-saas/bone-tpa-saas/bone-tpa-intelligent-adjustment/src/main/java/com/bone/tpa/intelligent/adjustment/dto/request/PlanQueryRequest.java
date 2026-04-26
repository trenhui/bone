package com.bone.tpa.intelligent.adjustment.dto.request;

import com.bone.core.result.PageParam;
import lombok.Data;

@Data
public class PlanQueryRequest extends PageParam {

    /**
     * 保单号
     */
    private String policyNo;


}
