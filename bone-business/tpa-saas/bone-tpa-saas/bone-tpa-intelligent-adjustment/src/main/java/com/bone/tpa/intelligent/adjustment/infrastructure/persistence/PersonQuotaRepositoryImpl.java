package com.bone.tpa.intelligent.adjustment.infrastructure.persistence;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.adjustment.model.PersonalQuota;
import com.bone.tpa.sdk.dao.PersonQuotaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class PersonQuotaRepositoryImpl extends BaseRepository<PersonalQuota, Long> implements PersonQuotaRepository {

    @Autowired
    public PersonQuotaRepositoryImpl(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, PersonalQuota.class, extensionCoordinator);
    }
}
