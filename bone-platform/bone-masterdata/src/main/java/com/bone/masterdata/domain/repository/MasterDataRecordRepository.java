package com.bone.masterdata.domain.repository;

import com.bone.masterdata.domain.model.record.MasterDataRecord;
import com.bone.masterdata.domain.model.record.vo.MasterDataRecordId;
import com.bone.metadata.sdk.Repository;

public interface MasterDataRecordRepository extends Repository<MasterDataRecord, MasterDataRecordId> {
}