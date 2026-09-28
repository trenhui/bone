package com.bone.integration.infrastructure.external;

import com.bone.core.exception.BizException;
import com.bone.integration.common.IntegrationErrorCodes;
import com.bone.integration.common.IntegrationErrors;

/**
 * 外部连接器公共行为：未实现时统一 501，禁止假成功（INT-01）。
 *
 * <p><b>为何不再把 {@code INT_CONNECTOR_NOT_IMPLEMENTED} 拼进 message</b>：拼串时语义只存在于中文文案里， 前端与告警只能按文案聚合。改走
 * {@link IntegrationErrors} 后，稳定码落在 {@code BizException.errorCode}， 由 {@code
 * IntegrationExceptionAdvice} 透传进 {@code ProblemDetail.errorCode}（前端 {@code i18n.t('errors.' +
 * errorCode)} 的键）。
 */
public final class ConnectorClientSupport {

  private ConnectorClientSupport() {}

  public static BizException notImplemented(String connectorType, String detail) {
    return IntegrationErrors.of(
        IntegrationErrorCodes.CONNECTOR_NOT_IMPLEMENTED, connectorType + " " + detail);
  }
}
