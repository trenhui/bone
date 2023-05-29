package com.bone.infra.service.logger;

import com.bone.infra.api.logger.dto.ApiAccessLogCreateReqDTO;
import com.bone.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogExportReqVO;
import com.bone.infra.controller.admin.logger.vo.apiaccesslog.ApiAccessLogPageReqVO;
import com.bone.infra.convert.logger.ApiAccessLogConvert;
import com.bone.infra.dal.dataobject.logger.ApiAccessLogDO;
import com.bone.infra.dal.mysql.logger.ApiAccessLogMapper;
import com.bone.base.core.pojo.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * API 访问日志 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class ApiAccessLogServiceImpl implements ApiAccessLogService {

    @Resource
    private ApiAccessLogMapper apiAccessLogMapper;

    @Override
    public void createApiAccessLog(ApiAccessLogCreateReqDTO createDTO) {
        ApiAccessLogDO apiAccessLog = ApiAccessLogConvert.INSTANCE.convert(createDTO);
        apiAccessLogMapper.insert(apiAccessLog);
    }

    @Override
    public PageResult<ApiAccessLogDO> getApiAccessLogPage(ApiAccessLogPageReqVO pageReqVO) {
        return apiAccessLogMapper.selectPage(pageReqVO);
    }

    @Override
    public List<ApiAccessLogDO> getApiAccessLogList(ApiAccessLogExportReqVO exportReqVO) {
        return apiAccessLogMapper.selectList(exportReqVO);
    }

}
