package com.bone.tpa.facade.request;

import lombok.Data;

@Data
public class TpaReleaseHangUpRequest {
    private String mockTag ;

    private String claimNo ;
    /**
     * 挂起状态（已处理/已取消）
     */
    private String hangUpStatus;




}
