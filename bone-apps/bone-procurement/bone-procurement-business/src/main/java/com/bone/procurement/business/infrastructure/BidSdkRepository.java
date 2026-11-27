package com.bone.procurement.business.infrastructure;

import com.bone.metadata.sdk.Repository;

/** 投标 SDK 仓储；条件查询与唯一键由 DB 和 Adapter 双层守护。 */
public interface BidSdkRepository extends Repository<BidEntity, Long> {}
