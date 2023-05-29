package com.bone.infra.convert.file;

import com.bone.base.core.pojo.PageResult;
import com.bone.infra.controller.admin.file.vo.file.FileRespVO;
import com.bone.infra.dal.dataobject.file.FileDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface FileConvert {

    FileConvert INSTANCE = Mappers.getMapper(FileConvert.class);

    FileRespVO convert(FileDO bean);

    PageResult<FileRespVO> convertPage(PageResult<FileDO> page);

}
