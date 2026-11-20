package com.bone.procurement.risk.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.risk.infrastructure.InMemoryRiskAlertRepository;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 应用服务：同来源重复投递幂等，不新增预警记录。 */
class RiskApplicationServiceTest {

  @Test
  @DisplayName("相同 (供应商, 来源, 来源引用) 重复投递返回同一 ID")
  void duplicateDeliveryIsIdempotent() {
    InMemoryRiskAlertRepository repo = new InMemoryRiskAlertRepository();
    RiskApplicationService service = new RiskApplicationService(repo);
    Long first =
        service.raise(1L, "SUP-1", "EXTERNAL", "evt-1", "HIGH", "CREDIT", "ev-1", new Date());
    Long second =
        service.raise(1L, "SUP-1", "EXTERNAL", "evt-1", "HIGH", "CREDIT", "ev-1", new Date());
    assertThat(second).isEqualTo(first);
    assertThat(repo.findBySourceRef(1L, "SUP-1", "EXTERNAL", "evt-1")).isPresent();
  }
}
