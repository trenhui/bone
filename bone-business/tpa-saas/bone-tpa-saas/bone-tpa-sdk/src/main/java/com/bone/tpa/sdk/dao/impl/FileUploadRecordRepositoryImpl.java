package com.bone.tpa.sdk.dao.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.tpa.sdk.claim.model.FileUploadRecord;
import com.bone.tpa.sdk.dao.FileUploadRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class FileUploadRecordRepositoryImpl extends BaseRepository<FileUploadRecord, Long> implements FileUploadRecordRepository {

    @Autowired
    public FileUploadRecordRepositoryImpl(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, FileUploadRecord.class, extensionCoordinator);
    }
}