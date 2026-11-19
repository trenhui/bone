package com.bone.procurement.contract.domain.gateway;

import com.bone.procurement.contract.domain.model.ReviewTraceEntry;
import java.util.List;

/** 审查留痕出站端口：降级 / 停用 / 人工推翻入追溯链，快照不可变。 */
public interface ReviewTracePort {

  void append(ReviewTraceEntry entry);

  List<ReviewTraceEntry> findByReviewId(String reviewId);
}
