package com.bone.lowcode.infra.application.service;

import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.application.convert.TableRuleConvert;
import com.bone.lowcode.infra.application.dto.tableRule.*;
import com.bone.lowcode.infra.application.vo.table.FieldSimpleInfo;
import com.bone.lowcode.infra.application.vo.table.RowRuleField;
import com.bone.lowcode.infra.application.vo.tableRule.*;
import com.bone.lowcode.infra.domain.service.*;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.StatusEnum;
import com.bone.lowcode.infra.domain.valueobject.ValueTypeEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;


@Service
public class TableRuleApplicationService {

    @Autowired
    private TableService tableService;
    @Autowired
    private FieldService fieldService;
    @Autowired
    private TableDataRelationService tableDataRelationService;
    @Autowired
    private TableDataCrossEditService crossTableEditService;
    @Autowired
    private TableDataRowVerifyService tableDataRowVerifyService;
    @Autowired
    private TableDataRowEditService tableRowEditService;
    @Autowired
    private TableDataCrossVerifyService crossTableDataVerifyService;
    @Autowired
    private PageService pageService;


    public boolean createTableRowEditRule(CreateTableRowEditRuleDTO param) {
        CfgTableDataRowEditDO ruleDO = TableRuleConvert.createTableRowEditRuleDTOToDO(param);
        Long tableId = param.getTableId();
        CfgTableDO tableDO = tableService.getDOById(tableId);
        if (tableDO == null) {
            throw new ServiceException(500, "目标表格不存在");
        }

        Long pageId = tableDO.getPageId();
        ruleDO.setPageId(pageId);
        int sequence = getTableEditRuleSequence(pageId);
        ruleDO.setSequence(sequence);
        boolean flag = tableRowEditService.add(ruleDO);
        return flag;
    }

    private int getTableEditRuleSequence(Long pageId) {
        Integer sequence1 = tableRowEditService.getMaxSequenceByPageId(pageId);
        if (sequence1 == null) {
            sequence1 = 0;
        }
        Integer sequence2 = crossTableEditService.getMaxSequenceByPageId(pageId);
        if (sequence2 == null) {
            sequence2 = 0;
        }
        return Math.max(sequence1, sequence2) + 1;
    }

    public List<TableRowEditRuleVO> getTableRowEditRule(Long tableId) {
        List<CfgTableDataRowEditDO> rowRuleDOList = tableRowEditService.getDOListByTableId(tableId);
        List<TableRowEditRuleVO> res = new ArrayList<>();
        CfgTableDO tableDO = tableService.getDOById(tableId);

        for (CfgTableDataRowEditDO rowRuleDO : rowRuleDOList) {
            TableRowEditRuleVO vo = new TableRowEditRuleVO();
            vo.setId(rowRuleDO.getId().toString());
            vo.setTableId(tableId.toString());

            List<Long> sourceFieldIds = rowRuleDO.getSourceFieldIds();
            List<CfgFieldDO> sourceFieldList = fieldService.getDOListByIdList(sourceFieldIds);
            Map<Long, CfgFieldDO> fieldDOMap = sourceFieldList.stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));
            List<CfgFieldDO> sourceFieldListNew = sourceFieldIds.stream().map(fieldDOMap::get).toList();
            List<RowRuleField> sourceFields = sourceFieldListNew.stream().map(i -> {
                RowRuleField field = new RowRuleField();
                field.setId(i.getId().toString());
                field.setBizName(i.getBizName());
                field.setDataBinding(i.getDataBinding());
                return field;
            }).toList();
            vo.setSourceFieldList(sourceFields);
            vo.setFunctionName(rowRuleDO.getFunctionName());

            Long targetFieldId = rowRuleDO.getTargetFieldId();
            CfgFieldDO fieldDO = fieldService.getDOById(targetFieldId);
            RowRuleField field = new RowRuleField();
            field.setId(fieldDO.getId().toString());
            field.setBizName(fieldDO.getBizName());
            field.setDataBinding(fieldDO.getDataBinding());
            vo.setTargetField(field);

            vo.setErrorPrompt(rowRuleDO.getErrorPrompt());
            vo.setStatus(rowRuleDO.getStatus());
            vo.setVerifyType(rowRuleDO.getVerifyType());
            vo.setSequence(rowRuleDO.getSequence());
            vo.setTableName(tableDO.getName());
            res.add(vo);
        }
        return res;
    }

    public boolean updateRowRule(UpdateRowRuleDTO dto) {
        CfgTableDataRowEditDO ruleDO = TableRuleConvert.updateRowRuleDTOToDO(dto);
        return tableRowEditService.updateById(ruleDO);
    }

    public boolean deleteRowRule(Long id) {
        return tableRowEditService.deleteById(id);
    }

    public List<GetTargetTableVO> getTargetTable(Long tableId) {
        List<CfgTableDataRelationDO> relationDOList = tableDataRelationService.getDOByCurrentTableId(tableId);

        List<GetTargetTableVO> res = new ArrayList<>();
        for (CfgTableDataRelationDO i : relationDOList) {
            CfgTableDO tableDO = tableService.getDOById(i.getTargetTableId());
            GetTargetTableVO vo = new GetTargetTableVO(tableDO.getId().toString(), tableDO.getName());
            res.add(vo);
        }
        return res;
    }

    public boolean createCrossTableDataEditRule(TableDataRelationDTO dto) {
        CfgTableDataRelationDO relationDO = tableDataRelationService.getDOByTableIds(dto.getCurrentTableId(), dto.getTargetTableId());
        if (relationDO == null) {
            throw new ServiceException(500, "请先建立表格关系再创建跨表格规则");
        }

        CfgTableDataCrossEditDO detailDO = new CfgTableDataCrossEditDO();
        detailDO.setPageId(relationDO.getPageId());
        detailDO.setRelationId(relationDO.getId());
        detailDO.setCurrentTableFieldIds(dto.getCurrentTableFieldIdList());
        detailDO.setFunctionName(dto.getFunctionName());
        detailDO.setTargetTableFieldId(dto.getTargetTableFieldId());

        int sequence = getTableEditRuleSequence(relationDO.getPageId());
        detailDO.setSequence(sequence);

        detailDO.setVerifyType(dto.getVerifyType());
        detailDO.setCreateTime(new Date());
        detailDO.setDeleted(DeletedEnum.UNDELETED.getCode());
        boolean flag = crossTableEditService.create(detailDO);
        return flag;
    }

    public List<CrossTableDataEditVO> getCrossTableEditRule(Long tableId, Integer type) {
        List<CfgTableDataRelationDO> relationDOList;
        if (type != null && type == 1) {
            relationDOList = tableDataRelationService.getDOByTargetTableId(tableId); //1->TargetTable
        } else if (type != null && type == 2) {
            relationDOList = tableDataRelationService.getDOByCurrentTableId(tableId); //2->CurrentTable
        } else {
            throw new ServiceException(500, "请传入正确的表格id类型, 1:TargetTableId 2:CurrentTableId");
        }
        if (CollectionUtils.isEmpty(relationDOList)) return new ArrayList<>();

        List<CrossTableDataEditVO> res = getCrossTableEditVOList(relationDOList);
        return res;
    }

    private List<CrossTableDataEditVO> getCrossTableEditVOList(List<CfgTableDataRelationDO> relationDOList) {
        List<Long> fieldIdList = new ArrayList<>();
        List<CfgTableDataCrossEditDO> allTableCrossEditDOList = new ArrayList<>();
        HashSet<Long> tableIds = new HashSet<>();
        for (CfgTableDataRelationDO relationDO : relationDOList) {
            fieldIdList.add(relationDO.getCurrentRelationFieldId());
            fieldIdList.add(relationDO.getTargetRelationFieldId());

            List<CfgTableDataCrossEditDO> tableCrossEditDOList = crossTableEditService.getDOListByRelationId(relationDO.getId());
            allTableCrossEditDOList.addAll(tableCrossEditDOList);
            for (CfgTableDataCrossEditDO tableCrossEditDO : tableCrossEditDOList) {
                fieldIdList.addAll(tableCrossEditDO.getCurrentTableFieldIds());
                fieldIdList.add(tableCrossEditDO.getTargetTableFieldId());
            }

            tableIds.add(relationDO.getCurrentTableId());
            tableIds.add(relationDO.getTargetTableId());
        }

        List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(fieldIdList);
        Map<Long, FieldSimpleInfo> fieldMap = fieldDOList.stream().collect(Collectors.toMap(CfgFieldDO::getId, cfgFieldDO -> {
            FieldSimpleInfo field = new FieldSimpleInfo();
            field.setId(cfgFieldDO.getId().toString());
            field.setBizCode(cfgFieldDO.getBizCode());
            field.setBizName(cfgFieldDO.getBizName());
            field.setDataBinding(cfgFieldDO.getDataBinding());
            return field;
        }));

        Map<Long, String> tableNameMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(tableIds)) {
            List<CfgTableDO> tableDOList = tableService.getByIds(tableIds);
            tableNameMap = tableDOList.stream().collect(Collectors.toMap(CfgTableDO::getId, CfgTableDO::getName));
        }

        Map<Long, List<CfgTableDataCrossEditDO>> map = allTableCrossEditDOList.stream().collect(Collectors.groupingBy(CfgTableDataCrossEditDO::getRelationId));
        List<CrossTableDataEditVO> res = new ArrayList<>();
        for (CfgTableDataRelationDO relationDO : relationDOList) {
            if (!map.containsKey(relationDO.getId())) {
                continue;
            }

            List<CfgTableDataCrossEditDO> editDOList = map.get(relationDO.getId());
            for (CfgTableDataCrossEditDO editDO : editDOList) {
                CrossTableDataEditVO vo = new CrossTableDataEditVO();
                vo.setId(editDO.getId().toString());
                vo.setCurrentTableId(relationDO.getCurrentTableId().toString());
                vo.setCurrentTableName(tableNameMap.get(relationDO.getCurrentTableId()));
                Long currentRelationFieldId = relationDO.getCurrentRelationFieldId();
                vo.setCurrentRelationField(fieldMap.get(currentRelationFieldId));
                vo.setTargetTableId(relationDO.getTargetTableId().toString());
                vo.setTargetTableName(tableNameMap.get(relationDO.getTargetTableId()));
                Long targetRelationFieldId = relationDO.getTargetRelationFieldId();
                vo.setTargetRelationField(fieldMap.get(targetRelationFieldId));

                List<FieldSimpleInfo> list = new ArrayList<>();
                for (Long id : editDO.getCurrentTableFieldIds()) {
                    list.add(fieldMap.get(id));
                }
                vo.setCurrentTableFieldList(list);
                Long targetTableFieldId = editDO.getTargetTableFieldId();
                vo.setTargetTableField(fieldMap.get(targetTableFieldId));
                vo.setFunctionName(editDO.getFunctionName());
                vo.setErrorPrompt(editDO.getErrorPrompt());
                vo.setStatus(editDO.getStatus());
                vo.setVerifyType(editDO.getVerifyType());
                vo.setSequence(editDO.getSequence());
                res.add(vo);
            }
        }
        return res;
    }

    public boolean updateCrossTableDataEditRule(UpdateTableDataRelationDTO dto) {
        CfgTableDataCrossEditDO detailDO = new CfgTableDataCrossEditDO();
        detailDO.setId(dto.getId());

        if (dto.getCurrentTableId() != null && dto.getTargetTableId() != null) {
            CfgTableDataRelationDO relationDO = tableDataRelationService.getDOByTableIds(dto.getCurrentTableId(), dto.getTargetTableId());
            detailDO.setRelationId(relationDO.getId());
        }

        if (!CollectionUtils.isEmpty(dto.getCurrentTableFieldIdList())) {
            detailDO.setCurrentTableFieldIds(dto.getCurrentTableFieldIdList());
        }

        detailDO.setFunctionName(dto.getFunctionName());
        detailDO.setTargetTableFieldId(dto.getTargetTableFieldId());
        detailDO.setUpdateTime(new Date());
        detailDO.setErrorPrompt(dto.getErrorPrompt());
        detailDO.setStatus(dto.getStatus());
        detailDO.setVerifyType(dto.getVerifyType());
        return crossTableEditService.updateById(detailDO);
    }

    public boolean createTableRowVerifyRule(CreateTableRowVerifyDTO dto) {
        Long tableId = dto.getTableId();
        CfgTableDO tableDO = tableService.getDOById(tableId);

        CfgTableDataRowVerifyDO verifyDO = new CfgTableDataRowVerifyDO();
        verifyDO.setTableId(tableId);
        verifyDO.setPageId(tableDO.getPageId());
        verifyDO.setFieldIds(dto.getFieldIdList());
        verifyDO.setFunctionName(dto.getFunctionName());
        verifyDO.setOperator(dto.getOperator());
        verifyDO.setValueType(dto.getValueType());
        verifyDO.setValue(dto.getValue());

        Integer sequence = tableDataRowVerifyService.getMaxSequenceByTableId(tableId);
        verifyDO.setSequence(sequence == null ? 1 : sequence + 1);

        verifyDO.setVerifyType(dto.getVerifyType());
        verifyDO.setCreateTime(new Date());
        return tableDataRowVerifyService.create(verifyDO);
    }

    public boolean updateTableRowVerifyRule(UpdateTableRowVerifyDTO dto) {
        CfgTableDataRowVerifyDO verifyDO = new CfgTableDataRowVerifyDO();
        verifyDO.setId(dto.getId());
        if (!CollectionUtils.isEmpty(dto.getFieldIdList())) {
            verifyDO.setFieldIds(dto.getFieldIdList());
        }
        verifyDO.setFunctionName(dto.getFunctionName());
        verifyDO.setOperator(dto.getOperator());
        verifyDO.setValueType(dto.getValueType());
        verifyDO.setValue(dto.getValue());
        verifyDO.setUpdateTime(new Date());
        verifyDO.setErrorPrompt(dto.getErrorPrompt());
        verifyDO.setStatus(dto.getStatus());
        verifyDO.setVerifyType(dto.getVerifyType());
        return tableDataRowVerifyService.updateById(verifyDO);
    }

    public List<TableRelationVO> getTableRelationByTargetTableId(Long tableId, Integer type) {
        List<CfgTableDataRelationDO> relationDOList;
        if (type != null && type == 1) {
            relationDOList = tableDataRelationService.getDOByTargetTableId(tableId);
        } else if (type != null && type == 2) {
            relationDOList = tableDataRelationService.getDOByCurrentTableId(tableId);
        } else {
            throw new ServiceException(500, "请传入表格类型");
        }

        List<TableRelationVO> voList = new ArrayList<>();
        for (CfgTableDataRelationDO relationDO : relationDOList) {
            TableRelationVO vo = new TableRelationVO();
            vo.setCurrentTableId(relationDO.getCurrentTableId().toString());
            CfgTableDO currentTable = tableService.getDOById(relationDO.getCurrentTableId());
            vo.setCurrentTableName(currentTable.getName());
            CfgFieldDO current = fieldService.getDOById(relationDO.getCurrentRelationFieldId());
            FieldSimpleInfo currentField = new FieldSimpleInfo(
                    current.getId().toString(), current.getBizCode(), current.getBizName(), current.getDataBinding());
            vo.setCurrentRelationField(currentField);

            vo.setTargetTableId(relationDO.getTargetTableId().toString());
            CfgTableDO targetTable = tableService.getDOById(relationDO.getTargetTableId());
            vo.setTargetTableName(targetTable.getName());
            CfgFieldDO target = fieldService.getDOById(relationDO.getTargetRelationFieldId());
            FieldSimpleInfo targetField = new FieldSimpleInfo(
                    target.getId().toString(), target.getBizCode(), target.getBizName(), target.getDataBinding());
            vo.setTargetRelationField(targetField);
            voList.add(vo);
        }
        return voList;
    }

    public List<TableRowVerifyRuleVO> getTableRowVerifyRuleByTableId(Long tableId) {
        List<CfgTableDataRowVerifyDO> verifyDOList = tableDataRowVerifyService.getDOListByTableId(tableId);
        Set<Long> tableIds = verifyDOList.stream().map(CfgTableDataRowVerifyDO::getTableId).collect(Collectors.toSet());
        Map<Long, String> tableNameMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(tableIds)) {
            List<CfgTableDO> tableDOList = tableService.getByIds(tableIds);
            tableNameMap = tableDOList.stream().collect(Collectors.toMap(CfgTableDO::getId, CfgTableDO::getName));
        }

        List<TableRowVerifyRuleVO> voList = new ArrayList<>();
        for (CfgTableDataRowVerifyDO verifyDO : verifyDOList) {
            TableRowVerifyRuleVO vo = new TableRowVerifyRuleVO();
            vo.setId(verifyDO.getId().toString());

            List<Long> fieldIds = verifyDO.getFieldIds();
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(fieldIds);
            Map<Long, CfgFieldDO> fieldDOMap = fieldDOList.stream().collect(Collectors.toMap(CfgFieldDO::getId, i -> i));
            List<CfgFieldDO> fieldDOListNew = fieldIds.stream().map(fieldDOMap::get).toList();
            List<FieldSimpleInfo> fieldList = new ArrayList<>();
            for (CfgFieldDO fieldDO : fieldDOListNew) {
                FieldSimpleInfo fieldSimpleInfo = new FieldSimpleInfo(
                        fieldDO.getId().toString(), fieldDO.getBizCode(), fieldDO.getBizName(), fieldDO.getDataBinding());
                fieldList.add(fieldSimpleInfo);
            }
            vo.setFieldList(fieldList);

            vo.setFunctionName(verifyDO.getFunctionName());
            vo.setOperator(verifyDO.getOperator());
            vo.setValueType(verifyDO.getValueType());

            if (ValueTypeEnum.FIXED.getValue().equals(verifyDO.getValueType())) {
                vo.setValue(verifyDO.getValue());
            } else if (ValueTypeEnum.DYNAMIC.getValue().equals(verifyDO.getValueType())) {
                String fieldId = verifyDO.getValue();
                CfgFieldDO fieldDO = fieldService.getDOById(Long.parseLong(fieldId));
                FieldSimpleInfo fieldSimpleInfo = new FieldSimpleInfo(
                        fieldDO.getId().toString(), fieldDO.getBizCode(), fieldDO.getBizName(), fieldDO.getDataBinding());
                vo.setValue(fieldSimpleInfo);
            }
            vo.setErrorPrompt(verifyDO.getErrorPrompt());
            vo.setStatus(verifyDO.getStatus());
            vo.setVerifyType(verifyDO.getVerifyType());
            vo.setTableId(verifyDO.getTableId().toString());
            vo.setTableName(tableNameMap.get(verifyDO.getTableId()));
            voList.add(vo);
        }
        return voList;
    }

    public boolean deleteRowVerifyRuleById(Long id) {
        return tableDataRowVerifyService.deleteById(id);
    }

    public boolean deleteCrossTableDataEditRule(Long id) {
        return crossTableEditService.deleteById(id);
    }

    public boolean createCrossTableDataVerifyRule(CreateCrossTableDataVerifyRuleDTO dto) {
        CfgTableDataRelationDO relationDO = tableDataRelationService.getDOByTableIds(dto.getCurrentTableId(), dto.getTargetTableId());
        if (relationDO == null) {
            throw new ServiceException(500, "请先建立表格关系再创建跨表格规则");
        }

        CfgTableDataCrossVerifyDO verifyDO = new CfgTableDataCrossVerifyDO();
        verifyDO.setPageId(relationDO.getPageId());
        verifyDO.setRelationId(relationDO.getId());
        verifyDO.setCurrentTableFieldIds(dto.getCurrentTableFieldIdList());
        verifyDO.setFunctionName(dto.getFunctionName());
        verifyDO.setTargetTableFieldId(dto.getTargetTableFieldId());
        verifyDO.setOperator(dto.getOperator());

        Integer sequence = crossTableDataVerifyService.getMaxSequenceByRelationId(relationDO.getId());
        verifyDO.setSequence(sequence == null ? 1 : sequence + 1);

        verifyDO.setVerifyType(dto.getVerifyType());
        verifyDO.setDeleted(DeletedEnum.UNDELETED.getCode());
        verifyDO.setCreateTime(new Date());

        return crossTableDataVerifyService.create(verifyDO);
    }

    public List<CrossTableDataVerifyRuleVO> getCrossTableDataVerifyRule(Long tableId, Integer type) {
        List<CfgTableDataRelationDO> relationDOList;
        if (type != null && type == 1) {
            relationDOList = tableDataRelationService.getDOByTargetTableId(tableId); //1->TargetTable
        } else if (type != null && type == 2) {
            relationDOList = tableDataRelationService.getDOByCurrentTableId(tableId); //2->CurrentTable
        } else {
            throw new ServiceException(500, "请传入正确的表格id类型, 1:TargetTableId 2:CurrentTableId");
        }
        if (CollectionUtils.isEmpty(relationDOList)) return new ArrayList<>();

        List<Long> fieldIdList = new ArrayList<>();
        List<CfgTableDataCrossVerifyDO> allVerifyDOList = new ArrayList<>();
        HashSet<Long> tableIds = new HashSet<>();
        for (CfgTableDataRelationDO relationDO : relationDOList) {
            fieldIdList.add(relationDO.getCurrentRelationFieldId());
            fieldIdList.add(relationDO.getTargetRelationFieldId());

            List<CfgTableDataCrossVerifyDO> verifyDOList = crossTableDataVerifyService.getDOListByRelationId(relationDO.getId());
            if (!CollectionUtils.isEmpty(verifyDOList)) allVerifyDOList.addAll(verifyDOList);
            for (CfgTableDataCrossVerifyDO verifyDO : verifyDOList) {
                fieldIdList.addAll(verifyDO.getCurrentTableFieldIds());
                fieldIdList.add(verifyDO.getTargetTableFieldId());
            }

            tableIds.add(relationDO.getCurrentTableId());
            tableIds.add(relationDO.getTargetTableId());
        }
        if (CollectionUtils.isEmpty(allVerifyDOList)) return new ArrayList<>();

        List<CfgFieldDO> fieldDOList = fieldService.getDOListByIdList(fieldIdList);
        Map<Long, FieldSimpleInfo> fieldMap = fieldDOList.stream().collect(Collectors.toMap(CfgFieldDO::getId, cfgFieldDO -> {
            FieldSimpleInfo field = new FieldSimpleInfo();
            field.setId(cfgFieldDO.getId().toString());
            field.setBizCode(cfgFieldDO.getBizCode());
            field.setBizName(cfgFieldDO.getBizName());
            field.setDataBinding(cfgFieldDO.getDataBinding());
            return field;
        }));

        Map<Long, String> tableNameMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(tableIds)) {
            List<CfgTableDO> tableDOList = tableService.getByIds(tableIds);
            tableNameMap = tableDOList.stream().collect(Collectors.toMap(CfgTableDO::getId, CfgTableDO::getName));
        }

        HashMap<Long, List<CfgTableDataCrossVerifyDO>> verifyDOMap = allVerifyDOList.stream().collect(Collectors.groupingBy(CfgTableDataCrossVerifyDO::getRelationId, HashMap::new, Collectors.toList()));
        List<CrossTableDataVerifyRuleVO> res = new ArrayList<>();
        for (CfgTableDataRelationDO relationDO : relationDOList) {
            if (!verifyDOMap.containsKey(relationDO.getId())) {
                continue;
            }

            List<CfgTableDataCrossVerifyDO> verifyDOList = verifyDOMap.get(relationDO.getId());
            for (CfgTableDataCrossVerifyDO verifyDO : verifyDOList) {
                CrossTableDataVerifyRuleVO vo = new CrossTableDataVerifyRuleVO();
                vo.setId(verifyDO.getId().toString());
                vo.setCurrentTableId(relationDO.getCurrentTableId().toString());
                vo.setCurrentTableName(tableNameMap.get(relationDO.getCurrentTableId()));
                vo.setCurrentRelationField(fieldMap.get(relationDO.getCurrentRelationFieldId()));
                vo.setTargetTableId(relationDO.getTargetTableId().toString());
                vo.setTargetTableName(tableNameMap.get(relationDO.getTargetTableId()));
                vo.setTargetRelationField(fieldMap.get(relationDO.getTargetRelationFieldId()));

                List<FieldSimpleInfo> list = new ArrayList<>();
                for (Long id : verifyDO.getCurrentTableFieldIds()) {
                    list.add(fieldMap.get(id));
                }
                vo.setCurrentTableFieldList(list);
                vo.setFunctionName(verifyDO.getFunctionName());
                vo.setTargetTableField(fieldMap.get(verifyDO.getTargetTableFieldId()));
                vo.setOperator(verifyDO.getOperator());
                vo.setErrorPrompt(verifyDO.getErrorPrompt());
                vo.setStatus(verifyDO.getStatus());
                vo.setVerifyType(verifyDO.getVerifyType());
                res.add(vo);
            }
        }
        return res;
    }

    public boolean updateCrossTableDataVerifyRule(UpdateCrossTableDataVerifyRuleDTO dto) {
        CfgTableDataCrossVerifyDO verifyDO = new CfgTableDataCrossVerifyDO();
        verifyDO.setId(dto.getId());

        if (dto.getCurrentTableId() != null && dto.getTargetTableId() != null) {
            CfgTableDataRelationDO relationDO = tableDataRelationService.getDOByTableIds(dto.getCurrentTableId(), dto.getTargetTableId());
            if (relationDO == null) {
                throw new ServiceException(500, "请先设置两个表格的数据对应关系");
            }
            verifyDO.setRelationId(relationDO.getId());
        }
        if (!CollectionUtils.isEmpty(dto.getCurrentTableFieldIdList())) {
            verifyDO.setCurrentTableFieldIds(dto.getCurrentTableFieldIdList());
        }
        verifyDO.setFunctionName(dto.getFunctionName());
        verifyDO.setTargetTableFieldId(dto.getTargetTableFieldId());
        verifyDO.setOperator(dto.getOperator());
        verifyDO.setUpdateTime(new Date());
        verifyDO.setErrorPrompt(dto.getErrorPrompt());
        verifyDO.setStatus(dto.getStatus());
        verifyDO.setVerifyType(dto.getVerifyType());
        return crossTableDataVerifyService.updateById(verifyDO);
    }

    public boolean deleteCrossTableDataVerifyRule(Long id) {
        return crossTableDataVerifyService.deleteCrossTableDataVerifyRule(id);
    }

    public List<EditTableRuleVO> getTableEditRule(String pageCode, String bizIdentityCode) {
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        List<CfgTableDO> tableDOList = tableService.getDOListByPageId(pageDO.getId());
        List<Long> tableIdList = tableDOList.stream().map(CfgTableDO::getId).toList();

        // 行内
        List<TableRowEditRuleVO> rowEditRuleVOList = tableIdList.stream()
                .flatMap(i -> getTableRowEditRule(i).stream())
                .toList();

        // 表间
        List<CrossTableDataEditVO> crossTableEditVOList = tableIdList.stream()
                .flatMap(i -> getCrossTableEditRule(i, 1).stream())
                .toList();

        List<EditTableRuleVO> res = new ArrayList<>();
        rowEditRuleVOList.stream().map(i -> new EditTableRuleVO(1, i)).forEach(res::add);
        crossTableEditVOList.stream().map(i -> new EditTableRuleVO(2, i)).forEach(res::add);
        res.sort(Comparator.comparing(i -> i.getRule().returnSequence()));
        return res;
    }

    public List<VerifyTableRuleVO> getTableVerifyRule(String pageCode, String bizIdentityCode) {
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        List<CfgTableDO> tableDOList = tableService.getDOListByPageId(pageDO.getId());
        List<Long> tableIdList = tableDOList.stream().map(CfgTableDO::getId).toList();

        // 行内
        List<TableRowVerifyRuleVO> rowVerifyVOList = tableIdList.stream().flatMap(i -> getTableRowVerifyRuleByTableId(i).stream()).toList();

        // 表间
        List<CrossTableDataVerifyRuleVO> crossTableVerifyVOList = tableIdList.stream().flatMap(i -> getCrossTableDataVerifyRule(i, 1).stream()).toList();

        List<VerifyTableRuleVO> res = new ArrayList<>();
        rowVerifyVOList.stream().map(i -> new VerifyTableRuleVO(1, i)).forEach(res::add);
        crossTableVerifyVOList.stream().map(i -> new VerifyTableRuleVO(2, i)).forEach(res::add);
        return res;
    }

    public FourTableRuleVO getFourRule(Long tableId) {
        //表格行内动态规则
        List<TableRowEditRuleVO> rowEditRuleVOList = getTableRowEditRule(tableId);
        rowEditRuleVOList = rowEditRuleVOList.stream().filter(i -> i.getStatus() == StatusEnum.YES.getCode()).toList();

        //表间字段动态规则
        List<CrossTableDataEditVO> crossTableDataEditVOList = getCrossTableEditRule(tableId, 2);
        crossTableDataEditVOList = crossTableDataEditVOList.stream().filter(i -> i.getStatus() == StatusEnum.YES.getCode()).toList();

        //行内字段校验规则
        List<TableRowVerifyRuleVO> rowVerifyRuleVOList = getTableRowVerifyRuleByTableId(tableId);
        rowVerifyRuleVOList = rowVerifyRuleVOList.stream().filter(i -> i.getStatus() == StatusEnum.YES.getCode()).toList();

        //表间字段校验规则
        List<CrossTableDataVerifyRuleVO> crossTableDataVerifyRuleVOList = getCrossTableDataVerifyRule(tableId, 2);
        crossTableDataVerifyRuleVOList = crossTableDataVerifyRuleVOList.stream().filter(i -> i.getStatus() == StatusEnum.YES.getCode()).toList();

        return new FourTableRuleVO(rowEditRuleVOList, crossTableDataEditVOList, rowVerifyRuleVOList, crossTableDataVerifyRuleVOList);
    }

    @Transactional(rollbackFor = Throwable.class)
    public void updateSequence(List<UpdateSequenceDTO> paramList) {
        if (CollectionUtils.isEmpty(paramList)) {
            throw new ServiceException(500, "更新表格动态规则序号的参数不能为空");
        }

        List<CfgTableDataRowEditDO> tableRowEditDOList = new ArrayList<>();
        List<CfgTableDataCrossEditDO> tableCrossEditDOList = new ArrayList<>();
        for (UpdateSequenceDTO param : paramList) {
            if (param.getType() == 1) {
                CfgTableDataRowEditDO tableRowEditDO = new CfgTableDataRowEditDO();
                tableRowEditDO.setId(param.getRuleId());
                tableRowEditDO.setSequence(param.getSequence());
                tableRowEditDOList.add(tableRowEditDO);
            } else if (param.getType() == 2) {
                CfgTableDataCrossEditDO tableCrossEditDO = new CfgTableDataCrossEditDO();
                tableCrossEditDO.setId(param.getRuleId());
                tableCrossEditDO.setSequence(param.getSequence());
                tableCrossEditDOList.add(tableCrossEditDO);
            }
        }
        if (!CollectionUtils.isEmpty(tableRowEditDOList)) {
            tableRowEditService.batchUpdateById(tableRowEditDOList);
        }
        if (!CollectionUtils.isEmpty(tableCrossEditDOList)) {
            crossTableEditService.batchUpdateById(tableCrossEditDOList);
        }
    }

    public List<TableRowEditRuleVO> getTableRowEditRule(List<CfgTableDataRowEditDO> rowRuleDOList, Map<Long, CfgFieldDO> fieldDOMap) {
        if (CollectionUtils.isEmpty(rowRuleDOList) || fieldDOMap == null) {
            return new ArrayList<>();
        }

        List<TableRowEditRuleVO> res = new ArrayList<>();
        for (CfgTableDataRowEditDO rowRuleDO : rowRuleDOList) {
            TableRowEditRuleVO vo = new TableRowEditRuleVO();
            vo.setId(rowRuleDO.getId().toString());
            vo.setTableId(rowRuleDO.getTableId().toString());

            List<Long> sourceFieldIds = rowRuleDO.getSourceFieldIds();
            List<CfgFieldDO> fieldDOList = sourceFieldIds.stream().map(fieldDOMap::get).toList();
            if (CollectionUtils.isEmpty(fieldDOList)) {
                throw new ServiceException(500, "源字段查询失败");
            }
            List<RowRuleField> sourceFields = fieldDOList.stream().map(i -> new RowRuleField(i.getId().toString(), i.getBizName(), i.getDataBinding())).toList();
            vo.setSourceFieldList(sourceFields);
            vo.setFunctionName(rowRuleDO.getFunctionName());

            Long targetFieldId = rowRuleDO.getTargetFieldId();
            CfgFieldDO fieldDO = fieldDOMap.get(targetFieldId);
            if (fieldDO == null) {
                throw new ServiceException(500, "目标字段查询失败");
            }
            RowRuleField field = new RowRuleField(fieldDO.getId().toString(), fieldDO.getBizName(), fieldDO.getDataBinding());
            vo.setTargetField(field);

            vo.setErrorPrompt(rowRuleDO.getErrorPrompt());
            vo.setStatus(rowRuleDO.getStatus());
            vo.setVerifyType(rowRuleDO.getVerifyType());
            vo.setSequence(rowRuleDO.getSequence());
            res.add(vo);
        }
        return res;
    }

    public List<TableRowVerifyRuleVO> getTableRowVerifyRule(List<CfgTableDataRowVerifyDO> verifyDOList, Map<Long, CfgFieldDO> fieldDOMap) {
        if (CollectionUtils.isEmpty(verifyDOList) || fieldDOMap == null) {
            return new ArrayList<>();
        }

        List<TableRowVerifyRuleVO> voList = new ArrayList<>();
        for (CfgTableDataRowVerifyDO verifyDO : verifyDOList) {
            TableRowVerifyRuleVO vo = new TableRowVerifyRuleVO();
            vo.setId(verifyDO.getId().toString());

            List<Long> fieldIds = verifyDO.getFieldIds();
            List<CfgFieldDO> fieldDOList = fieldIds.stream().map(fieldDOMap::get).toList();
            if (CollectionUtils.isEmpty(fieldDOList)) {
                throw new ServiceException(500, "字段列表查询失败");
            }
            List<FieldSimpleInfo> fieldList = fieldDOList.stream()
                    .map(i -> new FieldSimpleInfo(i.getId().toString(), i.getBizCode(), i.getBizName(), i.getDataBinding()))
                    .toList();
            vo.setFieldList(fieldList);

            vo.setFunctionName(verifyDO.getFunctionName());
            vo.setOperator(verifyDO.getOperator());
            vo.setValueType(verifyDO.getValueType());

            if (ValueTypeEnum.FIXED.getValue().equals(verifyDO.getValueType())) {
                vo.setValue(verifyDO.getValue());
            } else if (ValueTypeEnum.DYNAMIC.getValue().equals(verifyDO.getValueType())) {
                String fieldId = verifyDO.getValue();
                CfgFieldDO fieldDO = fieldDOMap.get(Long.parseLong(fieldId));
                if (fieldDO == null) {
                    throw new ServiceException(500, "动态值字段查询失败");
                }
                FieldSimpleInfo fieldSimpleInfo = new FieldSimpleInfo(
                        fieldDO.getId().toString(), fieldDO.getBizCode(), fieldDO.getBizName(), fieldDO.getDataBinding());
                vo.setValue(fieldSimpleInfo);
            }
            vo.setErrorPrompt(verifyDO.getErrorPrompt());
            vo.setStatus(verifyDO.getStatus());
            vo.setVerifyType(verifyDO.getVerifyType());
            vo.setTableId(verifyDO.getTableId().toString());
            voList.add(vo);
        }
        return voList;
    }

    public List<CrossTableDataEditVO> getCrossTableEditVOList(List<CfgTableDataRelationDO> relationDOList,
                                                              List<CfgTableDataCrossEditDO> tableCrossEditDOList,
                                                              Map<Long, CfgFieldDO> fieldDOMap) {
        if (CollectionUtils.isEmpty(relationDOList) || CollectionUtils.isEmpty(tableCrossEditDOList) || fieldDOMap == null) {
            return new ArrayList<>();
        }

        Map<Long, List<CfgTableDataCrossEditDO>> map = tableCrossEditDOList.stream().collect(Collectors.groupingBy(CfgTableDataCrossEditDO::getRelationId));
        List<CrossTableDataEditVO> res = new ArrayList<>();
        for (CfgTableDataRelationDO relationDO : relationDOList) {
            if (!map.containsKey(relationDO.getId())) {
                continue;
            }

            List<CfgTableDataCrossEditDO> editDOList = map.get(relationDO.getId());
            for (CfgTableDataCrossEditDO ruleDO : editDOList) {
                CrossTableDataEditVO vo = new CrossTableDataEditVO();
                vo.setId(ruleDO.getId().toString());
                vo.setCurrentTableId(relationDO.getCurrentTableId().toString());
                Long currentRelationFieldId = relationDO.getCurrentRelationFieldId();
                CfgFieldDO field1 = fieldDOMap.get(currentRelationFieldId);
                FieldSimpleInfo simpleField1 = new FieldSimpleInfo(field1.getId().toString(), field1.getBizCode(),
                        field1.getBizName(), field1.getDataBinding());
                vo.setCurrentRelationField(simpleField1);

                vo.setTargetTableId(relationDO.getTargetTableId().toString());
                Long targetRelationFieldId = relationDO.getTargetRelationFieldId();
                CfgFieldDO field2 = fieldDOMap.get(targetRelationFieldId);
                FieldSimpleInfo simpleField2 = new FieldSimpleInfo(field2.getId().toString(), field2.getBizCode(),
                        field2.getBizName(), field2.getDataBinding());
                vo.setTargetRelationField(simpleField2);

                List<FieldSimpleInfo> list = ruleDO.getCurrentTableFieldIds().stream().map(i -> {
                    CfgFieldDO fieldDO = fieldDOMap.get(i);
                    return new FieldSimpleInfo(fieldDO.getId().toString(), fieldDO.getBizCode(),
                            fieldDO.getBizName(), fieldDO.getDataBinding());
                }).toList();
                vo.setCurrentTableFieldList(list);

                Long targetTableFieldId = ruleDO.getTargetTableFieldId();
                CfgFieldDO field3 = fieldDOMap.get(targetTableFieldId);
                FieldSimpleInfo simpleField3 = new FieldSimpleInfo(field3.getId().toString(), field3.getBizCode(),
                        field3.getBizName(), field3.getDataBinding());
                vo.setTargetTableField(simpleField3);
                vo.setFunctionName(ruleDO.getFunctionName());
                vo.setErrorPrompt(ruleDO.getErrorPrompt());
                vo.setStatus(ruleDO.getStatus());
                vo.setVerifyType(ruleDO.getVerifyType());
                vo.setSequence(ruleDO.getSequence());
                res.add(vo);
            }
        }
        return res;
    }

    public List<CrossTableDataVerifyRuleVO> getCrossTableDataVerifyRule(List<CfgTableDataRelationDO> relationDOList,
                                                                        List<CfgTableDataCrossVerifyDO> tableCrossVerifyDOList,
                                                                        Map<Long, CfgFieldDO> fieldDOMap) {
        if (CollectionUtils.isEmpty(relationDOList) || CollectionUtils.isEmpty(tableCrossVerifyDOList) || fieldDOMap == null) {
            return new ArrayList<>();
        }

        Map<Long, List<CfgTableDataCrossVerifyDO>> verifyDOMap = tableCrossVerifyDOList.stream().collect(Collectors.groupingBy(CfgTableDataCrossVerifyDO::getRelationId));
        List<CrossTableDataVerifyRuleVO> res = new ArrayList<>();
        for (CfgTableDataRelationDO relationDO : relationDOList) {
            if (!verifyDOMap.containsKey(relationDO.getId())) {
                continue;
            }

            List<CfgTableDataCrossVerifyDO> verifyDOList = verifyDOMap.get(relationDO.getId());
            for (CfgTableDataCrossVerifyDO verifyDO : verifyDOList) {
                CrossTableDataVerifyRuleVO vo = new CrossTableDataVerifyRuleVO();
                vo.setId(verifyDO.getId().toString());
                vo.setCurrentTableId(relationDO.getCurrentTableId().toString());
                Long currentRelationFieldId = relationDO.getCurrentRelationFieldId();
                CfgFieldDO field1 = fieldDOMap.get(currentRelationFieldId);
                FieldSimpleInfo simpleField1 = new FieldSimpleInfo(field1.getId().toString(), field1.getBizCode(),
                        field1.getBizName(), field1.getDataBinding());
                vo.setCurrentRelationField(simpleField1);

                vo.setTargetTableId(relationDO.getTargetTableId().toString());
                Long targetRelationFieldId = relationDO.getTargetRelationFieldId();
                CfgFieldDO field2 = fieldDOMap.get(targetRelationFieldId);
                FieldSimpleInfo simpleField2 = new FieldSimpleInfo(field2.getId().toString(), field2.getBizCode(),
                        field2.getBizName(), field2.getDataBinding());
                vo.setTargetRelationField(simpleField2);

                List<FieldSimpleInfo> list = verifyDO.getCurrentTableFieldIds().stream().map(i -> {
                    CfgFieldDO fieldDO = fieldDOMap.get(i);
                    return new FieldSimpleInfo(fieldDO.getId().toString(), fieldDO.getBizCode(),
                            fieldDO.getBizName(), fieldDO.getDataBinding());
                }).toList();
                vo.setCurrentTableFieldList(list);
                vo.setFunctionName(verifyDO.getFunctionName());
                Long targetTableFieldId = verifyDO.getTargetTableFieldId();
                CfgFieldDO field3 = fieldDOMap.get(targetTableFieldId);
                FieldSimpleInfo simpleField3 = new FieldSimpleInfo(field3.getId().toString(), field3.getBizCode(),
                        field3.getBizName(), field3.getDataBinding());
                vo.setTargetTableField(simpleField3);
                vo.setOperator(verifyDO.getOperator());
                vo.setErrorPrompt(verifyDO.getErrorPrompt());
                vo.setStatus(verifyDO.getStatus());
                vo.setVerifyType(verifyDO.getVerifyType());
                res.add(vo);
            }
        }
        return res;
    }
}
