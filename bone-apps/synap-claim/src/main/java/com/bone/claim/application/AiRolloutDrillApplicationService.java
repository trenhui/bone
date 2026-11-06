package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.config.AiRolloutDrill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 放量回滚演练（只读）：与资金侧准入共用 {@link AiRolloutApplicationService} 的单一配置源， 租户经 {@link TenantPort}
 * 取认证主体，禁止由请求参数指定（防越权查看其它租户）。
 */
@Service
@RequiredArgsConstructor
public class AiRolloutDrillApplicationService {

  private final AiRolloutApplicationService rolloutService;
  private final TenantPort tenantPort;

  public AiRolloutDrill.Report drill() {
    return AiRolloutDrill.of(rolloutService.currentStage(tenantPort.currentTenantId()));
  }
}
