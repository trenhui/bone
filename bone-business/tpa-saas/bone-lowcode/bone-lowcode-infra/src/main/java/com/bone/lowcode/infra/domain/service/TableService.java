package com.bone.lowcode.infra.domain.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDO;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.TableMapper;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;


@Service
public class TableService {

    @Autowired
    private TableMapper tableMapper;


    public CfgTableDO getDOById(Long id) {
        return tableMapper.selectOne(new LambdaQueryWrapper<CfgTableDO>()
                .eq(CfgTableDO::getId, id)
                .eq(CfgTableDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public boolean updateById(CfgTableDO tableDO) {
        return tableMapper.updateById(tableDO) > 0;
    }


    public boolean batchSave(List<CfgTableDO> tableDOList) {
        List<BatchResult> re = tableMapper.insert(tableDOList);
        return true;
    }

    public List<CfgTableDO> getDOListByPageId(Long pageId) {
        return tableMapper.selectList(new LambdaQueryWrapper<CfgTableDO>()
                .eq(CfgTableDO::getPageId, pageId)
                .eq(CfgTableDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgTableDO> getByPageIds(Collection<Long> pageIds) {
        return tableMapper.selectList(new LambdaQueryWrapper<CfgTableDO>()
                .in(CfgTableDO::getPageId, pageIds)
                .eq(CfgTableDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public boolean deleteByIdList(List<Long> idList) {
        int i = tableMapper.deleteByIds(idList);
        return i > 0;
    }

    public boolean updateAcceptNull(CfgTableDO tableDO) {
        LambdaUpdateWrapper<CfgTableDO> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(CfgTableDO::getId, tableDO.getId())
                .set(StringUtils.hasText(tableDO.getName()), CfgTableDO::getName, tableDO.getName())
                .set(StringUtils.hasText(tableDO.getPrompt()), CfgTableDO::getPrompt, tableDO.getPrompt())
                .set(StringUtils.hasText(tableDO.getEmptyPrompt()), CfgTableDO::getEmptyPrompt, tableDO.getEmptyPrompt())
                .set(CfgTableDO::getOrderByList, tableDO.getOrderByList()) //允许更新为null
                .set(tableDO.getEnableOrderColumn() != null, CfgTableDO::getEnableOrderColumn, tableDO.getEnableOrderColumn())
                .set(CfgTableDO::getEditableColumnList, tableDO.getEditableColumnList()) //允许更新为null
                .set(tableDO.getLeftFixed() != null, CfgTableDO::getLeftFixed, tableDO.getLeftFixed())
                .set(tableDO.getRightFixed() != null, CfgTableDO::getRightFixed, tableDO.getRightFixed())
                .set(tableDO.getFillScreen() != null, CfgTableDO::getFillScreen, tableDO.getFillScreen())
                .set(tableDO.getOperationColumnEnabled() != null, CfgTableDO::getOperationColumnEnabled, tableDO.getOperationColumnEnabled())
                .set(tableDO.getOperationColumnFixed() != null, CfgTableDO::getOperationColumnFixed, tableDO.getOperationColumnFixed())
                .set(StringUtils.hasText(tableDO.getDisplay()), CfgTableDO::getDisplay, tableDO.getDisplay())
                .set(tableDO.getRequiredData() != null, CfgTableDO::getRequiredData, tableDO.getRequiredData())
                .set(tableDO.getPaginationEnabled() != null, CfgTableDO::getPaginationEnabled, tableDO.getPaginationEnabled())
                .set(tableDO.getDefaultPageSize() != null, CfgTableDO::getDefaultPageSize, tableDO.getDefaultPageSize())
                .set(tableDO.getDisplayTotalPage() != null, CfgTableDO::getDisplayTotalPage, tableDO.getDisplayTotalPage())
                .set(tableDO.getDisplayTotalSize() != null, CfgTableDO::getDisplayTotalSize, tableDO.getDisplayTotalSize())
                .set(tableDO.getDeleted() != null, CfgTableDO::getDeleted, tableDO.getDeleted())
                .set(tableDO.getCreateBy() != null, CfgTableDO::getCreateBy, tableDO.getCreateBy())
                .set(tableDO.getCreateTime() != null, CfgTableDO::getCreateTime, tableDO.getCreateTime())
                .set(tableDO.getUpdateBy() != null, CfgTableDO::getUpdateBy, tableDO.getUpdateBy())
                .set(tableDO.getUpdateTime() != null, CfgTableDO::getUpdateTime, tableDO.getUpdateTime())
                .set(tableDO.getParentTableId() != null, CfgTableDO::getParentTableId, tableDO.getParentTableId())
                .set(StringUtils.hasText(tableDO.getInitApiParam()), CfgTableDO::getInitApiParam, tableDO.getInitApiParam())
                .set(StringUtils.hasText(tableDO.getInitApi()), CfgTableDO::getInitApi, tableDO.getInitApi())
                .set(StringUtils.hasText(tableDO.getSubmitApi()), CfgTableDO::getSubmitApi, tableDO.getSubmitApi())
                .set(StringUtils.hasText(tableDO.getDeleteApi()), CfgTableDO::getDeleteApi, tableDO.getDeleteApi())
                .set(tableDO.getEnableDataSummary() != null, CfgTableDO::getEnableDataSummary, tableDO.getEnableDataSummary())
                .set(StringUtils.hasText(tableDO.getDataSummaryRule()), CfgTableDO::getDataSummaryRule, tableDO.getDataSummaryRule())
                .set(tableDO.getEnableDataAggregate() != null, CfgTableDO::getEnableDataAggregate, tableDO.getEnableDataAggregate())
                .set(tableDO.getEnableSearch() != null, CfgTableDO::getEnableSearch, tableDO.getEnableSearch())
                .set(StringUtils.hasText(tableDO.getSearchFieldList()), CfgTableDO::getSearchFieldList, tableDO.getSearchFieldList())
        ;
        return tableMapper.update(wrapper) > 0;
    }

    public List<CfgTableDO> getDOByBlockId(Long blockId) {
        return tableMapper.selectList(new LambdaQueryWrapper<CfgTableDO>()
                .eq(CfgTableDO::getBlockId, blockId)
                .eq(CfgTableDO::getDeleted, DeletedEnum.UNDELETED.getCode()));
    }

    public List<CfgTableDO> getByIds(Collection<Long> ids) {
        LambdaQueryWrapper<CfgTableDO> wrapper = new LambdaQueryWrapper<CfgTableDO>()
                .in(CfgTableDO::getId, ids)
                .eq(CfgTableDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return tableMapper.selectList(wrapper);
    }

    public CfgTableDO getDOByNameAndPageId(String tableName, Long pageId) {
        LambdaQueryWrapper<CfgTableDO> wrapper = new LambdaQueryWrapper<CfgTableDO>()
                .eq(CfgTableDO::getName, tableName)
                .eq(CfgTableDO::getPageId, pageId)
                .eq(CfgTableDO::getDeleted, DeletedEnum.UNDELETED.getCode());
        return tableMapper.selectOne(wrapper);
    }

    public void batchUpdateById(List<CfgTableDO> tableList) {
        tableMapper.updateById(tableList);
    }
}
