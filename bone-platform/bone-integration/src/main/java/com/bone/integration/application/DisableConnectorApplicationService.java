package com.bone.integration.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.exception.DomainException;
import com.bone.integration.application.command.cmd.DisableConnectorCommand;
import com.bone.integration.domain.model.connector.Connector;
import com.bone.integration.domain.repository.ConnectorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 禁用连接器命令处理器。
 *
 * <p><b>不发 DomainEvent 豁免（E-5.4）</b>：连接器状态迁移由 integration 的 Outbox 在基础设施层发布集成事件， 聚合本身不发布 Bone
 * 领域事件；故按 E-5.4（集成事件由 Outbox 另管）声明豁免。 若将来需聚合级领域事件，须改为调用 publishFrom 并移除本豁免。
 */
@Component
@RequiredArgsConstructor
@NoDomainEvent
public class DisableConnectorApplicationService {

  private final ConnectorRepository connectorRepository;

  @Transactional
  public void handle(DisableConnectorCommand cmd) {
    Connector connector = connectorRepository.findById(cmd.id());
    if (connector == null) {
      throw new DomainException("连接器不存在");
    }
    connector.disable();
    connectorRepository.save(connector);
  }
}
