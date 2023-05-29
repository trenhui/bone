package com.bone.infra.convert.logger;

import com.bone.base.core.pojo.PageResult;
import com.bone.infra.api.logger.dto.ApiAccessLogCreateReqDTO;
import com.bone.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogExcelVO;
import com.bone.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogRespVO;
import com.bone.infra.dal.dataobject.logger.ApiAccessLogDO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:27:59+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class ApiAccessLogConvertImpl implements ApiAccessLogConvert {

    @Override
    public ApiAccessLogRespVO convert(ApiAccessLogDO bean) {
        if ( bean == null ) {
            return null;
        }

        ApiAccessLogRespVO apiAccessLogRespVO = new ApiAccessLogRespVO();

        apiAccessLogRespVO.setTraceId( bean.getTraceId() );
        apiAccessLogRespVO.setUserId( bean.getUserId() );
        apiAccessLogRespVO.setUserType( bean.getUserType() );
        apiAccessLogRespVO.setApplicationName( bean.getApplicationName() );
        apiAccessLogRespVO.setRequestMethod( bean.getRequestMethod() );
        apiAccessLogRespVO.setRequestUrl( bean.getRequestUrl() );
        apiAccessLogRespVO.setRequestParams( bean.getRequestParams() );
        apiAccessLogRespVO.setUserIp( bean.getUserIp() );
        apiAccessLogRespVO.setUserAgent( bean.getUserAgent() );
        apiAccessLogRespVO.setBeginTime( bean.getBeginTime() );
        apiAccessLogRespVO.setEndTime( bean.getEndTime() );
        apiAccessLogRespVO.setDuration( bean.getDuration() );
        apiAccessLogRespVO.setResultCode( bean.getResultCode() );
        apiAccessLogRespVO.setResultMsg( bean.getResultMsg() );
        apiAccessLogRespVO.setId( bean.getId() );
        apiAccessLogRespVO.setCreateTime( bean.getCreateTime() );

        return apiAccessLogRespVO;
    }

    @Override
    public List<ApiAccessLogRespVO> convertList(List<ApiAccessLogDO> list) {
        if ( list == null ) {
            return null;
        }

        List<ApiAccessLogRespVO> list1 = new ArrayList<ApiAccessLogRespVO>( list.size() );
        for ( ApiAccessLogDO apiAccessLogDO : list ) {
            list1.add( convert( apiAccessLogDO ) );
        }

        return list1;
    }

    @Override
    public PageResult<ApiAccessLogRespVO> convertPage(PageResult<ApiAccessLogDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<ApiAccessLogRespVO> pageResult = new PageResult<ApiAccessLogRespVO>();

        pageResult.setList( convertList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }

    @Override
    public List<ApiAccessLogExcelVO> convertList02(List<ApiAccessLogDO> list) {
        if ( list == null ) {
            return null;
        }

        List<ApiAccessLogExcelVO> list1 = new ArrayList<ApiAccessLogExcelVO>( list.size() );
        for ( ApiAccessLogDO apiAccessLogDO : list ) {
            list1.add( apiAccessLogDOToApiAccessLogExcelVO( apiAccessLogDO ) );
        }

        return list1;
    }

    @Override
    public ApiAccessLogDO convert(ApiAccessLogCreateReqDTO bean) {
        if ( bean == null ) {
            return null;
        }

        ApiAccessLogDO.ApiAccessLogDOBuilder apiAccessLogDO = ApiAccessLogDO.builder();

        apiAccessLogDO.traceId( bean.getTraceId() );
        apiAccessLogDO.userId( bean.getUserId() );
        apiAccessLogDO.userType( bean.getUserType() );
        apiAccessLogDO.applicationName( bean.getApplicationName() );
        apiAccessLogDO.requestMethod( bean.getRequestMethod() );
        apiAccessLogDO.requestUrl( bean.getRequestUrl() );
        apiAccessLogDO.requestParams( bean.getRequestParams() );
        apiAccessLogDO.userIp( bean.getUserIp() );
        apiAccessLogDO.userAgent( bean.getUserAgent() );
        apiAccessLogDO.beginTime( bean.getBeginTime() );
        apiAccessLogDO.endTime( bean.getEndTime() );
        apiAccessLogDO.duration( bean.getDuration() );
        apiAccessLogDO.resultCode( bean.getResultCode() );
        apiAccessLogDO.resultMsg( bean.getResultMsg() );

        return apiAccessLogDO.build();
    }

    protected ApiAccessLogExcelVO apiAccessLogDOToApiAccessLogExcelVO(ApiAccessLogDO apiAccessLogDO) {
        if ( apiAccessLogDO == null ) {
            return null;
        }

        ApiAccessLogExcelVO apiAccessLogExcelVO = new ApiAccessLogExcelVO();

        apiAccessLogExcelVO.setId( apiAccessLogDO.getId() );
        apiAccessLogExcelVO.setTraceId( apiAccessLogDO.getTraceId() );
        apiAccessLogExcelVO.setUserId( apiAccessLogDO.getUserId() );
        apiAccessLogExcelVO.setUserType( apiAccessLogDO.getUserType() );
        apiAccessLogExcelVO.setApplicationName( apiAccessLogDO.getApplicationName() );
        apiAccessLogExcelVO.setRequestMethod( apiAccessLogDO.getRequestMethod() );
        apiAccessLogExcelVO.setRequestUrl( apiAccessLogDO.getRequestUrl() );
        apiAccessLogExcelVO.setRequestParams( apiAccessLogDO.getRequestParams() );
        apiAccessLogExcelVO.setUserIp( apiAccessLogDO.getUserIp() );
        apiAccessLogExcelVO.setUserAgent( apiAccessLogDO.getUserAgent() );
        apiAccessLogExcelVO.setBeginTime( apiAccessLogDO.getBeginTime() );
        apiAccessLogExcelVO.setEndTime( apiAccessLogDO.getEndTime() );
        apiAccessLogExcelVO.setDuration( apiAccessLogDO.getDuration() );
        apiAccessLogExcelVO.setResultCode( apiAccessLogDO.getResultCode() );
        apiAccessLogExcelVO.setResultMsg( apiAccessLogDO.getResultMsg() );

        return apiAccessLogExcelVO;
    }
}
