package com.bone.tpa.facade.request;

import lombok.Data;

/**
 * @description:
 * @author: caixuepu
 * @time: 2021/5/12
 */
@Data
public class QueryBalanceRequest {
    /**
     *出险人姓名
     */
    public String personName;
    /**
     *身份证号
     */
    public String personCertId;
    /**
     *保单号
     */
    public String slipCode;
}
