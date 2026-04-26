package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.PageTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.UploadPictureDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.UploadPictureMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UploadPictureService {

    @Autowired
    private UploadPictureMapper uploadPictureMapper;

    public UploadPictureDO getDOById(Long id) {
        return uploadPictureMapper.selectById(id);
    }

    public UploadPictureDO getDOByCode(String code, String bizIdentityCode) {
        LambdaQueryWrapper<UploadPictureDO> wrapper = new LambdaQueryWrapper<UploadPictureDO>()
                .eq(UploadPictureDO::getCode, code)
                .eq(UploadPictureDO::getDeleted, DeletedEnum.UNDELETED.getCode());

        if (StringUtils.hasText(bizIdentityCode)) {
            wrapper.eq(UploadPictureDO::getType, PageTypeEnum.BIZ_IDENTITY.getCode())
                    .eq(UploadPictureDO::getBizIdentityCode, bizIdentityCode);
        } else {
            wrapper.eq(UploadPictureDO::getType, PageTypeEnum.TEMPLATE.getCode());
        }
        return uploadPictureMapper.selectOne(wrapper);
    }

    public boolean updateById(UploadPictureDO uploadPictureDO) {
        return uploadPictureMapper.updateById(uploadPictureDO) > 0;
    }
}
