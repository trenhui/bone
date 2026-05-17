package com.bone.masterdata.adapter.web.support;

import com.bone.core.enums.GlobalErrorCodeConstants;
import com.bone.core.exception.BizException;

/**
 * 主数据 API 占位：未实现能力统一 501，禁止假成功。
 */
public final class MasterdataApiSupport {

    private MasterdataApiSupport() {
    }

    public static BizException notImplemented(String errorCode, String detail) {
        return BizException.of(GlobalErrorCodeConstants.NOT_IMPLEMENTED.getCode(), errorCode + ": " + detail);
    }
}
