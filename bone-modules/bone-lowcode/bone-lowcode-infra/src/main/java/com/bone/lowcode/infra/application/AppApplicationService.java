package com.bone.lowcode.infra.application;

import com.bone.lowcode.infra.domain.model.App;
import com.bone.lowcode.infra.domain.service.AppService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * @author renhui.trh
 */
public class AppApplicationService {

    @Resource
    AppService appService;

    /**
     * 创建应用
     *
     * @param app 应用信息
     * @return 编号
     */
    public Long create(@Valid App app) {
        App newApp =  appService.create(app);
        return (Long)newApp.getId();
    }
}
