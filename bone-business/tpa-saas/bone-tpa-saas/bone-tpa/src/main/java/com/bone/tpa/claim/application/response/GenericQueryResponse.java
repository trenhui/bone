package com.bone.tpa.claim.application.response;

import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.core.tenant.TenantAbstractEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用查询结果
 * 包含至多
 */
@Data
public class GenericQueryResponse implements Serializable {

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "业务主体码")
    private String bizIdentityCode;

    @Schema(description = "主模型结果")
    private Object main;

    @Schema(description = "副模型结果_1")
    private Object sub1;

    @Schema(description = "副模型结果_2")
    private Object sub2;

    public GenericQueryResponse() {

    }

    public GenericQueryResponse(Object main) {
        this.setMain(main);

        if (main instanceof TenantAbstractEntity<?,?>) {
            this.setTenantId(String.valueOf(((TenantAbstractEntity<?, ?>) main).getTenantId()));
        }
        if (main instanceof ExtensibleObject<?,?>) {
            this.setBizIdentityCode(((ExtensibleObject<?, ?>) main).getBizIdentityCode());
        }
    }
}
