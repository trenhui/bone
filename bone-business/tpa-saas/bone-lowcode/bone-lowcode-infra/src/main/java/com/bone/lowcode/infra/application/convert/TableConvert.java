package com.bone.lowcode.infra.application.convert;

import com.bone.lowcode.infra.application.dto.table.UpdateTableDTO;
import com.bone.lowcode.infra.application.vo.page.pageJson.Table;
import com.bone.lowcode.infra.domain.model.EditableColumn;
import com.bone.lowcode.infra.domain.valueobject.PageItemTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgTableDO;
import org.springframework.beans.BeanUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

public class TableConvert {
    public static Table tableDOToTable(CfgTableDO tableDO) {
        Table table = new Table();
        BeanUtils.copyProperties(tableDO, table);
        table.setId(tableDO.getId().toString());
        table.setType(PageItemTypeEnum.TABLE.getName());
        table.setName(tableDO.getName());

        table.setPrompt(tableDO.getPrompt());
        table.setEmptyPrompt(tableDO.getEmptyPrompt());

        table.setFieldSortTypeList(tableDO.getSortTypeList());
        table.setEnableOrderColumn(tableDO.getEnableOrderColumn());
        table.setOperationColumnEnabled(tableDO.getOperationColumnEnabled());
        table.setOperationColumnFixed(tableDO.getOperationColumnFixed());
        table.setPaginationEnabled(tableDO.getPaginationEnabled());
        table.setDefaultPageSize(tableDO.getDefaultPageSize());
        table.setDisplayTotalSize(tableDO.getDisplayTotalSize());

        if (tableDO.getParentTableId() != null) {
            table.setParentTableId(tableDO.getParentTableId().toString());
        }
        if (StringUtils.hasText(tableDO.getInitApiParam())) {
            table.setInitApiParam(tableDO.getInitApiParam());
        }
        if (StringUtils.hasText(tableDO.getInitApi())) {
            table.setInitApi(tableDO.getInitApi());
        }
        if (StringUtils.hasText(tableDO.getSubmitApi())) {
            table.setSubmitApi(tableDO.getSubmitApi());
        }
        if (StringUtils.hasText(tableDO.getDeleteApi())) {
            table.setDeleteApi(tableDO.getDeleteApi());
        }
        table.setEnableDataSummary(tableDO.getEnableDataSummary());
        table.setEnableSearch(tableDO.getEnableSearch());
        return table;
    }


    public static CfgTableDO updateTableDTOToDo(UpdateTableDTO dto) {
        CfgTableDO tableDO = new CfgTableDO();
        BeanUtils.copyProperties(dto, tableDO);
        tableDO.setId(dto.getId());
        tableDO.setPrompt(dto.getPrompt());
        tableDO.setEmptyPrompt(dto.getEmptyPrompt());
        tableDO.setSortTypeList(dto.getFieldSortTypeList());
        tableDO.setEnableOrderColumn(dto.getEnableOrderColumn());

        //设置可编辑列列表
        if (!CollectionUtils.isEmpty(dto.getFieldOfTableChangeList())) {
            List<EditableColumn> list = dto.getFieldOfTableChangeList().stream()
                    .filter(i -> i.getFieldId() != null &&
                            ((i.getSingleLineEditable() != null && i.getSingleLineEditable() == 1) ||
                                    (i.getBatchEditable() != null && i.getBatchEditable() == 1)))
                    .map(field -> new EditableColumn(field.getFieldId().toString(), field.getSingleLineEditable(), field.getBatchEditable()))
                    .toList();
            tableDO.setEditableColumns(list);
        }

        tableDO.setOperationColumnEnabled(dto.getOperationColumnEnabled());
        tableDO.setOperationColumnFixed(dto.getOperationColumnFixed());
        tableDO.setPaginationEnabled(dto.getPaginationOpened());
        tableDO.setDefaultPageSize(dto.getDefaultPageSize());
        tableDO.setDisplayTotalPage(dto.getDisplayTotalPage());
        tableDO.setDisplayTotalSize(dto.getDisplayTotalSize());
        tableDO.setEnableDataSummary(dto.getEnableDataSummary());
        if (dto.getDataSummaryRuleList() != null) {
            tableDO.setDataSummaryRules(dto.getDataSummaryRuleList());
        }
        tableDO.setEnableDataAggregate(dto.getEnableDataAggregate());
        return tableDO;
    }

    public static com.bone.lowcode.infra.application.vo.simple.Table tableDOToSimpleTable(CfgTableDO tableDO) {
        com.bone.lowcode.infra.application.vo.simple.Table table = new com.bone.lowcode.infra.application.vo.simple.Table();
        table.setId(tableDO.getId().toString());
        table.setName(tableDO.getName());
        return table;
    }
}
