package com.bone.aidoc.domain.service;

import com.bone.aidoc.domain.gateway.FeedbackCollector;
import com.bone.aidoc.domain.model.ReviewFeedback;

/**
 * {@link FeedbackCollector} 的安全默认实现：丢弃全部反馈。
 *
 * <p>当骨架未装配真实反馈存储时启用。反馈闭环缺失不会阻断识别，但<b>会削弱可信验证能力</b>—— 此时生产应配合告警，提示「自动通过准确率无人验证」。
 */
public class NoOpFeedbackCollector implements FeedbackCollector {

  @Override
  public void record(ReviewFeedback feedback) {}
}
