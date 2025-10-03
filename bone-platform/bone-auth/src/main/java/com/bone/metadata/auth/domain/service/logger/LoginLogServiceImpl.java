package com.bone.metadata.auth.domain.service.logger;


import com.bone.metadata.auth.application.vo.logger.LoginLogCreateReqDTO;
import com.bone.metadata.auth.application.vo.logger.LoginLogPageReqVO;
import com.bone.metadata.auth.domain.entity.logger.LoginLogDO;
import com.bone.metadata.auth.domain.mapper.logger.LoginLogMapper;
import com.bone.core.result.PageResult;
import com.bone.core.util.BeanUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * 登录日志 Service 实现
 */
@Service
@Validated
public class LoginLogServiceImpl implements LoginLogService {

    @Resource
    private LoginLogMapper loginLogMapper;

    @Override
    public PageResult<LoginLogDO> getLoginLogPage(LoginLogPageReqVO pageReqVO) {
        return loginLogMapper.selectPage(pageReqVO);
    }

    @Override
    public void createLoginLog(LoginLogCreateReqDTO reqDTO) {
        LoginLogDO loginLog = BeanUtils.toBean(reqDTO, LoginLogDO.class);
        loginLogMapper.insert(loginLog);
    }

}
