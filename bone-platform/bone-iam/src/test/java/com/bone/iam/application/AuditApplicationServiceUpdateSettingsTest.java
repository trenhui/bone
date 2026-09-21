package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.UpdateAuditSettingsCommand;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.gateway.AuditSettingsGateway;
import com.bone.iam.domain.gateway.TenantProvider;
import com.bone.iam.domain.repository.AuditLogRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AuditApplicationService#updateSettings 入参校验测试。
 *
 * <p>原先「审计设置为空」抛裸 {@code IllegalArgumentException}，落 {@code Exception} 兜底报成 <strong>500</strong>；现为
 * {@code IAM_AUDIT_SETTINGS_REQUIRED} + <strong>400</strong>。
 */
@ExtendWith(MockitoExtension.class)
class AuditApplicationServiceUpdateSettingsTest {

  @Mock private AuditSettingsGateway auditSettingsGateway;

  @Mock private AuditLogRepository auditLogRepository;

  @Mock private TenantProvider tenantProvider;

  @InjectMocks private AuditApplicationService auditApplicationService;

  @Test
  void rejectsNullSettingsAsBadRequest() {
    UpdateAuditSettingsCommand cmd = new UpdateAuditSettingsCommand();

    assertThatThrownBy(() -> auditApplicationService.updateSettings(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 400)
        .hasMessageContaining(IamErrorCodes.AUDIT_SETTINGS_REQUIRED);
  }

  @Test
  void rejectsEmptySettingsAsBadRequest() {
    UpdateAuditSettingsCommand cmd = new UpdateAuditSettingsCommand();
    cmd.setSettings(Map.of());

    assertThatThrownBy(() -> auditApplicationService.updateSettings(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 400)
        .hasMessageContaining(IamErrorCodes.AUDIT_SETTINGS_REQUIRED);
  }
}
