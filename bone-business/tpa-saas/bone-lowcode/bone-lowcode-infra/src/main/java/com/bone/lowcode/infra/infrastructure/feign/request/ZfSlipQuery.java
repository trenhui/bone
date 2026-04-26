package com.bone.lowcode.infra.infrastructure.feign.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ZfSlipQuery {
    /**
     * 投保公司名称
     */
    private String corpName;
    /**
     * 保险公司名称
     */
    private String topInsuranceName ;
    /**
     * 保险公司code
     */
    private String insuParentCode;

    /**
     * 保单code
     */
    private String slipCode;

    /**
     * 保险分公司名称
     */
    private String insuranceName ;

    /**
     * 保险分公司的code
     */
    private String insuranceCode;



    private Integer pageSize = 200;

    private Integer pageIndex = 1;
}
