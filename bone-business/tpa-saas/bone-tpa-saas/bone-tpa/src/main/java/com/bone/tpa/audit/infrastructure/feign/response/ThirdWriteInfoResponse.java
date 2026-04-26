package com.bone.tpa.audit.infrastructure.feign.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * @Author feihaiming
 * @create 2025/9/18 10:57
 */
@Data
public class ThirdWriteInfoResponse {
    @Schema(description = "赔案号")
    private Long claimNumber;

    @Schema(description = "外包回传时间")
    private Date writeDate;

    @Schema(description = "录入人")
    private String writeUser;

    @Schema(description = "原始报文")
    private String writeJson;

    @Schema(description = "转译报文")
    private String writeChinese;
}
