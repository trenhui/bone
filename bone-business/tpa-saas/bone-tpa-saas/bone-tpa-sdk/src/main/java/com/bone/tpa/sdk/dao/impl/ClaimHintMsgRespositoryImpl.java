package com.bone.tpa.sdk.dao.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.claim.model.ClaimHintMsg;
import com.bone.tpa.sdk.dao.ClaimHintMsgRespository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ClaimHintMsgRespositoryImpl extends BaseRepository<ClaimHintMsg, Long> implements ClaimHintMsgRespository {

    @Autowired
    public ClaimHintMsgRespositoryImpl(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, ClaimHintMsg.class, extensionCoordinator);
    }
}