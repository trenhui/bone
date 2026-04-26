package com.bone.tpa.sdk.dao.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.claim.model.InvoiceImageRelation;
import com.bone.tpa.sdk.dao.InvoiceImageRelationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class InvoiceImageRelationRepositoryImpl extends BaseRepository<InvoiceImageRelation, Long> implements InvoiceImageRelationRepository {

    @Autowired
    public InvoiceImageRelationRepositoryImpl(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, InvoiceImageRelation.class, extensionCoordinator);
    }
}