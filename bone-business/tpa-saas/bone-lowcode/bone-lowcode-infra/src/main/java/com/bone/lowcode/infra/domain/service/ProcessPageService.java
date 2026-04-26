package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.PageTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.ProcessHeadPageDO;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.ProcessListPageDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.ProcessHeadPageMapper;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.ProcessListPageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

@Service
public class ProcessPageService {

    @Autowired
    private ProcessHeadPageMapper processHeadPageMapper;

    @Autowired
    private ProcessListPageMapper processListPageMapper;

    public boolean deleteHeadPageByIdList(List<Long> idList) {
        int count = processHeadPageMapper.deleteByIds(idList);
        return count > 0;
    }

    public boolean batchSaveHeadPage(List<ProcessHeadPageDO> headPageDOList) {
        processHeadPageMapper.insert(headPageDOList);
        return true;
    }

    public List<ProcessHeadPageDO> getBaseHeadPageList() {
        LambdaQueryWrapper<ProcessHeadPageDO> wrapper = new LambdaQueryWrapper<ProcessHeadPageDO>()
                .eq(ProcessHeadPageDO::getType, PageTypeEnum.TEMPLATE.getCode())
                .eq(ProcessHeadPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return processHeadPageMapper.selectList(wrapper);
    }

    public List<ProcessHeadPageDO> getHeadPageList() {
        List<ProcessHeadPageDO> list = processHeadPageMapper.selectList(new LambdaQueryWrapper<ProcessHeadPageDO>()
                .eq(ProcessHeadPageDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
        return list;
    }

    public ProcessHeadPageDO getHeadPageByCode(String code, String bizIdentityCode) {
        LambdaQueryWrapper<ProcessHeadPageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProcessHeadPageDO::getCode, code)
                .eq(ProcessHeadPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        if (StringUtils.hasText(bizIdentityCode)) {
            wrapper.eq(ProcessHeadPageDO::getType, PageTypeEnum.BIZ_IDENTITY.getCode())
                    .eq(ProcessHeadPageDO::getBizIdentityCode, bizIdentityCode);
        } else {
            wrapper.eq(ProcessHeadPageDO::getType, PageTypeEnum.TEMPLATE.getCode());
        }
        ProcessHeadPageDO page = processHeadPageMapper.selectOne(wrapper);
        return page;
    }

    public List<ProcessHeadPageDO> getHeadPageByBizCode(String bizIdentityCode) {
        LambdaQueryWrapper<ProcessHeadPageDO> wrapper = new LambdaQueryWrapper<ProcessHeadPageDO>()
                .eq(ProcessHeadPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());

        if (StringUtils.hasText(bizIdentityCode)) {
            wrapper.eq(ProcessHeadPageDO::getType, PageTypeEnum.BIZ_IDENTITY.getCode())
                    .eq(ProcessHeadPageDO::getBizIdentityCode, bizIdentityCode);
        } else {
            wrapper.eq(ProcessHeadPageDO::getType, PageTypeEnum.TEMPLATE.getCode());
        }
        return processHeadPageMapper.selectList(wrapper);
    }

    public boolean updateProcessHeadPageAcceptNull1(ProcessHeadPageDO page) {
        LambdaUpdateWrapper<ProcessHeadPageDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ProcessHeadPageDO::getId, page.getId())
                .set(ProcessHeadPageDO::getParam, page.getParam())
                .set(StringUtils.hasText(page.getDescription()), ProcessHeadPageDO::getDescription, page.getDescription())
                .set(ProcessHeadPageDO::getUpdateTime, new Date())
        ;
        return processHeadPageMapper.update(wrapper) > 0;
    }

    public boolean updateProcessHeadPageAcceptNull2(ProcessHeadPageDO page) {
        LambdaUpdateWrapper<ProcessHeadPageDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ProcessHeadPageDO::getId, page.getId())
                .set(ProcessHeadPageDO::getOptionSetId, page.getOptionSetId())
                .set(ProcessHeadPageDO::getParam, page.getParam())
                .set(ProcessHeadPageDO::getUpdateTime, new Date())
        ;
        return processHeadPageMapper.update(wrapper) > 0;
    }

    //------------------------------------------------------------------------------------------------------------------
    public ProcessListPageDO getProcessListPage(String code, String bizIdentityCode) {
        LambdaQueryWrapper<ProcessListPageDO> wrapper = new LambdaQueryWrapper<ProcessListPageDO>()
                .eq(ProcessListPageDO::getCode, code)
                .eq(ProcessListPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        if (StringUtils.hasText(bizIdentityCode)) {
            wrapper.eq(ProcessListPageDO::getType, PageTypeEnum.BIZ_IDENTITY.getCode())
                    .eq(ProcessListPageDO::getBizIdentityCode, bizIdentityCode);
        } else {
            wrapper.eq(ProcessListPageDO::getType, PageTypeEnum.TEMPLATE.getCode());
        }

        return processListPageMapper.selectOne(wrapper);
    }

    public boolean updateProcessListPageAcceptNull(ProcessListPageDO page) {
        LambdaUpdateWrapper<ProcessListPageDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ProcessListPageDO::getId, page.getId())
                .set(StringUtils.hasText(page.getName()), ProcessListPageDO::getName, page.getName())
                .set(StringUtils.hasText(page.getDescription()), ProcessListPageDO::getDescription, page.getDescription())
                .set(page.getEnablePageHead() != null, ProcessListPageDO::getEnablePageHead, page.getEnablePageHead())
                .set(page.getPageHeadModelId() != null, ProcessListPageDO::getPageHeadModelId, page.getPageHeadModelId())
                .set(ProcessListPageDO::getPageHeadFieldList, page.getPageHeadFieldList()) //允许更新为null
                .set(page.getEnableTab() != null, ProcessListPageDO::getEnableTab, page.getEnableTab())
                .set(ProcessListPageDO::getTabCondition, page.getTabCondition()) //允许更新为null
                .set(page.getDataRange() != null, ProcessListPageDO::getDataRange, page.getDataRange())
                .set(ProcessListPageDO::getUpdateTime, new Date())
        ;
        return processListPageMapper.update(wrapper) > 0;
    }

    public ProcessListPageDO getProcessListPageById(Long pageId) {
        return processListPageMapper.selectById(pageId);
    }

    public List<ProcessListPageDO> getBaseProcessListPage() {
        return processListPageMapper.selectList(new LambdaQueryWrapper<ProcessListPageDO>()
                .eq(ProcessListPageDO::getType, PageTypeEnum.TEMPLATE.getCode())
                .eq(ProcessListPageDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public boolean batchSaveListPage(List<ProcessListPageDO> listPageDOList) {
        processListPageMapper.insert(listPageDOList);
        return true;
    }

    public List<ProcessListPageDO> getListPageByBizCode(String bizIdentityCode) {
        LambdaQueryWrapper<ProcessListPageDO> wrapper = new LambdaQueryWrapper<ProcessListPageDO>()
                .eq(ProcessListPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());

        if (StringUtils.hasText(bizIdentityCode)) {
            wrapper.eq(ProcessListPageDO::getType, PageTypeEnum.BIZ_IDENTITY.getCode())
                    .eq(ProcessListPageDO::getBizIdentityCode, bizIdentityCode);
        } else {
            wrapper.eq(ProcessListPageDO::getType, PageTypeEnum.TEMPLATE.getCode());
        }
        return processListPageMapper.selectList(wrapper);
    }

    public boolean deleteListPageByIdList(List<Long> idList) {
        int count = processListPageMapper.deleteByIds(idList);
        return count > 0;
    }
}
