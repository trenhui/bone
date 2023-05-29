package com.bone.module.system.convert.logger;

import com.bone.base.core.pojo.PageResult;
import com.bone.module.system.api.logger.dto.OperateLogCreateReqDTO;
import com.bone.module.system.controller.admin.logger.vo.operatelog.OperateLogExcelVO;
import com.bone.module.system.controller.admin.logger.vo.operatelog.OperateLogRespVO;
import com.bone.module.system.dal.dataobject.logger.OperateLogDO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2023-05-29T10:28:15+0800",
    comments = "version: 1.5.3.Final, compiler: javac, environment: Java 17.0.6 (Microsoft)"
)
public class OperateLogConvertImpl implements OperateLogConvert {

    @Override
    public OperateLogDO convert(OperateLogCreateReqDTO bean) {
        if ( bean == null ) {
            return null;
        }

        OperateLogDO operateLogDO = new OperateLogDO();

        operateLogDO.setTraceId( bean.getTraceId() );
        operateLogDO.setUserId( bean.getUserId() );
        operateLogDO.setUserType( bean.getUserType() );
        operateLogDO.setModule( bean.getModule() );
        operateLogDO.setName( bean.getName() );
        operateLogDO.setType( bean.getType() );
        operateLogDO.setContent( bean.getContent() );
        Map<String, Object> map = bean.getExts();
        if ( map != null ) {
            operateLogDO.setExts( new LinkedHashMap<String, Object>( map ) );
        }
        operateLogDO.setRequestMethod( bean.getRequestMethod() );
        operateLogDO.setRequestUrl( bean.getRequestUrl() );
        operateLogDO.setUserIp( bean.getUserIp() );
        operateLogDO.setUserAgent( bean.getUserAgent() );
        operateLogDO.setJavaMethod( bean.getJavaMethod() );
        operateLogDO.setJavaMethodArgs( bean.getJavaMethodArgs() );
        operateLogDO.setStartTime( bean.getStartTime() );
        operateLogDO.setDuration( bean.getDuration() );
        operateLogDO.setResultCode( bean.getResultCode() );
        operateLogDO.setResultMsg( bean.getResultMsg() );
        operateLogDO.setResultData( bean.getResultData() );

        return operateLogDO;
    }

    @Override
    public PageResult<OperateLogRespVO> convertPage(PageResult<OperateLogDO> page) {
        if ( page == null ) {
            return null;
        }

        PageResult<OperateLogRespVO> pageResult = new PageResult<OperateLogRespVO>();

        pageResult.setList( operateLogDOListToOperateLogRespVOList( page.getList() ) );
        pageResult.setTotal( page.getTotal() );

        return pageResult;
    }

    @Override
    public OperateLogRespVO convert(OperateLogDO bean) {
        if ( bean == null ) {
            return null;
        }

        OperateLogRespVO operateLogRespVO = new OperateLogRespVO();

        operateLogRespVO.setTraceId( bean.getTraceId() );
        operateLogRespVO.setUserId( bean.getUserId() );
        operateLogRespVO.setModule( bean.getModule() );
        operateLogRespVO.setName( bean.getName() );
        operateLogRespVO.setType( bean.getType() );
        operateLogRespVO.setContent( bean.getContent() );
        Map<String, Object> map = bean.getExts();
        if ( map != null ) {
            operateLogRespVO.setExts( new LinkedHashMap<String, Object>( map ) );
        }
        operateLogRespVO.setRequestMethod( bean.getRequestMethod() );
        operateLogRespVO.setRequestUrl( bean.getRequestUrl() );
        operateLogRespVO.setUserIp( bean.getUserIp() );
        operateLogRespVO.setUserAgent( bean.getUserAgent() );
        operateLogRespVO.setJavaMethod( bean.getJavaMethod() );
        operateLogRespVO.setJavaMethodArgs( bean.getJavaMethodArgs() );
        operateLogRespVO.setStartTime( bean.getStartTime() );
        operateLogRespVO.setDuration( bean.getDuration() );
        operateLogRespVO.setResultCode( bean.getResultCode() );
        operateLogRespVO.setResultMsg( bean.getResultMsg() );
        operateLogRespVO.setResultData( bean.getResultData() );
        operateLogRespVO.setId( bean.getId() );

        return operateLogRespVO;
    }

    @Override
    public OperateLogExcelVO convert02(OperateLogDO bean) {
        if ( bean == null ) {
            return null;
        }

        OperateLogExcelVO operateLogExcelVO = new OperateLogExcelVO();

        operateLogExcelVO.setId( bean.getId() );
        operateLogExcelVO.setModule( bean.getModule() );
        operateLogExcelVO.setName( bean.getName() );
        if ( bean.getType() != null ) {
            operateLogExcelVO.setType( String.valueOf( bean.getType() ) );
        }
        operateLogExcelVO.setStartTime( bean.getStartTime() );
        operateLogExcelVO.setDuration( bean.getDuration() );

        return operateLogExcelVO;
    }

    protected List<OperateLogRespVO> operateLogDOListToOperateLogRespVOList(List<OperateLogDO> list) {
        if ( list == null ) {
            return null;
        }

        List<OperateLogRespVO> list1 = new ArrayList<OperateLogRespVO>( list.size() );
        for ( OperateLogDO operateLogDO : list ) {
            list1.add( convert( operateLogDO ) );
        }

        return list1;
    }
}
