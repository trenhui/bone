package com.bone.tpa.sdk.dao.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.claim.model.ClaimFlowConfig;
import com.bone.tpa.sdk.dao.ClaimFlowConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ClaimFlowConfigRepositoryImpl extends BaseRepository<ClaimFlowConfig, Long> implements ClaimFlowConfigRepository {

    @Autowired
    public ClaimFlowConfigRepositoryImpl(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, ClaimFlowConfig.class, extensionCoordinator);
    }
}