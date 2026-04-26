package com.bone.lowcode.infra.application.service;

import com.alibaba.fastjson.JSON;
import com.bone.core.exception.ServiceException;
import com.bone.lowcode.infra.application.convert.FieldConvert;
import com.bone.lowcode.infra.application.convert.ModelConvert;
import com.bone.lowcode.infra.application.convert.TableConvert;
import com.bone.lowcode.infra.application.dto.table.*;
import com.bone.lowcode.infra.application.vo.field.GroupAggregateField;
import com.bone.lowcode.infra.application.vo.page.pageJson.EventTrigger;
import com.bone.lowcode.infra.application.vo.table.*;
import com.bone.lowcode.infra.domain.model.DataSummaryRule;
import com.bone.lowcode.infra.domain.model.EditableColumn;
import com.bone.lowcode.infra.domain.model.TableFieldSortType;
import com.bone.lowcode.infra.domain.service.*;
import com.bone.lowcode.infra.domain.valueobject.ComponentTypeEnum;
import com.bone.lowcode.infra.domain.valueobject.DeletedEnum;
import com.bone.lowcode.infra.domain.valueobject.GroupAggregateFieldEnum;
import com.bone.lowcode.infra.domain.valueobject.TriggerStyleEnum;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;


@Service
public class TableApplicationService {

    @Autowired
    private PageService pageService;

    @Autowired
    private TableService tableService;
    @Autowired
    private ModelService modelService;
    @Autowired
    private FieldService fieldService;
    @Autowired
    private TableDataGroupAggregateService tableDataGroupAggregateService;

    @Autowired
    private EventTriggerService triggerService;

    @Autowired
    private EventService eventService;


    public TableVo getById(Long tableId) {
        CfgTableDO tableDO = tableService.getDOById(tableId);

        List<Long> modelIds = tableDO.getModelIds();
        List<ModelOfTable> dataModelList = new ArrayList<>();
        List<EditableColumn> editableColumnList = tableDO.getEditableColumns();
        Map<Long, EditableColumn> map = editableColumnList.stream().collect(Collectors.toMap(i -> Long.parseLong(i.getFieldId()), i -> i));

        for (Long modelId : modelIds) {
            CfgModelDO modelDO = modelService.getDOById(modelId);
            ModelOfTable model = new ModelOfTable();
            dataModelList.add(model);
            model.setId(modelDO.getId().toString());
            model.setTitle(modelDO.getName());
            model.setUsed(modelDO.getStatus());
            List<FieldOfTable> tableFields = new ArrayList<>();
            model.setTableFields(tableFields);
            List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelId(modelDO.getId());
            for (CfgFieldDO fieldDO : fieldDOList) {
                FieldOfTable field = new FieldOfTable();
                field.setId(fieldDO.getId().toString());
                field.setTitle(fieldDO.getTitle());
                field.setModelId(modelDO.getId().toString());
                field.setDisplay(fieldDO.getDisplayed());
                field.setSequence(fieldDO.getSequence());
                field.setBizCode(fieldDO.getBizCode());
                field.setBizName(fieldDO.getBizName());
                if (map.containsKey(fieldDO.getId())) {
                    EditableColumn column = map.get(fieldDO.getId());
                    field.setSingleLineEditable(column.getSingleLineEditable());
                    field.setBatchEditable(column.getBatchEditable());
                }
                tableFields.add(field);
            }
        }

        List<CfgEventTriggerDO> triggerDOList = triggerService.getDOListByTableId(tableId);
        List<EventTrigger> triggerList = triggerDOList.stream().map(triggerDO -> {
            EventTrigger trigger = new EventTrigger();
            trigger.setId(triggerDO.getId().toString());
            trigger.setLabel(triggerDO.getLabel());
            trigger.setStyle(triggerDO.getStyle());
            trigger.setDisplayType(triggerDO.getDisplayType());
            trigger.setOwner(triggerDO.getOwner());
            CfgEventDO eventDO = eventService.getById(triggerDO.getEventId());
            if (eventDO != null) {
                trigger.setEventId(eventDO.getId().toString());
                trigger.setEventName(eventDO.getName());
                trigger.setEventCode(eventDO.getCode());
            }
            return trigger;
        }).toList();

        TableVo tableVo = getTableVo(tableDO, dataModelList, triggerList);
        return tableVo;
    }

    private TableVo getTableVo(CfgTableDO tableDO, List<ModelOfTable> dataModel, List<EventTrigger> triggerList) {
        TableVo tableVo = new TableVo();
        BeanUtils.copyProperties(tableDO, tableVo);
        tableVo.setId(tableDO.getId().toString());
        tableVo.setModelType("待确认");
        tableVo.setTableName(tableDO.getName());
        tableVo.setPrompt(tableDO.getPrompt());
        tableVo.setEmptyPrompt(tableDO.getEmptyPrompt());

        tableVo.setDataModel(dataModel);

        if (StringUtils.hasText(tableDO.getOrderByList())) {
            List<TableFieldSortType> sortTypeList = tableDO.getSortTypeList();
            tableVo.setFieldSortTypeList(sortTypeList);
        }

        tableVo.setEnableOrderColumn(tableDO.getEnableOrderColumn());
        tableVo.setOperationColumnEnabled(tableDO.getOperationColumnEnabled());
        tableVo.setOperationColumnFixed(tableDO.getOperationColumnFixed());

        tableVo.setEventTriggerList(triggerList);

        tableVo.setPaginationOpened(tableDO.getPaginationEnabled());
        tableVo.setDefaultPageSize(tableDO.getDefaultPageSize());
        tableVo.setDisplayTotalPage(tableDO.getDisplayTotalPage());
        tableVo.setDisplayTotalSize(tableDO.getDisplayTotalSize());
        tableVo.setEnableDataSummary(tableDO.getEnableDataSummary());
        tableVo.setEnableDataAggregate(tableDO.getEnableDataAggregate());
        return tableVo;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateTableById(UpdateTableDTO dto) {
        CfgTableDO tableDO = TableConvert.updateTableDTOToDo(dto);
        boolean flag1 = tableService.updateAcceptNull(tableDO);

        handleEventTrigger(dto);//删除+新增

        List<ModelOfTableChange> modelChangeList = dto.getModelOfTableChangelist();
        if (!CollectionUtils.isEmpty(modelChangeList)) {
            List<CfgModelDO> modelDOList = ModelConvert.modelChangeListToDOList(modelChangeList);
            modelService.batchUpdateById(modelDOList);
        }

        List<FieldOfTableChange> fieldChangeList = dto.getFieldOfTableChangeList();
        if (!CollectionUtils.isEmpty(fieldChangeList)) {
            List<CfgFieldDO> fieldDOList = FieldConvert.fieldChangeListToDOList(fieldChangeList);
            fieldService.batchUpdateById(fieldDOList);
        }
        return flag1;
    }

    private void handleEventTrigger(UpdateTableDTO dto) {
        List<CfgEventTriggerDO> triggerDOList = triggerService.getDOListByTableId(dto.getId());
        Set<Long> oldEventIdSet = triggerDOList.stream().map(CfgEventTriggerDO::getEventId).collect(Collectors.toSet());

        List<TableEventTriggerDTO> eventList1 = dto.getRowEventList();
        List<TableEventTriggerDTO> eventList2 = dto.getLeftTableHeadEventList();
        List<TableEventTriggerDTO> eventList3 = dto.getRightTableHeadEventList();
        List<TableEventTriggerDTO> allEventList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(eventList1)) allEventList.addAll(eventList1);
        if (!CollectionUtils.isEmpty(eventList2)) allEventList.addAll(eventList2);
        if (!CollectionUtils.isEmpty(eventList3)) allEventList.addAll(eventList3);
        Set<Long> newEventIdSet = allEventList.stream().map(TableEventTriggerDTO::getEventId).collect(Collectors.toSet());

        HashSet<Long> set = new HashSet<>(oldEventIdSet);
        set.retainAll(newEventIdSet);

        for (CfgEventTriggerDO triggerDO : triggerDOList) {
            if (!set.contains(triggerDO.getEventId())) triggerService.deleteById(triggerDO.getId());
        }
        CfgTableDO tableDO = tableService.getDOById(dto.getId());
        for (TableEventTriggerDTO triggerDTO : allEventList) {
            CfgEventDO eventDO = eventService.getById(triggerDTO.getEventId());
            if (eventDO == null)
                throw new ServiceException(500, "目标事件不存在");

            if (set.contains(triggerDTO.getEventId())) continue;
            CfgEventTriggerDO triggerDO = new CfgEventTriggerDO();
            triggerDO.setPageId(tableDO.getPageId());
            triggerDO.setLabel(eventDO.getName());
            triggerDO.setStyle((byte) 0);
            triggerDO.setEventId(triggerDTO.getEventId());
            triggerDO.setDisplayType(TriggerStyleEnum.BUTTON.getCode());
            triggerDO.setOwner(triggerDTO.getOwner());
            triggerDO.setOwnerId(dto.getId());
            triggerDO.setDeleted(DeletedEnum.UNDELETED.getCode());
            triggerDO.setCreateTime(new Date());
            triggerService.add(triggerDO);
        }
    }

    public List<DataSummaryRule> getDataSummaryRuleByTableId(Long tableId) {
        CfgTableDO tableDO = tableService.getDOById(tableId);

        String dataSummaryRuleStr = tableDO.getDataSummaryRule();
        if (StringUtils.hasText(dataSummaryRuleStr)) {
            return tableDO.getDataSummaryRules();
        }
        return new ArrayList<>();
    }

    public List<FieldSimpleInfo> getDataSummaryField(Long tableId) {
        CfgTableDO tableDO = tableService.getDOById(tableId);
        List<Long> modelIds = tableDO.getModelIds();
        List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelIds(modelIds);

        List<FieldSimpleInfo> re = fieldDOList.stream().filter(cfgFieldDO ->
                        (ComponentTypeEnum.INPUT.getType().equals(cfgFieldDO.getComponentType()) ||
                                ComponentTypeEnum.INPUT_NUM.getType().equals(cfgFieldDO.getComponentType())))
                .map(cfgFieldDO -> {
                    FieldSimpleInfo field = new FieldSimpleInfo();
                    field.setId(cfgFieldDO.getId().toString());
                    field.setBizCode(cfgFieldDO.getBizCode());
                    field.setBizName(cfgFieldDO.getBizName());
                    return field;
                }).toList();
        return re;
    }

    public List<TableFieldVO> getFieldByTableId(Long tableId) {
        CfgTableDO tableDO = tableService.getDOById(tableId);
        List<Long> modelIds = tableDO.getModelIds();
        List<CfgFieldDO> fieldDOList = fieldService.getDOListByModelIds(modelIds);

        List<TableFieldVO> re = fieldDOList.stream().map(fieldDO -> {
            TableFieldVO field = new TableFieldVO();
            field.setFieldId(fieldDO.getId().toString());
            field.setFieldBizCode(fieldDO.getBizCode());
            field.setFieldBizName(fieldDO.getBizName());
            field.setComponentType(fieldDO.getComponentType());
            return field;
        }).toList();
        return re;
    }

    public boolean createAggregateRule(CreateAggregateRuleDTO dto) {
        Long tableId = dto.getTableId();
        CfgTableDO tableDO = tableService.getDOById(tableId);
        if (tableDO == null) throw new ServiceException(500, "目标表格不存在");

        List<GroupAggregateField> fieldList = dto.getGroupAggregateFieldList();

        List<GroupAggregateField> groupFieldList = fieldList.stream()
                .filter(i -> GroupAggregateFieldEnum.group.getCode() == i.getType()).toList();
        if (CollectionUtils.isEmpty(groupFieldList) || groupFieldList.size() > 1)
            throw new ServiceException(500, "分组字段有且只能一个");

        List<GroupAggregateField> aggregateFieldList = fieldList.stream()
                .filter(i -> GroupAggregateFieldEnum.Aggregate.getCode() == i.getType()).toList();

        List<GroupAggregateField> otherFieldList = fieldList.stream()
                .filter(i -> GroupAggregateFieldEnum.other.getCode() == i.getType()).toList();

        if (CollectionUtils.isEmpty(aggregateFieldList) && CollectionUtils.isEmpty(otherFieldList))
            throw new ServiceException(500, "聚合字段和其它展示字段都不存在");

        CfgTableDataGroupAggregateDO tableDataGroupDO = new CfgTableDataGroupAggregateDO();
        tableDataGroupDO.setTableId(tableId);
        tableDataGroupDO.setPageId(tableDO.getPageId());
        tableDataGroupDO.setName(dto.getAggregateRuleName());
        tableDataGroupDO.setGroupField(groupFieldList.get(0));
        tableDataGroupDO.setAggregateField(aggregateFieldList);
        tableDataGroupDO.setOtherField(otherFieldList);
        tableDataGroupDO.setCreateBy(tableDO.getCreateBy());
        tableDataGroupDO.setCreateTime(new Date());

        boolean flag = tableDataGroupAggregateService.add(tableDataGroupDO);
        return flag;
    }

    public List<AggregateRuleVO> getAggregateRule(Long tableId) {
        List<CfgTableDataGroupAggregateDO> tableDataGroupDOList = tableDataGroupAggregateService.getDOByTableId(tableId);

        List<AggregateRuleVO> res = getAggregateRuleVOList(tableDataGroupDOList);
        return res;
    }

    public List<AggregateRuleVO> getAggregateRuleVOList(List<CfgTableDataGroupAggregateDO> tableDataGroupDOList) {
        if (CollectionUtils.isEmpty(tableDataGroupDOList)) {
            return new ArrayList<>();
        }

        List<AggregateRuleVO> res = new ArrayList<>();
        for (CfgTableDataGroupAggregateDO tableDataGroupDO : tableDataGroupDOList) {
            List<GroupAggregateField> re = new ArrayList<>();
            re.add(tableDataGroupDO.getGroupField());
            re.addAll(tableDataGroupDO.getAggregateField());
            re.addAll(tableDataGroupDO.getOtherField());
            re.sort(Comparator.comparingInt(GroupAggregateField::getSort));

            AggregateRuleVO vo = new AggregateRuleVO();
            vo.setId(tableDataGroupDO.getId().toString());
            vo.setTableId(tableDataGroupDO.getTableId().toString());
            vo.setName(tableDataGroupDO.getName());
            vo.setFieldList(re);
            res.add(vo);
        }
        return res;
    }

    public boolean updateAggregateRule(UpdateAggregateRuleDTO dto) {
        List<GroupAggregateField> fieldList = dto.getGroupAggregateFieldList();

        List<GroupAggregateField> groupFieldList = fieldList.stream()
                .filter(i -> GroupAggregateFieldEnum.group.getCode() == i.getType()).toList();
        if (CollectionUtils.isEmpty(groupFieldList) || groupFieldList.size() > 1)
            throw new ServiceException(500, "分组字段有且只能一个");

        List<GroupAggregateField> aggregateFieldList = fieldList.stream()
                .filter(i -> GroupAggregateFieldEnum.Aggregate.getCode() == i.getType()).toList();

        List<GroupAggregateField> otherFieldList = fieldList.stream()
                .filter(i -> GroupAggregateFieldEnum.other.getCode() == i.getType()).toList();

        if (CollectionUtils.isEmpty(aggregateFieldList) && CollectionUtils.isEmpty(otherFieldList))
            throw new ServiceException(500, "聚合字段和其它展示字段都不存在");

        CfgTableDataGroupAggregateDO tableDataGroupDO = new CfgTableDataGroupAggregateDO();
        tableDataGroupDO.setId(dto.getId());
        tableDataGroupDO.setName(dto.getAggregateRuleName());
        tableDataGroupDO.setGroupFieldInfo(JSON.toJSONString(groupFieldList.get(0)));
        tableDataGroupDO.setAggregateFieldInfo(JSON.toJSONString(aggregateFieldList));
        tableDataGroupDO.setOtherFieldInfo(JSON.toJSONString(otherFieldList));
        tableDataGroupDO.setUpdateBy(null);
        tableDataGroupDO.setUpdateTime(new Date());
        boolean flag = tableDataGroupAggregateService.updateById(tableDataGroupDO);
        return flag;
    }

    public boolean deleteAggregateRule(Long aggregateRuledId) {
        return tableDataGroupAggregateService.deleteById(aggregateRuledId);
    }

    public List<TableVO1> getByFieldId(Long fieldId) {
        CfgFieldDO fieldDO = fieldService.getDOById(fieldId);
        if (fieldDO == null || fieldDO.getPageId() == null) {
            throw new ServiceException(500, "目标字段不存在或查询对应pageId失败");
        }

        List<CfgTableDO> tableDOList = tableService.getDOListByPageId(fieldDO.getPageId());
        List<TableVO1> voList = tableDOList.stream().map(tableDO -> new TableVO1(tableDO.getId().toString(), tableDO.getName())).toList();
        return voList;
    }

    public List<TableVO1> getByPageCode(String pageCode, String bizIdentityCode) {
        CfgPageDO pageDO = pageService.getByPageCodeAndIdentityCode(pageCode, bizIdentityCode);
        if (pageDO == null) {
            throw new ServiceException(500, "目标页面不存在,pageCode:" + pageCode + ", bizIdentityCode:" + bizIdentityCode);
        }

        List<CfgTableDO> tableDOList = tableService.getDOListByPageId(pageDO.getId());
        return tableDOList.stream().map(tableDO -> {
            TableVO1 vo = new TableVO1();
            vo.setId(tableDO.getId().toString());
            vo.setTableName(tableDO.getName());
            return vo;
        }).toList();
    }
}
