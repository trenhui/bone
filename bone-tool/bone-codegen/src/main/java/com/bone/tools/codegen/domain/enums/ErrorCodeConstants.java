package com.bone.tools.codegen.domain.enums;


import com.bone.core.enums.ErrorCode;

/**
 * 代码生成错误码常量类
 *
 * 代码生成模块，使用 1-001-004-000 段错误码
 */
public interface ErrorCodeConstants {

    // ========== 代码生成器 1-001-004-000 ==========
    ErrorCode CODEGEN_IMPORT_TABLE_NULL = new ErrorCode(1_001_004_001, "导入的表不存在");
    ErrorCode CODEGEN_TABLE_EXISTS = new ErrorCode(1_001_004_002, "表定义已经存在");
    ErrorCode CODEGEN_IMPORT_COLUMNS_NULL = new ErrorCode(1_001_004_003, "导入的字段不存在");
    ErrorCode CODEGEN_TABLE_NOT_EXISTS = new ErrorCode(1_001_004_004, "表定义不存在");
    ErrorCode CODEGEN_COLUMN_NOT_EXISTS = new ErrorCode(1_001_004_005, "字段定义不存在");
    ErrorCode CODEGEN_SYNC_COLUMNS_NULL = new ErrorCode(1_001_004_006, "同步的字段不存在");
    ErrorCode CODEGEN_SYNC_NONE_CHANGE = new ErrorCode(1_001_004_007, "同步失败，不存在改变");
    ErrorCode CODEGEN_TABLE_INFO_TABLE_COMMENT_IS_NULL = new ErrorCode(1_001_004_008, "数据库的表注释未填写");
    ErrorCode CODEGEN_TABLE_INFO_COLUMN_COMMENT_IS_NULL = new ErrorCode(1_001_004_009, "数据库的表字段({})注释未填写");
    ErrorCode CODEGEN_MASTER_TABLE_NOT_EXISTS = new ErrorCode(1_001_004_010, "主表(id={})定义不存在，请检查");
    ErrorCode CODEGEN_SUB_COLUMN_NOT_EXISTS = new ErrorCode(1_001_004_011, "子表的字段(id={})不存在，请检查");
    ErrorCode CODEGEN_MASTER_GENERATION_FAIL_NO_SUB_TABLE = new ErrorCode(1_001_004_012, "主表生成代码失败，原因：它没有子表");
    ErrorCode CODEGEN_INSERT_ERROR = new ErrorCode(1_001_004_013, "代码生成数据插入失败");
    
    // ========== 数据源配置 1-001-007-000 ==========
    ErrorCode DATA_SOURCE_CONFIG_NOT_EXISTS = new ErrorCode(1_001_007_000, "数据源配置不存在");
    ErrorCode DATA_SOURCE_CONFIG_NOT_OK = new ErrorCode(1_001_007_001, "数据源配置不正确，无法进行连接");
    ErrorCode DATA_SOURCE_CONFIG_REPEAT = new ErrorCode(1_001_007_002, "数据源配置重复");


}
