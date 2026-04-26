package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.PageTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.UploadImageDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.UploadImageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;

@Service
public class UploadImageService {

    @Autowired
    private UploadImageMapper uploadImageMapper;


    public UploadImageDO getDOByCode(String code, String bizIdentityCode) {
        LambdaQueryWrapper<UploadImageDO> wrapper = new LambdaQueryWrapper<UploadImageDO>()
                .eq(UploadImageDO::getCode, code)
                .eq(UploadImageDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        if (StringUtils.hasText(bizIdentityCode)) {
            wrapper.eq(UploadImageDO::getType, PageTypeEnum.BIZ_IDENTITY.getCode())
                    .eq(UploadImageDO::getBizIdentityCode, bizIdentityCode);
        } else {
            wrapper.eq(UploadImageDO::getType, PageTypeEnum.TEMPLATE.getCode());
        }
        return uploadImageMapper.selectOne(wrapper);
    }

    public UploadImageDO getDOById(Long id) {
        return uploadImageMapper.selectOne(new LambdaQueryWrapper<UploadImageDO>()
                .eq(UploadImageDO::getId, id));
    }

    public boolean updateAcceptNull(UploadImageDO item) {
        LambdaUpdateWrapper<UploadImageDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UploadImageDO::getId, item.getId())
                .set(UploadImageDO::getTitle, item.getTitle()) //允许更新为null
                .set(UploadImageDO::getPackageDescription, item.getPackageDescription()) //允许更新为null
                .set(item.getFileMaxSize() != null, UploadImageDO::getFileMaxSize, item.getFileMaxSize())
                .set(item.getFolderMaxSize() != null, UploadImageDO::getFolderMaxSize, item.getFolderMaxSize())
                .set(UploadImageDO::getFileFormat, item.getFileFormat()) //允许更新为null
                .set(UploadImageDO::getImportType, item.getImportType()) //允许更新为null
                .set(item.getSameFileHandle() != null, UploadImageDO::getSameFileHandle, item.getSameFileHandle())
                .set(UploadImageDO::getImportDescription, item.getImportDescription()) //允许更新为null
                .set(UploadImageDO::getUpdateTime, new Date())
        ;
        return uploadImageMapper.update(wrapper) > 0;
    }
}
