package com.bone.tpa.sdk.dao.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ClaimInvoiceRepository extends BaseRepository<ClaimInvoice, Long> implements com.bone.tpa.sdk.dao.ClaimInvoiceRepository {

    @Autowired
    public ClaimInvoiceRepository(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, ClaimInvoice.class, extensionCoordinator);
    }
}