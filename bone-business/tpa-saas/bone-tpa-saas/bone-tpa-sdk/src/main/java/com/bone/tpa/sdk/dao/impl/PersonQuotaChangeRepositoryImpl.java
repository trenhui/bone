package com.bone.tpa.sdk.dao.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.adjustment.model.PersonalQuotaChange;
import com.bone.tpa.sdk.dao.PersonQuotaChangeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class PersonQuotaChangeRepositoryImpl extends BaseRepository<PersonalQuotaChange, Long> implements PersonQuotaChangeRepository {

    @Autowired
    public PersonQuotaChangeRepositoryImpl(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, PersonalQuotaChange.class, extensionCoordinator);
    }
}