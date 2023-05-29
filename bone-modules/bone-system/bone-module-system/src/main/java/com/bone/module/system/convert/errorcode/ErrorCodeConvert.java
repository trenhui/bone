package com.bone.module.system.convert.errorcode;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.system.api.errorcode.dto.ErrorCodeAutoGenerateReqDTO;
import com.bone.module.system.api.errorcode.dto.ErrorCodeRespDTO;
import com.bone.module.system.controller.admin.errorcode.vo.ErrorCodeCreateReqVO;
import com.bone.module.system.controller.admin.errorcode.vo.ErrorCodeExcelVO;
import com.bone.module.system.controller.admin.errorcode.vo.ErrorCodeRespVO;
import com.bone.module.system.dal.dataobject.errorcode.ErrorCodeDO;
import com.bone.module.system.controller.admin.errorcode.vo.ErrorCodeUpdateReqVO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 错误码 Convert
 *
 * @author 芋道源码
 */
@Mapper
public interface ErrorCodeConvert {

    ErrorCodeConvert INSTANCE = Mappers.getMapper(ErrorCodeConvert.class);

    ErrorCodeDO convert(ErrorCodeCreateReqVO bean);

    ErrorCodeDO convert(ErrorCodeUpdateReqVO bean);

    ErrorCodeRespVO convert(ErrorCodeDO bean);

    List<ErrorCodeRespVO> convertList(List<ErrorCodeDO> list);

    PageResult<ErrorCodeRespVO> convertPage(PageResult<ErrorCodeDO> page);

    List<ErrorCodeExcelVO> convertList02(List<ErrorCodeDO> list);

    ErrorCodeDO convert(ErrorCodeAutoGenerateReqDTO bean);

    List<ErrorCodeRespDTO> convertList03(List<ErrorCodeDO> list);

}
