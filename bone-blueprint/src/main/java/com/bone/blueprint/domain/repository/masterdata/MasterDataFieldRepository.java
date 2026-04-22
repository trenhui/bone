package com.bone.blueprint.domain.repository.masterdata;

import com.bone.blueprint.domain.model.masterdata.MasterDataField;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface MasterDataFieldRepository extends Repository<MasterDataField, Long> {
    List<MasterDataField> findByMasterDataEntityId(Long masterDataEntityId);
    boolean existsByNameAndMasterDataEntityId(String name, Long masterDataEntityId);
}
