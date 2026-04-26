package com.bone.tpa.claim.application.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用查询结果
 * 包含至多
 */
@Data
public class GenericQueryRequest implements Serializable {

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "租户id")
    private String bizIdentityCode;

    @Schema(description = "主模型结果")
    private Object main;

    @Schema(description = "副模型结果_1")
    private Object sub1;

    @Schema(description = "副模型结果_2")
    private Object sub2;
}
