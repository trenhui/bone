package com.bone.procurement.contract.infrastructure;

import com.bone.procurement.contract.domain.gateway.ReviewTracePort;
import com.bone.procurement.contract.domain.model.review.ReviewTraceEntry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 受控内存追溯链（已退役）：文件保留作骨架对照，不再注册为 bean。
 *
 * @deprecated 端口已由 {@link ReviewTraceAdapter} 委托 SDK 持久化仓储实现；同接口保留 bean 会双 bean。
 */
@Deprecated
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
