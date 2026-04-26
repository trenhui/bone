package com.bone.tpa.sdk.dao.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.claim.model.SignRecordTrackLog;
import com.bone.tpa.sdk.dao.SignRecordTrackLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class SignRecordTrackLogRepositoryImpl extends BaseRepository<SignRecordTrackLog, Long> implements SignRecordTrackLogRepository {

    @Autowired
    public SignRecordTrackLogRepositoryImpl(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, SignRecordTrackLog.class, extensionCoordinator);
    }
}