package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDataGroupAggregateDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.TableDataGroupAggregateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class TableDataGroupAggregateService {

    @Autowired
    private TableDataGroupAggregateMapper tableDataGroupAggregateMapper;


    public boolean add(CfgTableDataGroupAggregateDO tableDataGroupDO) {
        return tableDataGroupAggregateMapper.insert(tableDataGroupDO) > 0;
    }

    public List<CfgTableDataGroupAggregateDO> getDOByTableId(Long tableId) {
        List<CfgTableDataGroupAggregateDO> list = tableDataGroupAggregateMapper.selectList(
                new LambdaQueryWrapper<CfgTableDataGroupAggregateDO>()
                        .eq(CfgTableDataGroupAggregateDO::getTableId, tableId)
                        .eq(CfgTableDataGroupAggregateDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
        return list;
    }

    public boolean updateById(CfgTableDataGroupAggregateDO tableDataGroupDO) {
        return tableDataGroupAggregateMapper.updateById(tableDataGroupDO) > 0;
    }

    public boolean deleteById(Long id) {
        return tableDataGroupAggregateMapper.deleteById(id) > 0;
    }

    public List<CfgTableDataGroupAggregateDO> getDOListByPageId(Long pageId) {
        return tableDataGroupAggregateMapper.selectList(
                new LambdaQueryWrapper<CfgTableDataGroupAggregateDO>()
                        .eq(CfgTableDataGroupAggregateDO::getPageId, pageId)
                        .eq(CfgTableDataGroupAggregateDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgTableDataGroupAggregateDO> getByPageIds(Collection<Long> pageIds) {
        LambdaQueryWrapper<CfgTableDataGroupAggregateDO> wrapper = new LambdaQueryWrapper<CfgTableDataGroupAggregateDO>()
                .in(CfgTableDataGroupAggregateDO::getPageId, pageIds)
                .eq(CfgTableDataGroupAggregateDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return tableDataGroupAggregateMapper.selectList(wrapper);
    }

    public void batchSave(List<CfgTableDataGroupAggregateDO> tableDataGroupAggregateDOList) {
        tableDataGroupAggregateMapper.insert(tableDataGroupAggregateDOList);
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = tableDataGroupAggregateMapper.deleteByIds(idList);
        return i > 0;
    }
}
