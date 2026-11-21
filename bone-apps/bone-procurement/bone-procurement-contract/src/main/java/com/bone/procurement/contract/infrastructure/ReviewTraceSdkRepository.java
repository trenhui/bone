package com.bone.procurement.contract.infrastructure;

import com.bone.metadata.sdk.Repository;

/** 审查追溯 SDK 仓储：追加与按 reviewId 检索由 Adapter 内条件查询完成。 */
public interface ReviewTraceSdkRepository extends Repository<ReviewTraceEntity, Long> {}
