package com.bone.blueprint.domain.repository.masterdata;

import com.bone.blueprint.domain.model.masterdata.MasterDataRecord;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface MasterDataRecordRepository extends Repository<MasterDataRecord, Long> {
    List<MasterDataRecord> findByMasterDataEntityId(Long masterDataEntityId);
    List<MasterDataRecord> findByMasterDataEntityIdAndStatus(Long masterDataEntityId, String status);
}
