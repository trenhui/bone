package com.bone.procurement.contract.infrastructure;

import com.bone.metadata.sdk.Repository;

/** ContractReviewed DLQ SDK 仓储：写入/重放定位由 Adapter 内条件查询完成。 */
public interface ContractReviewedDlqSdkRepository
    extends Repository<ContractReviewedDlqEntity, Long> {}
