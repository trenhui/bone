package com.bone.tpa.api.request;

import lombok.Data;

/**
 * 赔案推送回调请求对象
 */
@Data
public class ClaimPushBackRequest {

    /**
     * 赔案号
     */
    private String claimNo;

    /**
     * -1保司推送失败；0保司已推送；1保司已完成
     */
    private Integer status;

    /**
     * 推送回调原因
     */
    private String pushBackReason;

    /**
     *
     */
    private String errorType;

}
