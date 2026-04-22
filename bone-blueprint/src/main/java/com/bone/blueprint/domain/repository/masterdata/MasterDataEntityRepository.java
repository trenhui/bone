package com.bone.blueprint.domain.repository.masterdata;

import com.bone.blueprint.domain.model.masterdata.MasterDataEntity;
import com.bone.metadata.sdk.Repository;

import java.util.List;

public interface MasterDataEntityRepository extends Repository<MasterDataEntity, Long> {
    boolean existsByName(String name);
    List<MasterDataEntity> findByCategory(String category);
    List<MasterDataEntity> findByStatus(String status);
}
