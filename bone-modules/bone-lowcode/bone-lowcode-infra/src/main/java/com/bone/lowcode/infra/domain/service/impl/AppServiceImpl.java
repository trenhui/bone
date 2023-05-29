package com.bone.lowcode.infra.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.bone.lowcode.infra.domain.repository.AppRepository;
import com.bone.lowcode.infra.infrastructure.persistence.AppRepositoryMybatis;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.bone.lowcode.infra.domain.model.App;
import com.bone.lowcode.infra.domain.service.AppService;

import java.util.List;


/**
 * 系统应用 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class AppServiceImpl extends BaseServiceImpl<App, Long> implements AppService {
    private final AppRepository appRepository;

    public AppServiceImpl(AppRepositoryMybatis appRepository) {
        super(appRepository);
        this.appRepository = appRepository;
    }

    @Override
    public List<App> customQuery(App app, Pageable pageParam) {
        return appRepository.customQuery(app,(pageParam.getPageNumber())*pageParam.getPageSize(),pageParam.getPageSize());
    }
}
