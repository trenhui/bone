package com.bone.metadata.sdk.sql.processor;

import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import lombok.extern.slf4j.Slf4j;


/**
 * 标准SQL处理器，不做任何动态处理
 */
@Slf4j
public class PassThroughSqlProcessor extends MyBatisSqlProcessor implements SqlProcessor {

    public PassThroughSqlProcessor(SqlConfigProperties properties) {
        super(properties);
    }
}