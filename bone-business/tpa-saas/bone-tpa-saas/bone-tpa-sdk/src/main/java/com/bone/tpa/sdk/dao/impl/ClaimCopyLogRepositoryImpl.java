package com.bone.tpa.sdk.dao.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.claim.model.ClaimCopyLog;
import com.bone.tpa.sdk.dao.ClaimCopyLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ClaimCopyLogRepositoryImpl extends BaseRepository<ClaimCopyLog, Long> implements ClaimCopyLogRepository {

    @Autowired
    public ClaimCopyLogRepositoryImpl(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, ClaimCopyLog.class, extensionCoordinator);
    }
}