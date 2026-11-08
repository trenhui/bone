package com.bone.claim.application.port.out;

import com.bone.claim.domain.model.evaluation.AiEvalSampleCollectedEvent;

/**
 * 评测样本出站端口（学习层）。
 *
 * <p>把后督差异投递给下游评测流水线。一期实现为进程内领域事件（由既有 outbox 兜底）， 二期换 bone-ai C7 评测集 / MQ 消费者时只换适配器，抽检应用服务与聚合零改动。
 */
public interface AiEvalSamplePort {

  void collect(AiEvalSampleCollectedEvent sample);
}
