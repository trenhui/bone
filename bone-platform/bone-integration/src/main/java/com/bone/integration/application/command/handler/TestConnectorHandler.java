package com.bone.integration.application.command.handler;

import com.bone.core.exception.DomainException;
import com.bone.integration.application.command.cmd.TestConnectorCommand;
import com.bone.integration.application.port.IntegrationExecutionRecorder;
import com.bone.integration.application.service.ConnectorService;
import com.bone.integration.domain.connector.Connector;
import com.bone.integration.domain.repository.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 测试连接器命令处理器 */
@Component
@RequiredArgsConstructor
public class TestConnectorHandler {

  private final ConnectorService connectorService;
  private final ConnectorRepository connectorRepository;
  private final IntegrationExecutionRecorder integrationMetrics;

  @Transactional
  public Boolean handle(TestConnectorCommand cmd) {
    Connector connector = connectorRepository.findById(cmd.id());
    if (connector == null) {
      throw new DomainException("连接器不存在");
    }

    boolean success = connectorService.testConnector(connector);
    integrationMetrics.recordConnectorTest(connector.getType().name(), success);
    String message = success ? "连接测试成功" : "连接测试失败";
    connector.recordTestResult(success, message);

    // 注意：领域事件发布应在 CommandHandler 外部或通过 AOP 处理
    // 这里简化处理，实际项目中可能需要调整

    return success;
  }
}
