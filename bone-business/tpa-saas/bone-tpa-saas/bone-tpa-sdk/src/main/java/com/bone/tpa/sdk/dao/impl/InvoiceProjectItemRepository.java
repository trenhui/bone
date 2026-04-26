package com.bone.tpa.sdk.dao.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.claim.model.InvoiceProjectItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class InvoiceProjectItemRepository extends BaseRepository<InvoiceProjectItem, Long> implements com.bone.tpa.sdk.dao.InvoiceProjectItemRepository {

    @Autowired
    public InvoiceProjectItemRepository(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, InvoiceProjectItem.class, extensionCoordinator);
    }
}