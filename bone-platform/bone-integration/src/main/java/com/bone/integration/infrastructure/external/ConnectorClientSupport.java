package com.bone.integration.infrastructure.external;

import com.bone.core.enums.GlobalErrorCodeConstants;
import com.bone.core.exception.BizException;

/**
 * 外部连接器公共行为：未实现时统一 501，禁止假成功。
 */
public final class ConnectorClientSupport {

    private ConnectorClientSupport() {
    }

    public static BizException notImplemented(String connectorType, String detail) {
        return BizException.of(
                GlobalErrorCodeConstants.NOT_IMPLEMENTED.getCode(),
                "INT_CONNECTOR_NOT_IMPLEMENTED: " + connectorType + " " + detail);
    }
}
