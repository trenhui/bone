package com.bone.tpa.facade.request;

import lombok.Data;

import java.util.List;

@Data
public class TpaNewClaimNoRequest {
    /**
     * 要被复制的赔案号
     */
    private List<Long> claimNos;

    /**
     * 是否需要生成新批次号和签收时间
     */
    private Boolean needNewBatchNo;

    /**
     * 是否复制流水号
     */
    private Boolean isCopySerialNo;

    /**
     * 新赔案分配给谁
     */
    private String userName;

    /**
     * 备注
     */
    private String remark;
}
