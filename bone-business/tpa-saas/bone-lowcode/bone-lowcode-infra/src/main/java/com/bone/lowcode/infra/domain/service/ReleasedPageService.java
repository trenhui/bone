package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.PageTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgReleasedPageDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.ReleasedPageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;


@Service
public class ReleasedPageService {

    @Autowired
    private ReleasedPageMapper releasedPageMapper;


    public boolean save(CfgReleasedPageDO releasedPageDO) {
        return releasedPageMapper.insert(releasedPageDO) > 0;
    }


    public boolean batchSave(List<CfgReleasedPageDO> list) {
        releasedPageMapper.insert(list);
        return true;
    }


    public CfgReleasedPageDO getLastBasicDO() {
        CfgReleasedPageDO releasedPageDO = releasedPageMapper.selectOne(new LambdaQueryWrapper<CfgReleasedPageDO>()
                .eq(CfgReleasedPageDO::getType, PageTypeEnum.TEMPLATE.getCode())
                .eq(CfgReleasedPageDO::getDeleted, DeletedEnum.UNDELETED.getCode())
                .orderByDesc(CfgReleasedPageDO::getCreateTime)
                .last("limit 1"));
        return releasedPageDO;
    }

    public CfgReleasedPageDO getLatestExclusiveDO(String bizIdentityCode) {
        CfgReleasedPageDO releasedPageDO = releasedPageMapper.selectOne(new LambdaQueryWrapper<CfgReleasedPageDO>()
                .eq(CfgReleasedPageDO::getType, PageTypeEnum.BIZ_IDENTITY.getCode())
                .eq(CfgReleasedPageDO::getBizIdentityCode, bizIdentityCode)
                .eq(CfgReleasedPageDO::getDeleted, DeletedEnum.UNDELETED.getCode())
                .orderByDesc(CfgReleasedPageDO::getCreateTime)
                .last("limit 1"));
        return releasedPageDO;
    }

    public CfgReleasedPageDO getLastDOByPageCodeBizIdentityCode(String pageCode, String bizIdentityCode) {
        LambdaQueryWrapper<CfgReleasedPageDO> wrapper = new LambdaQueryWrapper<CfgReleasedPageDO>()
                .eq(CfgReleasedPageDO::getPageCode, pageCode)
                .eq(CfgReleasedPageDO::getDeleted, DeletedEnum.UNDELETED.getCode())
                .orderByDesc(CfgReleasedPageDO::getCreateTime)
                .last("limit 1");
        if (StringUtils.hasText(bizIdentityCode)) {
            wrapper.eq(CfgReleasedPageDO::getBizIdentityCode, bizIdentityCode);
        } else {
            wrapper.isNull(!StringUtils.hasText(bizIdentityCode), CfgReleasedPageDO::getBizIdentityCode);
        }

        CfgReleasedPageDO releasedPageDO = releasedPageMapper.selectOne(wrapper);
        return releasedPageDO;
    }

    public CfgReleasedPageDO getDOByVersion(Long versionId, String pageCode) {
        LambdaQueryWrapper<CfgReleasedPageDO> wrapper = new LambdaQueryWrapper<CfgReleasedPageDO>()
                .eq(CfgReleasedPageDO::getVersionId, versionId)
                .eq(CfgReleasedPageDO::getPageCode, pageCode)
                .eq(CfgReleasedPageDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return releasedPageMapper.selectOne(wrapper);
    }
}
