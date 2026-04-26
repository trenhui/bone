package com.bone.lowcode.infra.application.convert;

import com.bone.lowcode.infra.application.vo.bizIdentity.GetAllBizIdentityVo;
import com.bone.lowcode.infra.application.vo.page.pageJson.BizIdentity;
import com.bone.lowcode.infra.domain.valueobject.IdentityTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.SysBizIdentityDO;

public class BizIdentityConvert {

    public static GetAllBizIdentityVo bizIdentityDoToVo(SysBizIdentityDO identityDO) {
        GetAllBizIdentityVo vo = new GetAllBizIdentityVo();
        vo.setId(identityDO.getId().toString());
        vo.setCode(identityDO.getCode());
        if (identityDO.getBizType() == IdentityTypeEnum.POLICY.getCode()) {
            vo.setName(identityDO.getParentName() + "(" + identityDO.getName() + ")");
        } else {
            vo.setName(identityDO.getName());
        }
        vo.setType(identityDO.getBizType());
        vo.setStatus(identityDO.getStatus());
        vo.setUpdateTime(identityDO.getCreateTime());
        return vo;
    }

    public static BizIdentity doToBizIdentity(SysBizIdentityDO identityDO) {
        BizIdentity bizIdentity = new BizIdentity();
        bizIdentity.setId(identityDO.getId().toString());
        bizIdentity.setCode(identityDO.getCode());
        bizIdentity.setBizType(identityDO.getBizType().toString());
        if (identityDO.getBizType() == IdentityTypeEnum.POLICY.getCode()) {
            bizIdentity.setName(identityDO.getParentName() + "(" + identityDO.getName() + ")");
        } else {
            bizIdentity.setName(identityDO.getName());
        }
        return bizIdentity;
    }
}
