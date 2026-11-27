package com.bone.procurement.contract.infrastructure;

import com.bone.metadata.sdk.Repository;

/** ContractReviewed Inbox SDK 仓储：条件插入去重由 Adapter 内完成。 */
public interface ContractReviewedInboxSdkRepository
    extends Repository<ContractReviewedInboxEntity, Long> {}
