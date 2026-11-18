package com.bone.procurement.fulfillment.infrastructure;

import com.bone.procurement.fulfillment.domain.port.FinanceAclPort;
import com.bone.procurement.fulfillment.domain.port.InventoryAclPort;
import com.bone.procurement.fulfillment.domain.port.ToleranceConfigPort;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 跨上下文 ACL 默认桩：Inventory/Finance 上游未交付前一律不可用（fail-closed），容差取严格默认 0。
 *
 * <p>运行期绝不推断收货/匹配结果；测试经领域夹具注入事件验证状态机，不依赖真实上游。
 */
public final class FailClosedAclAdapters {

  @Component
  public static class InventoryStub implements InventoryAclPort {
    @Override
    public boolean isAvailable() {
      return false;
    }
  }

  @Component
  public static class FinanceStub implements FinanceAclPort {
    @Override
    public boolean isAvailable() {
      return false;
    }
  }

  @Component
  public static class StrictTolerance implements ToleranceConfigPort {
    @Override
    public BigDecimal tolerance(Long tenantId, String vendorPartyId, String category) {
      return ToleranceConfigPort.super.tolerance(tenantId, vendorPartyId, category);
    }
  }

  private FailClosedAclAdapters() {}
}
