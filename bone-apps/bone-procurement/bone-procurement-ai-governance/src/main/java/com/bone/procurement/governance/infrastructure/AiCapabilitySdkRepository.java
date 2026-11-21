package com.bone.procurement.governance.infrastructure;

import com.bone.metadata.sdk.Repository;

/** AI 能力登记 SDK 仓储：标记接口，CRUD 由 metadata-sdk 提供，条件查询在 Adapter 内构造。 */
public interface AiCapabilitySdkRepository extends Repository<AiCapabilityEntity, Long> {}
