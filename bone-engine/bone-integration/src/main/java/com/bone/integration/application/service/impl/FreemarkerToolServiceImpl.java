package com.bone.integration.application.service.impl;

import com.bone.integration.application.dto.FreemarkerDTO;
import com.bone.integration.application.service.IFreemarkerToolService;
import com.bone.integration.utils.FreemarkerUtils;
import org.springframework.stereotype.Component;

@Component
public class FreemarkerToolServiceImpl implements IFreemarkerToolService {
    @Override
    public String execute(FreemarkerDTO dto) {
        try {
            return FreemarkerUtils.process(dto.getBodyParam(), dto.getTemplate(), dto.getHeaderParamList());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
