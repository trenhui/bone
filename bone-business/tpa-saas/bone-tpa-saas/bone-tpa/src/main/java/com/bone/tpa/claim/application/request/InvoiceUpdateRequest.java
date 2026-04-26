package com.bone.tpa.claim.application.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class InvoiceUpdateRequest implements Serializable {

    @Schema(description = "更新id")
    private List<Long> idList;

    @Schema(description = "更新类型")
    private String updateType;

    @Schema(description = "更新值")
    private String updateValue;

    @Schema(description = "业务主体")
    private String bizIdentityCode;

    /**租户id*/
    @Schema(description = "租户id")
    private Long tenantId;
}
