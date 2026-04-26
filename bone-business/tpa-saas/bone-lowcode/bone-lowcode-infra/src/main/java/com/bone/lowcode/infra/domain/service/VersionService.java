package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgVersionDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.VersionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;

@Service
public class VersionService {

    @Autowired
    private VersionMapper versionMapper;


    public CfgVersionDO saveVersionInfo(Byte type, String remark, Long id, String identityCode) {
        CfgVersionDO versionDO = new CfgVersionDO();
        if (id != null) {
            versionDO.setId(id);
        }
        versionDO.setType(type);
        versionDO.setIdentityCode(identityCode);
        versionDO.setRemark(remark);
        versionDO.setDeleted(DeletedEnum.UNDELETED.getCode());
        versionDO.setCreateTime(new Date());
        versionMapper.insert(versionDO);
        return versionDO;
    }

    public CfgVersionDO getLastByType(byte type, String identityCode) {
        LambdaQueryWrapper<CfgVersionDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgVersionDO::getType, type)
                .eq(StringUtils.hasText(identityCode), CfgVersionDO::getIdentityCode, identityCode)
                .isNull(!StringUtils.hasText(identityCode), CfgVersionDO::getIdentityCode)
                .eq(CfgVersionDO::getDeleted, DeletedEnum.UNDELETED.getCode())
                .orderByDesc(CfgVersionDO::getCreateTime)
                .last("limit 1");

        return versionMapper.selectOne(wrapper);
    }
}
