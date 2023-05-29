package com.bone.lowcode.studio.application;

import com.bone.lowcode.studio.domain.model.Form;
import com.bone.lowcode.studio.domain.service.FormService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;

/**
 * 表单 ApplicationService
 *
 * @author 梅山源码
 */
public class FormApplicationService {

    @Resource
    private FormService formService;

    /**
     * 创建表单
     *
     * @param form 表单
     * @return Id
     */
    public Long create(@Valid Form form) {
        return formService.create(form).getId();
    }
}
