package com.bone.procurement.contract.infrastructure;

import com.bone.procurement.contract.application.ConditionalOnContractAiReviewEnabled;
import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import com.bone.procurement.contract.domain.model.review.ReviewTraceEntry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/** 受控内存追溯链：降级 / 停用 / 人工推翻留痕，快照条目只追加不改写。 */
@Component
@ConditionalOnContractAiReviewEnabled
public class InMemoryReviewTraceAdapter implements ReviewTracePort {

  private final List<ReviewTraceEntry> entries = new CopyOnWriteArrayList<>();

  @Override
  public void append(ReviewTraceEntry entry) {
    entries.add(entry);
  }

  @Override
  public List<ReviewTraceEntry> findByReviewId(String reviewId) {
    return entries.stream().filter(e -> e.reviewId().equals(reviewId)).toList();
  }
}
