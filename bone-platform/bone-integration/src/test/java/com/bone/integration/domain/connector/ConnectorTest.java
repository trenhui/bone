package com.bone.integration.domain.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bone.core.exception.DomainException;
import com.bone.integration.domain.model.connector.event.ConnectorCreatedEvent;
import com.bone.integration.domain.model.connector.event.ConnectorTestedEvent;
import com.bone.integration.domain.model.connector.vo.ConnectorStatus;
import com.bone.integration.domain.model.connector.vo.ConnectorType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** {@link Connector} 纯单测：默认停用、启停防重、更新校验与测试结果事件（无容器）。 */
class ConnectorTest {

  private Connector createConnector() {
    return Connector.create(1L, "订单同步 HTTP", ConnectorType.HTTP, Map.of("url", "http://order"));
  }

  @Test
  void testCreateDefaultsToDisabledAndPublishesEvent() {
    Connector connector = createConnector();

    assertEquals(ConnectorStatus.DISABLED, connector.getStatus());
    assertEquals("订单同步 HTTP", connector.getName());
    assertEquals(ConnectorType.HTTP, connector.getType());
    assertEquals(Map.of("url", "http://order"), connector.getConfig());
    assertEquals(1, connector.getDomainEvents().size());
    assertInstanceOf(ConnectorCreatedEvent.class, connector.getDomainEvents().get(0));
  }

  @Test
  void testCreateRejectsInvalidArguments() {
    assertThrows(
        DomainException.class, () -> Connector.create(1L, "  ", ConnectorType.HTTP, Map.of()));
    assertThrows(DomainException.class, () -> Connector.create(1L, "x", null, Map.of()));
    assertThrows(DomainException.class, () -> Connector.create(1L, "x", ConnectorType.HTTP, null));
  }

  @Test
  void testEnableDisableGuardAgainstRedundantTransition() {
    Connector connector = createConnector();

    connector.enable();
    assertEquals(ConnectorStatus.ENABLED, connector.getStatus());
    // 重复启用拒绝
    assertThrows(DomainException.class, connector::enable);

    connector.disable();
    assertEquals(ConnectorStatus.DISABLED, connector.getStatus());
    // 重复禁用拒绝
    assertThrows(DomainException.class, connector::disable);
  }

  @Test
  void testUpdateOverridesAndRejectsBlankNameOrNullConfig() {
    Connector connector = createConnector();

    connector.update("订单同步 FTP", ConnectorType.FTP, Map.of("host", "ftp://order"));
    assertEquals("订单同步 FTP", connector.getName());
    assertEquals(ConnectorType.FTP, connector.getType());

    assertThrows(
        DomainException.class, () -> connector.update(" ", ConnectorType.FTP, Map.of("h", "1")));
    assertThrows(DomainException.class, () -> connector.updateConfig(null));
    // 校验失败时原名称保持不变
    assertEquals("订单同步 FTP", connector.getName());
  }

  @Test
  void testRecordTestResultAppendsTestedEvent() {
    Connector connector = createConnector();
    connector.clearDomainEvents();

    connector.recordTestResult(true, "连通性正常");

    assertEquals(1, connector.getDomainEvents().size());
    assertInstanceOf(ConnectorTestedEvent.class, connector.getDomainEvents().get(0));
  }
}
