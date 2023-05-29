package com.bone.lowcode.infra.domain.service;

import java.util.*;

import com.bone.core.domain.BaseService;
import com.bone.lowcode.infra.domain.model.App;
import org.springframework.data.domain.Pageable;


/**
 * @author renhui.trh
 */
public interface AppService extends BaseService<App, Long> {
    /**
     * 自定义查询
     * @param app
     * @param pageParam
     * @return
     */
    List<App> customQuery(App app, Pageable pageParam);
}
