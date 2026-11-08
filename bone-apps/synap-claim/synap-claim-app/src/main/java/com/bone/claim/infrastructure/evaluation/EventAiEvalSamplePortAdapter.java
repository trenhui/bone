package com.bone.claim.infrastructure.evaluation;

import com.bone.claim.application.port.out.AiEvalSamplePort;
import com.bone.claim.domain.model.evaluation.AiEvalSampleCollectedEvent;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.domain.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 评测样本一期投递：进程内领域事件总线 + 结构化日志（既有 outbox 机制兜底）。
 *
 * <p><b>为什么标注 {@link NoDomainEvent}</b>：本类本身就是"投递领域事件"的动作， 若被写操作守卫再次包装成领域事件，会出现「样本事件 →
 * 再产生样本事件」的自激回路。
 *
 * <p>二期接 bone-ai C7 评测集或 MQ 时替换本类；{@link AiEvalSamplePort} 契约与 {@code AiEvalSampleCollectedEvent}
 * 载荷保持不变。
 */
@NoDomainEvent
@Slf4j
@Component
@RequiredArgsConstructor
public class EventAiEvalSamplePortAdapter implements AiEvalSamplePort {

  private final DomainEventPublisher domainEventPublisher;

  @Override
  public void collect(AiEvalSampleCollectedEvent sample) {
    log.info(
        "[AI_EVAL_SAMPLE] claimId={} inspectionNo={} diffType={} sampleType={} fingerprint={}",
        sample.claimId(),
        sample.inspectionNo(),
        sample.diffType(),
        sample.sampleType(),
        sample.fingerprint());
    domainEventPublisher.publish(sample);
  }
}
