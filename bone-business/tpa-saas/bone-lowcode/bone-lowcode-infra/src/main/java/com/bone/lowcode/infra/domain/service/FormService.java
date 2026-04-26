package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgFormDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.FormMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;


@Service
public class FormService {

    @Autowired
    private FormMapper formMapper;


    public CfgFormDO getDOByPageId(Long pageId) {
        LambdaQueryWrapper<CfgFormDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CfgFormDO::getPageId, pageId)
                .eq(CfgFormDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return formMapper.selectOne(wrapper);
    }

    public List<CfgFormDO> getDOByPageIds(Collection<Long> pageIds) {
        LambdaQueryWrapper<CfgFormDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CfgFormDO::getPageId, pageIds)
                .eq(CfgFormDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return formMapper.selectList(wrapper);
    }

    public boolean addForm(CfgFormDO formDO) {
        return formMapper.insert(formDO) > 0;
    }

    public boolean batchSave(List<CfgFormDO> formDOList) {
        formMapper.insert(formDOList);
        return true;
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = formMapper.deleteByIds(idList);
        return i > 0;
    }

    public boolean deleteById(Long formId) {
        return formMapper.deleteById(formId) > 0;
    }

    public CfgFormDO getDOById(Long id) {
        return formMapper.selectById(id);
    }

    public List<CfgFormDO> getByIds(Collection<Long> ids) {
        LambdaQueryWrapper<CfgFormDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(CfgFormDO::getId, ids)
                .eq(CfgFormDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return formMapper.selectList(wrapper);
    }
}
