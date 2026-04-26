package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.StatusEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDataRowVerifyDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.TableDataRowVerifyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class TableDataRowVerifyService {

    @Autowired
    private TableDataRowVerifyMapper tableDataVerifyMapper;


    public boolean create(CfgTableDataRowVerifyDO verifyDO) {
        return tableDataVerifyMapper.insert(verifyDO) > 0;
    }

    public boolean updateById(CfgTableDataRowVerifyDO verifyDO) {
        return tableDataVerifyMapper.updateById(verifyDO) > 0;
    }

    public List<CfgTableDataRowVerifyDO> getDOListByTableId(Long tableId) {
        return tableDataVerifyMapper.selectList(new LambdaQueryWrapper<CfgTableDataRowVerifyDO>()
                .eq(CfgTableDataRowVerifyDO::getTableId, tableId)
                .eq(CfgTableDataRowVerifyDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public boolean deleteById(Long id) {
        return tableDataVerifyMapper.deleteById(id) > 0;
    }

    public List<CfgTableDataRowVerifyDO> getDOListByPageId(Long pageId) {
        return tableDataVerifyMapper.selectList(new LambdaQueryWrapper<CfgTableDataRowVerifyDO>()
                .eq(CfgTableDataRowVerifyDO::getPageId, pageId)
                .eq(CfgTableDataRowVerifyDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgTableDataRowVerifyDO> getEnableByPageId(Long pageId) {
        return tableDataVerifyMapper.selectList(new LambdaQueryWrapper<CfgTableDataRowVerifyDO>()
                .eq(CfgTableDataRowVerifyDO::getPageId, pageId)
                .eq(CfgTableDataRowVerifyDO::getStatus, StatusEnum.YES.getCode())
                .eq(CfgTableDataRowVerifyDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgTableDataRowVerifyDO> getByPageIds(Collection<Long> pageIds) {
        return tableDataVerifyMapper.selectList(new LambdaQueryWrapper<CfgTableDataRowVerifyDO>()
                .in(CfgTableDataRowVerifyDO::getPageId, pageIds)
                .eq(CfgTableDataRowVerifyDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public void batchSave(List<CfgTableDataRowVerifyDO> tableDataRowVerifyDOList) {
        tableDataVerifyMapper.insert(tableDataRowVerifyDOList);
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = tableDataVerifyMapper.deleteByIds(idList);
        return i > 0;
    }

    //========================================以下是手写sql的方法==============================================
    public Integer getMaxSequenceByTableId(Long tableId) {
        return tableDataVerifyMapper.getMaxSequenceByTableId(tableId);
    }
}
