package com.bone.lowcode.infra.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldsetDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.FileUploadRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * FileUploadRecord
 */
@Mapper
public interface FileUploadRecordMapper extends BaseMapper<FileUploadRecord> {
}
