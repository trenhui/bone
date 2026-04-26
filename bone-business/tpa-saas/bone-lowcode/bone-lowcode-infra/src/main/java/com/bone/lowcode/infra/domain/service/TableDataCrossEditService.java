package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.StatusEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDataCrossEditDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.TableDataCrossEditMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class TableDataCrossEditService {

    @Autowired
    private TableDataCrossEditMapper crossTableDataEditMapper;


    public boolean create(CfgTableDataCrossEditDO detailDO) {
        return crossTableDataEditMapper.insert(detailDO) > 0;
    }

    public List<CfgTableDataCrossEditDO> getDOListByPageId(Long pageId) {
        LambdaQueryWrapper<CfgTableDataCrossEditDO> wrapper = new LambdaQueryWrapper<CfgTableDataCrossEditDO>()
                .eq(CfgTableDataCrossEditDO::getPageId, pageId)
                .eq(CfgTableDataCrossEditDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return crossTableDataEditMapper.selectList(wrapper);
    }

    public List<CfgTableDataCrossEditDO> getEnableByPageId(Long pageId) {
        LambdaQueryWrapper<CfgTableDataCrossEditDO> wrapper = new LambdaQueryWrapper<CfgTableDataCrossEditDO>()
                .eq(CfgTableDataCrossEditDO::getPageId, pageId)
                .eq(CfgTableDataCrossEditDO::getStatus, StatusEnum.YES.getCode())
                .eq(CfgTableDataCrossEditDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return crossTableDataEditMapper.selectList(wrapper);
    }

    public List<CfgTableDataCrossEditDO> getDOListByRelationId(Long relationId) {
        return crossTableDataEditMapper.selectList(new LambdaQueryWrapper<CfgTableDataCrossEditDO>()
                .eq(CfgTableDataCrossEditDO::getRelationId, relationId)
                .eq(CfgTableDataCrossEditDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgTableDataCrossEditDO> getByRelationIds(Collection<Long> relationIds) {
        LambdaQueryWrapper<CfgTableDataCrossEditDO> wrapper = new LambdaQueryWrapper<CfgTableDataCrossEditDO>()
                .eq(CfgTableDataCrossEditDO::getRelationId, relationIds)
                .eq(CfgTableDataCrossEditDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return crossTableDataEditMapper.selectList(wrapper);
    }

    public boolean updateById(CfgTableDataCrossEditDO detailDO) {
        return crossTableDataEditMapper.updateById(detailDO) > 0;
    }

    public void batchUpdateById(List<CfgTableDataCrossEditDO> tableCrossEditDOList) {
        crossTableDataEditMapper.updateById(tableCrossEditDOList);
    }

    public boolean deleteById(Long id) {
        return crossTableDataEditMapper.deleteById(id) > 0;
    }

    public void batchSave(List<CfgTableDataCrossEditDO> crossTableDataEditDOList) {
        crossTableDataEditMapper.insert(crossTableDataEditDOList);
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = crossTableDataEditMapper.deleteByIds(idList);
        return i > 0;
    }

    //========================================以下是手写sql的方法==============================================
    public Integer getMaxSequenceByRelationId(Long relationId) {
        return crossTableDataEditMapper.getMaxSequenceByRelationId(relationId);
    }

    public Integer getMaxSequenceByPageId(Long pageId) {
        return crossTableDataEditMapper.getMaxSequenceByPageId(pageId);
    }
}
