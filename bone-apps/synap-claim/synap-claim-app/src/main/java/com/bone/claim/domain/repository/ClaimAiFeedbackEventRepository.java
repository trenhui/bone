package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.feedback.ClaimAiFeedbackEvent;
import com.bone.claim.domain.model.feedback.FeedbackCountRow;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.sql.Timestamp;
import java.util.List;

// 接口级 AUTO：外置 SQL 模板方法必须显式声明租户策略，否则 SDK 启动期拒绝注册。
// Criteria 通道另由 TenantFilterInjector 注入租户，无需注解。
@TenantScope(TenantScopeMode.AUTO)
public interface ClaimAiFeedbackEventRepository extends Repository<ClaimAiFeedbackEvent, Long> {

  default ClaimAiFeedbackEvent findByEventId(Long tenantId, String eventId) {
    return findOneByCriteria(
        Criteria.<ClaimAiFeedbackEvent>create()
            .eq(ClaimAiFeedbackEvent::getTenantId, tenantId)
            .eq(ClaimAiFeedbackEvent::getEventId, eventId));
  }

  default List<ClaimAiFeedbackEvent> findByClaimId(Long tenantId, long claimId, int limit) {
    return findByCriteria(
        Criteria.<ClaimAiFeedbackEvent>create()
            .eq(ClaimAiFeedbackEvent::getTenantId, tenantId)
            .eq(ClaimAiFeedbackEvent::getClaimId, claimId)
            .orderByDesc(ClaimAiFeedbackEvent::getCreatedAt)
            .page(1, limit));
  }

  // 30 天窗口的租户级采纳率分子/分母；按 bucket 聚合，不回原始事件。
  List<FeedbackCountRow> countByBucket(@Param("since") Timestamp since);
}
