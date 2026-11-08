package com.bone.claim.application;

import com.bone.claim.domain.model.config.event.FeatureToggleChangedEvent;
import com.bone.core.annotation.NoDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 配置变更风控告警订阅器（G3 / 架构评审后续）。
 *
 * <p>订阅 {@link FeatureToggleChangedEvent}，在事务提交后按风险等级落结构化日志（ERROR 级，告警管道可直采）。
 * 这是「资金侧放量档位自动收紧」这类与自动出金直接相关的配置变更最该被盯防的动作—— 它等于「系统自己把放款权限调小了」，风控侧必须可见、可归因。
 *
 * <p><b>与 {@code remark} 日志不重复</b>：{@code autoRollback} 已在 {@code AiRolloutApplicationService} 内以
 * ERROR 级记录降档写入结果； 本订阅器消费的是<b>事件</b>，不依赖调用方是否记得打日志，也不依赖日志文本解析，是「配置变更」这一领域事实的<b>统一出口</b>。
 *
 * <p><b>E-5.4 豁免</b>：本订阅器只消费事件并告警，不写任何聚合（无 {@code Repository.save}），故不发布领域事件。
 */
@Slf4j
@Component
@NoDomainEvent
@RequiredArgsConstructor
public class FeatureToggleChangeAlert {

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onChanged(FeatureToggleChangedEvent event) {
    log.error(
        "[AI 放量-配置变更] tenantId={} changeType={} stage {} → {} reason={} operator={} beforeConfig={} afterConfig={}",
        event.tenantId(),
        event.changeType(),
        event.beforeStage(),
        event.afterStage(),
        event.reason(),
        event.operator(),
        event.beforeConfigJson(),
        event.afterConfigJson());
  }
}
