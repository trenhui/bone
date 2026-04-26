package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFieldsetDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.FieldSetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;


@Service
public class FieldsetService {

    @Autowired
    private FieldSetMapper fieldSetMapper;

    public void batchSave(List<CfgFieldsetDO> fieldsetDOList) {
        fieldSetMapper.insert(fieldsetDOList);
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = fieldSetMapper.deleteByIds(idList);
        return i > 0;
    }

    public boolean updateById(CfgFieldsetDO newFieldsetDO) {
        return fieldSetMapper.updateById(newFieldsetDO) == 1;
    }

    public CfgFieldsetDO getDOById(Long id) {
        return fieldSetMapper.selectById(id);
    }

    public Integer batchSaveWithId(List<CfgFieldsetDO> fieldsetDOList) {
        Integer count = fieldSetMapper.batchSaveWithId(fieldsetDOList);
        return count;
    }

    public List<CfgFieldsetDO> getDOByPageId(Long pageId) {
        return fieldSetMapper.selectList(new LambdaQueryWrapper<CfgFieldsetDO>()
                .eq(CfgFieldsetDO::getPageId, pageId)
                .eq(CfgFieldsetDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgFieldsetDO> getDOByPageIds(Collection<Long> pageIds) {
        return fieldSetMapper.selectList(new LambdaQueryWrapper<CfgFieldsetDO>()
                .in(CfgFieldsetDO::getPageId, pageIds)
                .eq(CfgFieldsetDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgFieldsetDO> getDOByBlockId(Long blockId) {
        LambdaQueryWrapper<CfgFieldsetDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgFieldsetDO::getBlockId, blockId)
                .eq(CfgFieldsetDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return fieldSetMapper.selectList(wrapper);
    }
}
