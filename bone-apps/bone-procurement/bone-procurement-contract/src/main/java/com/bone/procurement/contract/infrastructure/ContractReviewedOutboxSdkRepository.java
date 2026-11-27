package com.bone.procurement.contract.infrastructure;

import com.bone.metadata.sdk.Repository;

/** ContractReviewed Outbox SDK 仓储：扫描/定位由 Adapter 内条件查询完成。 */
public interface ContractReviewedOutboxSdkRepository
    extends Repository<ContractReviewedOutboxEntity, Long> {}
