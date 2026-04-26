package com.bone.tpa.sdk.dao.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.claim.model.ClaimHookConfig;
import com.bone.tpa.sdk.dao.ClaimHookConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ClaimHookConfigRepositoryImpl extends BaseRepository<ClaimHookConfig, Long> implements ClaimHookConfigRepository {

    @Autowired
    public ClaimHookConfigRepositoryImpl(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, ClaimHookConfig.class, extensionCoordinator);
    }
}