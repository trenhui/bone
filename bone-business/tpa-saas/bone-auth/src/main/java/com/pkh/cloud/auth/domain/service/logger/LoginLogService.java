package com.pkh.cloud.auth.domain.service.logger;

import com.pkh.cloud.auth.application.vo.logger.LoginLogCreateReqDTO;
import com.pkh.cloud.auth.application.vo.logger.LoginLogPageReqVO;
import com.pkh.cloud.auth.domain.entity.logger.LoginLogDO;
import com.bone.core.result.PageResult;
import jakarta.validation.Valid;

/**
 * 登录日志 Service 接口
 */
public interface LoginLogService {

    /**
     * 获得登录日志分页
     *
     * @param pageReqVO 分页条件
     * @return 登录日志分页
     */
    PageResult<LoginLogDO> getLoginLogPage(LoginLogPageReqVO pageReqVO);

    /**
     * 创建登录日志
     *
     * @param reqDTO 日志信息
     */
    void createLoginLog(@Valid LoginLogCreateReqDTO reqDTO);

}
