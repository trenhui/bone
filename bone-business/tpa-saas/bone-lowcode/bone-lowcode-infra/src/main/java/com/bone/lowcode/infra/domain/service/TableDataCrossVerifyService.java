package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.StatusEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDataCrossVerifyDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.TableDataCrossVerifyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class TableDataCrossVerifyService {

    @Autowired
    private TableDataCrossVerifyMapper crossTableDataVerifyMapper;


    public boolean create(CfgTableDataCrossVerifyDO verifyDO) {
        return crossTableDataVerifyMapper.insert(verifyDO) > 0;
    }

    public List<CfgTableDataCrossVerifyDO> getDOListByPageId(Long pageId) {
        LambdaQueryWrapper<CfgTableDataCrossVerifyDO> wrapper = new LambdaQueryWrapper<CfgTableDataCrossVerifyDO>()
                .eq(CfgTableDataCrossVerifyDO::getPageId, pageId)
                .eq(CfgTableDataCrossVerifyDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return crossTableDataVerifyMapper.selectList(wrapper);
    }

    public List<CfgTableDataCrossVerifyDO> getEnableByPageId(Long pageId) {
        LambdaQueryWrapper<CfgTableDataCrossVerifyDO> wrapper = new LambdaQueryWrapper<CfgTableDataCrossVerifyDO>()
                .eq(CfgTableDataCrossVerifyDO::getPageId, pageId)
                .eq(CfgTableDataCrossVerifyDO::getStatus, StatusEnum.YES.getCode())
                .eq(CfgTableDataCrossVerifyDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return crossTableDataVerifyMapper.selectList(wrapper);
    }

    public List<CfgTableDataCrossVerifyDO> getDOListByRelationId(Long relationId) {
        return crossTableDataVerifyMapper.selectList(new LambdaQueryWrapper<CfgTableDataCrossVerifyDO>()
                .eq(CfgTableDataCrossVerifyDO::getRelationId, relationId)
                .eq(CfgTableDataCrossVerifyDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgTableDataCrossVerifyDO> getByRelationIds(Collection<Long> relationIds) {
        LambdaQueryWrapper<CfgTableDataCrossVerifyDO> wrapper = new LambdaQueryWrapper<CfgTableDataCrossVerifyDO>()
                .in(CfgTableDataCrossVerifyDO::getRelationId, relationIds)
                .eq(CfgTableDataCrossVerifyDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return crossTableDataVerifyMapper.selectList(wrapper);
    }

    public boolean updateById(CfgTableDataCrossVerifyDO verifyDO) {
        return crossTableDataVerifyMapper.updateById(verifyDO) > 0;
    }

    public boolean deleteCrossTableDataVerifyRule(Long id) {
        return crossTableDataVerifyMapper.deleteById(id) > 0;
    }

    public void batchSave(List<CfgTableDataCrossVerifyDO> crossTableDataVerifyDOList) {
        crossTableDataVerifyMapper.insert(crossTableDataVerifyDOList);
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = crossTableDataVerifyMapper.deleteByIds(idList);
        return i > 0;
    }

    //========================================以下是手写sql的方法==============================================
    public Integer getMaxSequenceByRelationId(Long relationId) {
        return crossTableDataVerifyMapper.getMaxSequenceByRelationId(relationId);
    }
}
