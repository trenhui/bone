package com.bone.procurement.contract.infrastructure;

import com.bone.metadata.sdk.Repository;

/** 合同审查 SDK 仓储：标记接口，CRUD 由 metadata-sdk 提供；条件查询在 Adapter 内构造。 */
public interface ContractReviewSdkRepository extends Repository<ContractReviewEntity, Long> {}
