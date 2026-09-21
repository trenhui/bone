package com.bone.iam.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.BizException;
import com.bone.iam.application.command.UpdateTenantQuotaCommand;
import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.domain.gateway.TenantDeletionGateway;
import com.bone.iam.domain.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * TenantApplicationService#updateQuota 入参校验测试。
 *
 * <p>这三处原先是裸 {@code IllegalArgumentException}——它不被 {@code IamExceptionHandler} 的任何分支匹配， 会落到 {@code
 * Exception} 兜底变成 <strong>500</strong>（把「客户端参数错」报成服务端故障）。现已收口到 {@code IamErrors}：缺租户 id / 配额为负数一律
 * <strong>400</strong>，并可从响应稳定提取业务码。
 */
@ExtendWith(MockitoExtension.class)
class TenantApplicationServiceQuotaTest {

  @Mock private TenantRepository tenantRepository;

  @Mock private TenantDeletionGateway tenantDeletionGateway;

  @InjectMocks private TenantApplicationService tenantApplicationService;

  @Test
  void rejectsMissingTenantIdAsBadRequest() {
    UpdateTenantQuotaCommand cmd = new UpdateTenantQuotaCommand();

    assertThatThrownBy(() -> tenantApplicationService.updateQuota(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 400)
        .hasMessageContaining(IamErrorCodes.TENANT_ID_REQUIRED);
  }

  @Test
  void rejectsNegativeMaxAccountsAsBadRequest() {
    UpdateTenantQuotaCommand cmd = new UpdateTenantQuotaCommand();
    cmd.setId(10L);
    cmd.setMaxAccounts(-1);

    assertThatThrownBy(() -> tenantApplicationService.updateQuota(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 400)
        .hasMessageContaining(IamErrorCodes.TENANT_QUOTA_INVALID);
  }

  @Test
  void rejectsNegativeMaxRolesAsBadRequest() {
    UpdateTenantQuotaCommand cmd = new UpdateTenantQuotaCommand();
    cmd.setId(10L);
    cmd.setMaxRoles(-5);

    assertThatThrownBy(() -> tenantApplicationService.updateQuota(cmd))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 400)
        .hasMessageContaining(IamErrorCodes.TENANT_QUOTA_INVALID);
  }
}
