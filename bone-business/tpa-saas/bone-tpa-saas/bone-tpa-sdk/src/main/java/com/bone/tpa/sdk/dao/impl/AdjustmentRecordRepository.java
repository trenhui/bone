package com.bone.tpa.sdk.dao.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.adjustment.model.AdjustmentRecord;
import com.bone.tpa.sdk.dao.AdjustmentRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class AdjustmentRecordRepository extends BaseRepository<AdjustmentRecord, Long> implements com.bone.tpa.sdk.dao.AdjustmentRecordRepository {

    @Autowired
    public AdjustmentRecordRepository(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, AdjustmentRecord.class, extensionCoordinator);
    }
}