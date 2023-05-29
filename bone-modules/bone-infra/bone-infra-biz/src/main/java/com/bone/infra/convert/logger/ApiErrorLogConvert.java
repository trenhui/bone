package com.bone.infra.convert.logger;

import com.bone.base.core.pojo.PageResult;
import com.bone.infra.api.logger.dto.ApiErrorLogCreateReqDTO;
import com.bone.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogExcelVO;
import com.bone.infra.controller.admin.logger.vo.apierrorlog.ApiErrorLogRespVO;
import com.bone.infra.dal.dataobject.logger.ApiErrorLogDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * API 错误日志 Convert
 *
 * @author 芋道源码
 */
@Mapper
public interface ApiErrorLogConvert {

    ApiErrorLogConvert INSTANCE = Mappers.getMapper(ApiErrorLogConvert.class);

    ApiErrorLogRespVO convert(ApiErrorLogDO bean);

    PageResult<ApiErrorLogRespVO> convertPage(PageResult<ApiErrorLogDO> page);

    List<ApiErrorLogExcelVO> convertList02(List<ApiErrorLogDO> list);

    ApiErrorLogDO convert(ApiErrorLogCreateReqDTO bean);

}
