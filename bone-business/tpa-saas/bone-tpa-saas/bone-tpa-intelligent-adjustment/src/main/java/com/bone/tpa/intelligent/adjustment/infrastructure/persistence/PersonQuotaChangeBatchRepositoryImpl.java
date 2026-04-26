package com.bone.tpa.intelligent.adjustment.infrastructure.persistence;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.adjustment.model.PersonalQuotaChangeBatch;
import com.bone.tpa.sdk.dao.PersonQuotaChangeBatchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class PersonQuotaChangeBatchRepositoryImpl extends BaseRepository<PersonalQuotaChangeBatch, Long> implements PersonQuotaChangeBatchRepository {

    @Autowired
    public PersonQuotaChangeBatchRepositoryImpl(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, PersonalQuotaChangeBatch.class, extensionCoordinator);
    }
}
