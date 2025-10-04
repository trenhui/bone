package com.bone.core.extension;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/***
 * BizContext
 *
 * @author renhui.trh 2023-11-1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BizContext<T> {
    private String tenantCode;
    private String bizCode;
    private String useCase;
    private String scenario;
    private T data;

    public String getBizIdentity() {
        return getValueString(tenantCode) + ExtPointConstants.SEPARATOR
                + getValueString(bizCode) + ExtPointConstants.SEPARATOR
                + getValueString(useCase) + ExtPointConstants.SEPARATOR
                + getValueString(scenario);
    }

    private String getValueString(String value) {
        return value != null ? value : ExtPointConstants.DEFAULT_VALUE;
    }

    public String getDefaultBizIdentity() {
        return ExtPointConstants.DEFAULT_VALUE + ExtPointConstants.SEPARATOR
                + ExtPointConstants.DEFAULT_VALUE + ExtPointConstants.SEPARATOR
                + ExtPointConstants.DEFAULT_VALUE + ExtPointConstants.SEPARATOR
                + ExtPointConstants.DEFAULT_VALUE;
    }


}