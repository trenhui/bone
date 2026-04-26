package com.bone.tpa.facade.request;

import lombok.Data;

@Data
public class ReportingInfoQueryRequest {

    /**
     * 赔案号
     */
    private String claimNumber;

    private String gyTaskNo;
}
