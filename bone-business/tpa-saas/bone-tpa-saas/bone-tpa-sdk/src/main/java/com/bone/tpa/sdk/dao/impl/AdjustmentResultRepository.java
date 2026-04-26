package com.bone.tpa.sdk.dao.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.adjustment.model.AdjustmentResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class AdjustmentResultRepository extends BaseRepository<AdjustmentResult, Long> implements com.bone.tpa.sdk.dao.AdjustmentResultRepository {

    @Autowired
    public AdjustmentResultRepository(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, AdjustmentResult.class, extensionCoordinator);
    }
}