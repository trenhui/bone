package com.bone.tpa.api.request;

import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import lombok.Data;

@Data
public class ReleaseHandUpRequest {
    /**
     * 赔案号
     */
    private Long claimNumber;

    /**
     * 挂起类型
     */
    private String handUpType;//挂起时候的类型

    /**
     * 处理结果
     * 已处理
     * 已取消
     */
    private String dealedStatus ;
    /**
     * 根据handupType 来决定操作
     * 如果是挂起给客户，需要更改影像件信息
     *
     */
    private ClaimDetailSyncVO claimInfo;//更新信息,当挂起类型是客户补充影像件时

}
