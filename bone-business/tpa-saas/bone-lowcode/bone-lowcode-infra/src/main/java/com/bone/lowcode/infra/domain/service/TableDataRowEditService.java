package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.StatusEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDataRowEditDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.TableDataRowEditMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;


@Service
public class TableDataRowEditService {

    @Autowired
    private TableDataRowEditMapper tableDataRowEditMapper;


    public boolean add(CfgTableDataRowEditDO ruleDO) {
        return tableDataRowEditMapper.insert(ruleDO) > 0;
    }

    public List<CfgTableDataRowEditDO> getDOListByTableId(Long tableId) {
        return tableDataRowEditMapper.selectList(
                new LambdaQueryWrapper<CfgTableDataRowEditDO>()
                        .eq(CfgTableDataRowEditDO::getTableId, tableId)
                        .eq(CfgTableDataRowEditDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public boolean updateById(CfgTableDataRowEditDO ruleDO) {
        return tableDataRowEditMapper.updateById(ruleDO) > 0;
    }

    public void batchUpdateById(List<CfgTableDataRowEditDO> tableRowEditDOList) {
        tableDataRowEditMapper.updateById(tableRowEditDOList);
    }

    public boolean deleteById(Long id) {
        return tableDataRowEditMapper.deleteById(id) > 0;
    }

    public List<CfgTableDataRowEditDO> getDOListByPageId(Long pageId) {
        return tableDataRowEditMapper.selectList(
                new LambdaQueryWrapper<CfgTableDataRowEditDO>()
                        .eq(CfgTableDataRowEditDO::getPageId, pageId)
                        .eq(CfgTableDataRowEditDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgTableDataRowEditDO> getEnableByPageId(Long pageId) {
        LambdaQueryWrapper<CfgTableDataRowEditDO> wrapper = new LambdaQueryWrapper<CfgTableDataRowEditDO>()
                .eq(CfgTableDataRowEditDO::getPageId, pageId)
                .eq(CfgTableDataRowEditDO::getStatus, StatusEnum.YES.getCode())
                .eq(CfgTableDataRowEditDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return tableDataRowEditMapper.selectList(wrapper);
    }

    public List<CfgTableDataRowEditDO> getByPageIds(Collection<Long> pageIds) {
        LambdaQueryWrapper<CfgTableDataRowEditDO> wrapper = new LambdaQueryWrapper<CfgTableDataRowEditDO>()
                .in(CfgTableDataRowEditDO::getPageId, pageIds)
                .eq(CfgTableDataRowEditDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return tableDataRowEditMapper.selectList(wrapper);
    }

    public void batchSave(List<CfgTableDataRowEditDO> tableDataRowEditDOList) {
        tableDataRowEditMapper.insert(tableDataRowEditDOList);
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = tableDataRowEditMapper.deleteByIds(idList);
        return i > 0;
    }

    //========================================以下是手写sql的方法==============================================
    public Integer getMaxSequenceByTableId(Long tableId) {
        return tableDataRowEditMapper.getMaxSequenceByTableId(tableId);
    }

    public Integer getMaxSequenceByPageId(Long pageId) {
        return tableDataRowEditMapper.getMaxSequenceByPageId(pageId);
    }
}
