package com.bone.tpa.claim.application;

import com.bone.tpa.claim.application.dto.EnumOptionDTO;
import com.bone.tpa.claim.application.dto.EnumSelectDTO;
import com.bone.tpa.claim.domain.service.PageEnumsSelectService;
import com.bone.tpa.claim.infrastructure.log.SimpleLog;
import com.bone.core.util.BizContextUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * 页面枚举应用层服务
 */
@Service
@Transactional
public class BasicConfigApplicationService {
    @Autowired
    private PageEnumsSelectService pageEnumsSelectService;


    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public List<EnumOptionDTO> queryEnumValue(String enumCode) {
        List<EnumOptionDTO> optionDTOList = pageEnumsSelectService.getOptionSetByEnum(enumCode);

        return optionDTOList;
    }

    @SimpleLog
    @Transactional(rollbackFor = Throwable.class)
    public List<EnumSelectDTO> queryEnumList() {
        List<EnumSelectDTO> enumSelectDTOS = pageEnumsSelectService.getallEnumsCodeList();

        return enumSelectDTOS;
    }


    @Transactional(rollbackFor = Throwable.class)
    public String getCurrentLoginUser() {
        return BizContextUtils.getUser();
    }
}
