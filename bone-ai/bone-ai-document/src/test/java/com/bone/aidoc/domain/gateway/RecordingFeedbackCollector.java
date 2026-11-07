package com.bone.aidoc.domain.gateway;

import com.bone.aidoc.domain.model.ReviewFeedback;
import java.util.ArrayList;
import java.util.List;

/** 测试用反馈采集实现：记录所有 feedback，供断言验证「应用层确实采集」。 */
public class RecordingFeedbackCollector implements FeedbackCollector {

  private final List<ReviewFeedback> recorded = new ArrayList<>();

  @Override
  public void record(ReviewFeedback feedback) {
    recorded.add(feedback);
  }

  public List<ReviewFeedback> recorded() {
    return recorded;
  }

  public int confirmedErrors() {
    return (int) recorded.stream().filter(ReviewFeedback::isConfirmedError).count();
  }
}
