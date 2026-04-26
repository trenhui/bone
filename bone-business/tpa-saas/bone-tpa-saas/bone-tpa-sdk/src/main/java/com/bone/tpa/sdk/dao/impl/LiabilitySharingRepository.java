package com.bone.tpa.sdk.dao.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.adjustment.model.LiabilitySharing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class LiabilitySharingRepository extends BaseRepository<LiabilitySharing, Long> implements com.bone.tpa.sdk.dao.LiabilitySharingRepository {

    @Autowired
    public LiabilitySharingRepository(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, LiabilitySharing.class, extensionCoordinator);
    }
}