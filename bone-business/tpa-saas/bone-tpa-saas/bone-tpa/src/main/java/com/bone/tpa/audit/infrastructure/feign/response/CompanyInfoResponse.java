package com.bone.tpa.audit.infrastructure.feign.response;

import lombok.Data;

/**
 * @Author feihaiming
 * @create 2025/9/18 17:11
 */
@Data
public class CompanyInfoResponse {
    private String companyId;//公司id
    private String companyName;//公司名称
    private String companyParentId;//父公司id
    private String companyNo;//机构编号
    private String remark;//备注
}
