package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bone.lowcode.infra.application.dto.page.UpdatePageDTO;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.PageTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgPageDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.PageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;


@Service
public class PageService {

    @Autowired
    private PageMapper pageMapper;


    public boolean deleteById(Long pageId) {
        return pageMapper.deleteById(pageId) > 0;
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = pageMapper.deleteByIds(idList);
        return i > 0;
    }

    public CfgPageDO getDoById(Long pageId) {
        LambdaQueryWrapper<CfgPageDO> wrapper = new LambdaQueryWrapper<CfgPageDO>()
                .eq(CfgPageDO::getId, pageId)
                .eq(CfgPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return pageMapper.selectOne(wrapper);
    }

    public boolean addPage(CfgPageDO pageDo) {
        return pageMapper.insert(pageDo) > 0;
    }


    public boolean batchSave(List<CfgPageDO> pageDOList) {
        pageMapper.insert(pageDOList);
        return true;
    }

    public CfgPageDO getByPageCodeAndIdentityCode(String pageCode, String bizIdentityCode) {
        LambdaQueryWrapper<CfgPageDO> wrapper = new LambdaQueryWrapper<CfgPageDO>()
                .eq(CfgPageDO::getCode, pageCode)
                .eq(CfgPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());

        if (StringUtils.hasText(bizIdentityCode)) {
            wrapper.eq(CfgPageDO::getType, PageTypeEnum.BIZ_IDENTITY.getCode())
                    .eq(CfgPageDO::getBizIdentityCode, bizIdentityCode);
        } else {
            wrapper.eq(CfgPageDO::getType, PageTypeEnum.TEMPLATE.getCode());
        }
        return pageMapper.selectOne(wrapper);
    }

    public List<CfgPageDO> getDOListByIdentityCode(String bizIdentityCode) {
        if (StringUtils.hasText(bizIdentityCode)) {
            LambdaQueryWrapper<CfgPageDO> wrapper = new LambdaQueryWrapper<CfgPageDO>()
                    .eq(CfgPageDO::getType, PageTypeEnum.BIZ_IDENTITY.getCode())
                    .eq(CfgPageDO::getBizIdentityCode, bizIdentityCode)
                    .eq(CfgPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());
            return pageMapper.selectList(wrapper);
        } else {
            LambdaQueryWrapper<CfgPageDO> wrapper = new LambdaQueryWrapper<CfgPageDO>()
                    .eq(CfgPageDO::getType, PageTypeEnum.TEMPLATE.getCode())
                    .eq(CfgPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());
            return pageMapper.selectList(wrapper);
        }
    }

    public List<CfgPageDO> getAll() {
        LambdaQueryWrapper<CfgPageDO> wrapper = new LambdaQueryWrapper<CfgPageDO>()
                .eq(CfgPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return pageMapper.selectList(wrapper);

    }

    public List<CfgPageDO> getIdentityCode() {
        LambdaQueryWrapper<CfgPageDO> lqw = new QueryWrapper<CfgPageDO>()
                .select("DISTINCT biz_identity_code")
                .lambda()
                .isNotNull(CfgPageDO::getBizIdentityCode)
                .eq(CfgPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return pageMapper.selectList(lqw);
    }

    public boolean updatePage(UpdatePageDTO pageDO) {
        LambdaUpdateWrapper<CfgPageDO> pageUpdate = new LambdaUpdateWrapper<>();
        pageUpdate.eq(CfgPageDO::getId, pageDO.getId())
                .eq(CfgPageDO::getDeleted, 0)
                .set(CfgPageDO::getBusinessFieldEnabled, pageDO.getBusinessFieldEnabled());
        return pageMapper.update(pageUpdate) > 0;
    }
}
