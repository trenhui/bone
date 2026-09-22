package com.bone.integration.domain.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.flow.event.FlowActivatedEvent;
import com.bone.integration.domain.model.flow.event.FlowCreatedEvent;
import com.bone.integration.domain.model.flow.valueobject.FlowStatus;
import org.junit.jupiter.api.Test;

/** {@link IntegrationFlow} 纯单测：DRAFT 起步、激活/停用迁移与名称约束（无容器）。 */
class IntegrationFlowTest {

  private IntegrationFlow createFlow() {
    return IntegrationFlow.create(1L, "订单入库流程", "订单同步至主数据");
  }

  @Test
  void testCreateDefaultsToDraftAndPublishesEvent() {
    IntegrationFlow flow = createFlow();

    assertEquals(FlowStatus.DRAFT, flow.getStatus());
    assertEquals("订单入库流程", flow.getName());
    assertEquals(1, flow.getDomainEvents().size());
    assertInstanceOf(FlowCreatedEvent.class, flow.getDomainEvents().get(0));
  }

  @Test
  void testBlankNameRejected() {
    assertThrows(DomainException.class, () -> IntegrationFlow.create(1L, " ", "desc"));
    assertThrows(DomainException.class, () -> IntegrationFlow.create(1L, null, "desc"));
  }

  @Test
  void testActivateMovesToActiveAndGuardsRedundantActivation() {
    IntegrationFlow flow = createFlow();
    flow.clearDomainEvents();

    flow.activate();
    assertEquals(FlowStatus.ACTIVE, flow.getStatus());
    assertEquals(1, flow.getDomainEvents().size());
    assertInstanceOf(FlowActivatedEvent.class, flow.getDomainEvents().get(0));

    // 已激活再次激活拒绝
    assertThrows(DomainException.class, flow::activate);
  }

  @Test
  void testDeactivateMovesToInactiveAndGuardsRedundantDeactivation() {
    IntegrationFlow flow = createFlow();
    flow.activate();

    flow.deactivate();
    assertEquals(FlowStatus.INACTIVE, flow.getStatus());

    assertThrows(DomainException.class, flow::deactivate);
  }

  @Test
  void testUpdateOverridesProfileAndRejectsBlankName() {
    IntegrationFlow flow = createFlow();

    flow.update("订单入库流程 v2", "新增状态回写");
    assertEquals("订单入库流程 v2", flow.getName());
    assertEquals("新增状态回写", flow.getDescription());

    assertThrows(DomainException.class, () -> flow.update(" ", "desc"));
    assertEquals("订单入库流程 v2", flow.getName());
  }
}
