package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDataRelationDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.TableDataRelationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class TableDataRelationService {

    @Autowired
    private TableDataRelationMapper tableDataRelationMapper;


    public List<CfgTableDataRelationDO> getDOByCurrentTableId(Long tableId) {
        List<CfgTableDataRelationDO> relationDOList = tableDataRelationMapper.selectList(new LambdaQueryWrapper<CfgTableDataRelationDO>()
                .eq(CfgTableDataRelationDO::getCurrentTableId, tableId));
        return relationDOList;
    }

    public List<CfgTableDataRelationDO> getDOByTargetTableId(Long tableId) {
        List<CfgTableDataRelationDO> relationDOList = tableDataRelationMapper.selectList(new LambdaQueryWrapper<CfgTableDataRelationDO>()
                .eq(CfgTableDataRelationDO::getTargetTableId, tableId));
        return relationDOList;
    }

    public CfgTableDataRelationDO getDOByTableIds(Long currentTableId, Long targetTableId) {
        return tableDataRelationMapper.selectOne(new LambdaQueryWrapper<CfgTableDataRelationDO>()
                .eq(CfgTableDataRelationDO::getCurrentTableId, currentTableId)
                .eq(CfgTableDataRelationDO::getTargetTableId, targetTableId));
    }

    public List<CfgTableDataRelationDO> getDOListByPageId(Long pageId) {
        return tableDataRelationMapper.selectList(new LambdaQueryWrapper<CfgTableDataRelationDO>()
                .eq(CfgTableDataRelationDO::getPageId, pageId));
    }

    public List<CfgTableDataRelationDO> getByPageIds(Collection<Long> pageIds) {
        LambdaQueryWrapper<CfgTableDataRelationDO> wrapper = new LambdaQueryWrapper<CfgTableDataRelationDO>()
                .in(CfgTableDataRelationDO::getPageId, pageIds);
        return tableDataRelationMapper.selectList(wrapper);
    }

    public void batchSave(List<CfgTableDataRelationDO> tableDataRelationDOList) {
        tableDataRelationMapper.insert(tableDataRelationDOList);
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = tableDataRelationMapper.deleteByIds(idList);
        return i > 0;
    }
}
