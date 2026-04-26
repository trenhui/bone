package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.PageTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.UploadDataDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.UploadDataMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

@Service
public class UploadDataService {

    @Autowired
    private UploadDataMapper uploadDataMapper;

    public boolean updateAcceptNull(UploadDataDO item) {
        LambdaUpdateWrapper<UploadDataDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UploadDataDO::getId, item.getId())
                .set(StringUtils.hasText(item.getTitle()), UploadDataDO::getTitle, item.getTitle())
                .set(UploadDataDO::getFieldIdList, item.getFieldIdList()) //允许更新为null
                .set(UploadDataDO::getSingleFieldRule, item.getSingleFieldRule()) //允许更新为null
                .set(UploadDataDO::getGroupFieldRule, item.getGroupFieldRule()) //允许更新为null
                .set(StringUtils.hasText(item.getTemplateFileName()), UploadDataDO::getTemplateFileName, item.getTemplateFileName())
                .set(item.getFileMaxSize() != null, UploadDataDO::getFileMaxSize, item.getFileMaxSize())
                .set(item.getFileMaxCount() != null, UploadDataDO::getFileMaxCount, item.getFileMaxCount())
                .set(StringUtils.hasText(item.getFileFormat()), UploadDataDO::getFileFormat, item.getFileFormat())
                .set(item.getHeaderCheckMode() != null, UploadDataDO::getHeaderCheckMode, item.getHeaderCheckMode())
                .set(StringUtils.hasText(item.getImportTypeList()), UploadDataDO::getImportTypeList, item.getImportTypeList())
                .set(item.getCheckType() != null, UploadDataDO::getCheckType, item.getCheckType())
                .set(StringUtils.hasText(item.getImportDescription()), UploadDataDO::getImportDescription, item.getImportDescription())
                .set(UploadDataDO::getUpdateTime, new Date())
        ;
        return uploadDataMapper.update(wrapper) > 0;
    }


    public UploadDataDO getDOByCode(String code, String bizIdentityCode) {
        LambdaQueryWrapper<UploadDataDO> wrapper = new LambdaQueryWrapper<UploadDataDO>()
                .eq(UploadDataDO::getCode, code)
                .eq(UploadDataDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        if (StringUtils.hasText(bizIdentityCode)) {
            wrapper.eq(UploadDataDO::getType, PageTypeEnum.BIZ_IDENTITY.getCode())
                    .eq(UploadDataDO::getBizIdentityCode, bizIdentityCode);
        } else {
            wrapper.eq(UploadDataDO::getType, PageTypeEnum.TEMPLATE.getCode());
        }
        return uploadDataMapper.selectOne(wrapper);
    }

    public List<UploadDataDO> getDOByCodeList(List<String> codeList, String bizIdentityCode) {
        LambdaQueryWrapper<UploadDataDO> wrapper = new LambdaQueryWrapper<UploadDataDO>()
                .in(UploadDataDO::getCode, codeList)
                .eq(UploadDataDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        if (StringUtils.hasText(bizIdentityCode)) {
            wrapper.eq(UploadDataDO::getType, PageTypeEnum.BIZ_IDENTITY.getCode())
                    .eq(UploadDataDO::getBizIdentityCode, bizIdentityCode);
        } else {
            wrapper.eq(UploadDataDO::getType, PageTypeEnum.TEMPLATE.getCode());
        }
        return uploadDataMapper.selectList(wrapper);
    }

    public UploadDataDO getDOById(Long id) {
        return uploadDataMapper.selectById(id);
    }
}
