package com.bone.lowcode.studio.domain.service.impl;

import com.bone.core.domain.BaseServiceImpl;
import com.bone.lowcode.studio.domain.repository.FormRepository;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.bone.lowcode.studio.domain.model.Form;
import com.bone.lowcode.studio.domain.service.FormService;

/**
 * 表单 Service 实现类
 *
 * @author 梅山源码
 */
@Service
@Validated
public class FormServiceImpl extends BaseServiceImpl<Form, Long> implements FormService {

    private final FormRepository  formRepository;

    public FormServiceImpl(FormRepository  formRepository) {
        super(formRepository);
        this.formRepository = formRepository;
    }
}
