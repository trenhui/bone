package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.PageTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.ProcessDetailPageDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.ProcessDetailPageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ProcessDetailPageService {

    @Autowired
    private ProcessDetailPageMapper detailPageMapper;


    public ProcessDetailPageDO getDOByCode(String code, String bizIdentityCode) {
        LambdaQueryWrapper<ProcessDetailPageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessDetailPageDO::getCode, code)
                .eq(ProcessDetailPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        if (StringUtils.hasText(bizIdentityCode)) {
            wrapper.eq(ProcessDetailPageDO::getType, PageTypeEnum.BIZ_IDENTITY.getCode())
                    .eq(ProcessDetailPageDO::getBizIdentityCode, bizIdentityCode);
        } else {
            wrapper.eq(ProcessDetailPageDO::getType, PageTypeEnum.TEMPLATE.getCode());
        }

        return detailPageMapper.selectOne(wrapper);
    }

    public List<ProcessDetailPageDO> getBasePageList() {
        LambdaQueryWrapper<ProcessDetailPageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessDetailPageDO::getType, PageTypeEnum.TEMPLATE.getCode())
                .eq(ProcessDetailPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return detailPageMapper.selectList(wrapper);
    }

    public boolean update(ProcessDetailPageDO page) {
        LambdaUpdateWrapper<ProcessDetailPageDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ProcessDetailPageDO::getId, page.getId())
                .set(ProcessDetailPageDO::getDescription, page.getDescription())
                .set(page.getEnablePageHead() != null, ProcessDetailPageDO::getEnablePageHead, page.getEnablePageHead())
                .set(page.getPageHeadFieldList() != null, ProcessDetailPageDO::getPageHeadFieldList, page.getPageHeadFieldList())
                .set(page.getOpenDetail() != null, ProcessDetailPageDO::getOpenDetail, page.getOpenDetail())
                .set(ProcessDetailPageDO::getSpecification, page.getSpecification())
                .set(ProcessDetailPageDO::getTip, page.getTip())
                .set(page.getImageQuality() != null, ProcessDetailPageDO::getImageQuality, page.getImageQuality())
                .set(page.getImageType() != null, ProcessDetailPageDO::getImageType, page.getImageType())
        ;
        return detailPageMapper.update(wrapper) > 0;
    }

    public boolean batchSave(List<ProcessDetailPageDO> list) {
        detailPageMapper.insert(list);
        return true;
    }

    public List<ProcessDetailPageDO> getByBizCode(String bizIdentityCode) {
        LambdaQueryWrapper<ProcessDetailPageDO> wrapper = new LambdaQueryWrapper<ProcessDetailPageDO>()
                .eq(ProcessDetailPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());

        if (StringUtils.hasText(bizIdentityCode)) {
            wrapper.eq(ProcessDetailPageDO::getType, PageTypeEnum.BIZ_IDENTITY.getCode())
                    .eq(ProcessDetailPageDO::getBizIdentityCode, bizIdentityCode);
        } else {
            wrapper.eq(ProcessDetailPageDO::getType, PageTypeEnum.TEMPLATE.getCode());
        }
        return detailPageMapper.selectList(wrapper);
    }

    public ProcessDetailPageDO getById(Long pageId) {
        return detailPageMapper.selectById(pageId);
    }

    public boolean deleteByIdList(List<Long> idList) {
        int count = detailPageMapper.deleteByIds(idList);
        return count > 0;
    }
}
