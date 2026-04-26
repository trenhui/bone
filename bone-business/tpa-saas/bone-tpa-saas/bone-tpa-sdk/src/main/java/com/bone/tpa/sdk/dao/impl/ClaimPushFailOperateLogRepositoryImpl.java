package com.bone.tpa.sdk.dao.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.claim.model.ClaimPushFailOperateLog;
import com.bone.tpa.sdk.dao.ClaimPushFailOperateLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ClaimPushFailOperateLogRepositoryImpl extends BaseRepository<ClaimPushFailOperateLog, Long> implements ClaimPushFailOperateLogRepository {

    @Autowired
    public ClaimPushFailOperateLogRepositoryImpl(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, ClaimPushFailOperateLog.class, extensionCoordinator);
    }
}