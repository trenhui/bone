import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { Table, Button, Space, Input, Select, message, Modal, Popconfirm, Spin, Empty, Tooltip } from 'antd';
import { SearchOutlined, PlusOutlined, EditOutlined, DeleteOutlined, CopyOutlined, ExclamationCircleOutlined, DownOutlined, LoadingOutlined } from '@ant-design/icons';
import { useScopeData } from '../../hooks/useScopeData';
import { useDataBinding } from '../../hooks/useDataBinding';
import { useTableSave } from '../../hooks/useTableSave';
import { useTableLinkageWatcher } from '../../hooks/useTableLinkageWatcher';
import { useTableCrossLinkageWatcher } from '../../hooks/useTableCrossLinkageWatcher';
import { useTableSubmitRowRule } from '../../hooks/useTableSubmitRowRule';
import { useTableSubmitCrossTableRule } from '../../hooks/useTableSubmitCrossTableRule';
import { useSummary } from '../../hooks/useSummary';
import { useSelectDropFilter } from '../../hooks/useSelectDropFilter';
import DataAPI from '@/api/data';
import { getValueByJsonPath } from '@/utils/jsonpathUtils';
import { cloneDeep, isEmpty } from 'lodash-es';
import EventBus from '@/utils/eventBus';
import { capitalizeFirstLetter } from '@/utils/strUtils';

// Enums
import { OperationEnabledEnum } from '@/enums/table/OperationEnabledEnum';
import { OperationFixedEnum } from '@/enums/table/OperationFixedEnum';
import { SummaryRowStatusEnum } from '@/enums/table/SummaryRowStatusEnum';
import { OrderColumnEnableEnum } from '@/enums/table/OrderColumnEnableEnum';
import { EventOwnerEnum } from '@/enums/event/EventOwnerEnum';
import { PrepareEventCodeEnum, isPrepareEvent } from '@/enums/event/PrepareEventCodeEnum';
import { DisplayModeEnum } from '@/enums/DisplayModeEnum';
import { GroupingAggregateStatusEnum } from '@/enums/table/GroupingAggregateStatusEnum';
import { RuleVerifyTypeEnum, getRuleVerifyTypeShortLabel } from '@/enums/rule/RuleVerifyTypeEnum';
import { TableDisplayEnum } from '@/enums/table/TableDisplayEnum';
import { RequiredEnum } from '@/enums';

// Components
import TableEditableCell from './components/TableEditableCell';
import TableDetailDrawer from './components/DetailDrawer';
import TableSearch from './components/TableSearch';
import GroupingAggregateTable from './components/GroupingAggregateTable';
import BatchEditDialog from './components/BatchEditDialog';
import CustomContent from './components/CustomContent';
import ButtonGroup from '../ButtonGroup';

interface TableProps {
  id: string;
  name: string;
  body: any[];
  parentTableId?: string;
  initApiParam?: string;
  modelCodeList: string[];
  operationColumnEnabled: OperationEnabledEnum;
  operationColumnFixed: OperationFixedEnum;
  paginationEnabled: number;
  displayTotalSize: number;
  defaultPageSize?: number;
  enableDataSummary: SummaryRowStatusEnum;
  enableDataAggregate: GroupingAggregateStatusEnum;
  enableSearch: boolean;
  searchFieldList: any[];
  enableOrderColumn: OrderColumnEnableEnum;
  fieldSortTypeList?: any[];
  leftFixed: number;
  rightFixed: number;
  fillScreen: number;
  requiredData: RequiredEnum;
  eventTriggerList?: any[];
  editableColumnList?: any[];
  emptyPrompt?: string;
  prompt?: string;
}

const TableComponent: React.FC<TableProps> = (props) => {
  const scopeData = useScopeData();
  const modalManager = scopeData.getData('modalManager');
  const displayMode = scopeData.getData('displayMode');
  const updateSchema = scopeData.getData('updateSchema');
  const tableManager = scopeData.getData('tableManager');
  const dataManager = scopeData.getData('dataManager');
  const componentManager = scopeData.getData('componentManager');
  const validateManager = scopeData.getData('validateManager');
  const rulesManager = scopeData.getData('rulesManager');
  const tenantId = scopeData.getData('tenantId');
  const bizIdentityCode = scopeData.getData('bizIdentityCode');
  const tabConditionParams = scopeData.getData('tableCondition') || [];

  // 状态管理
  const [tableData, setTableData] = useState<any[]>([]);
  const [selectedRow, setSelectedRow] = useState<any[]>([]);
  const [groupingAggregateData, setGroupingAggregateData] = useState<any[]>([]);
  const [currentIndex, setCurrentIndex] = useState(-1);
  const [tableLoading, setTableLoading] = useState(false);
  const [tableRules, setTableRules] = useState<any>(null);
  const [foreignFieldValue, setForeignFieldValue] = useState('');
  const [customContentRefresh, setCustomContentRefresh] = useState(false);
  const [isExpand, setIsExpand] = useState(true);
  const [saveLoading, setSaveLoading] = useState(false);
  const [currentEditingIndexSet, setCurrentEditingIndexSet] = useState<Set<number>>(new Set());
  const [uneditedTableRowMap, setUneditedTableRowMap] = useState<Map<number, any>>(new Map());

  // 分页参数
  const [pagingParam, setPagingParam] = useState({
    pageNo: 1,
    pageSize: props.defaultPageSize || 5,
    totalCount: 0,
    totalPage: 0,
  });

  // 搜索参数
  const [searchParams, setSearchParams] = useState<any[]>([]);

  // 抽屉状态
  const [detailDrawer, setDetailDrawer] = useState({
    visible: false,
    params: {
      tableName: '',
      tableIndex: -1,
      body: [] as any[],
      tableId: '',
      isCreate: false,
      tableRules: {} as any,
      singleEditableColumnList: [] as any[],
      saveInfo: {} as any,
      editable: true,
      copyData: {} as any,
    },
  });

  const [batchEditDialog, setBatchEditDialog] = useState({
    visible: false,
    params: {
      selectedRow: [] as any[],
      batchEditFieldList: [] as any[],
      tableBody: [] as any[],
      saveInfo: {} as any,
    },
  });

  // 计算属性
  const isDisplay = useMemo(() => {
    return true; // 需要实现useBaseComponentProperty
  }, []);

  const isSupportConfig = useMemo(() => {
    return displayMode === DisplayModeEnum.CONFIG;
  }, [displayMode]);

  const isSupportPreview = useMemo(() => {
    return displayMode === DisplayModeEnum.PREVIEW;
  }, [displayMode]);

  const isSupportEdit = useMemo(() => {
    return displayMode === DisplayModeEnum.EDIT;
  }, [displayMode]);

  const isMainTable = useMemo(() => {
    return !props.parentTableId;
  }, [props.parentTableId]);

  const isShowTable = useMemo(() => {
    if (isSupportConfig) return true;
    if (!isDisplay) return false;
    if (isSupportPreview) return true;
    if (isMainTable) return true;
    if (foreignFieldName && foreignFieldValue) return true;
    return false;
  }, [isSupportConfig, isDisplay, isSupportPreview, isMainTable, foreignFieldName, foreignFieldValue]);

  const isShowOperate = useMemo(() => {
    return props.operationColumnEnabled === OperationEnabledEnum.ENABLED;
  }, [props.operationColumnEnabled]);

  const fixedOperatePosition = useMemo(() => {
    return props.operationColumnFixed === OperationFixedEnum.FIXED ? 'right' : false;
  }, [props.operationColumnFixed]);

  const isShowPagination = useMemo(() => {
    return props.paginationEnabled === 1;
  }, [props.paginationEnabled]);

  const isShowTotalSize = useMemo(() => {
    return props.displayTotalSize === 1;
  }, [props.displayTotalSize]);

  const isShowSummary = useMemo(() => {
    return props.enableDataSummary === SummaryRowStatusEnum.OPENED && !isSupportConfig;
  }, [props.enableDataSummary, isSupportConfig]);

  const isShowGroupingAggregate = useMemo(() => {
    return props.enableDataAggregate === GroupingAggregateStatusEnum.OPENED && !isSupportConfig;
  }, [props.enableDataAggregate, isSupportConfig]);

  const isShowSearch = useMemo(() => {
    return props.enableSearch && !isEmpty(props.searchFieldList) && !isSupportConfig;
  }, [props.enableSearch, props.searchFieldList, isSupportConfig]);

  const isShowIndex = useMemo(() => {
    return props.enableOrderColumn === OrderColumnEnableEnum.ENABLE;
  }, [props.enableOrderColumn]);

  const foreignFieldName = useMemo(() => {
    try {
      if (!props.initApiParam || props.initApiParam === '') {
        return null;
      }
      return JSON.parse(props.initApiParam)?.fieldName;
    } catch (error) {
      console.error('解析 initApiParam 出错:', error);
      return null;
    }
  }, [props.initApiParam]);

  const rowEventList = useMemo(() => {
    return props.eventTriggerList?.filter(
      (item) =>
        item.owner === EventOwnerEnum.TableRow &&
        item.prepare !== 1 &&
        !isPrepareEvent(item.eventCode)
    ) || [];
  }, [props.eventTriggerList]);

  const leftHeaderEventList = useMemo(() => {
    return props.eventTriggerList?.filter(
      (item) =>
        item.owner === EventOwnerEnum.TableLeftHeader &&
        item.prepare !== 1 &&
        !isPrepareEvent(item.eventCode)
    ) || [];
  }, [props.eventTriggerList]);

  const rightHeaderEventList = useMemo(() => {
    return props.eventTriggerList?.filter(
      (item) =>
        item.owner === EventOwnerEnum.TableRightHeader &&
        item.prepare !== 1 &&
        !isPrepareEvent(item.eventCode)
    ) || [];
  }, [props.eventTriggerList]);

  const singleEditableColumnList = useMemo(() => {
    return props.editableColumnList
      ?.filter((item) => item.singleLineEditable)
      ?.map((item) => item.field.id) || [];
  }, [props.editableColumnList]);

  const batchEditableColumnList = useMemo(() => {
    return props.editableColumnList
      ?.filter((item) => item.batchEditable)
      ?.map((item) => item.field.id) || [];
  }, [props.editableColumnList]);

  const fillScreen = useMemo(() => {
    return props.fillScreen === 1;
  }, [props.fillScreen]);

  const isShowCustomContent = useMemo(() => {
    return !isSupportConfig && props.modelCodeList.includes('claimInvoice');
  }, [isSupportConfig, props.modelCodeList]);

  const isShowReport = useMemo(() => {
    return !isSupportConfig && props.modelCodeList.includes('adjustmentDetail');
  }, [isSupportConfig, props.modelCodeList]);

  const isCollectPersonInfo = useMemo(() => {
    return !isSupportConfig && props.modelCodeList.includes('collectPerson');
  }, [isSupportConfig, props.modelCodeList]);

  const isRequired = useMemo(() => {
    return props.requiredData === RequiredEnum.required;
  }, [props.requiredData]);

  const isShowPolicyBind = useMemo(() => {
    return props.modelCodeList.includes('policyInfoModel');
  }, [props.modelCodeList]);

  // 筛选功能
  const { shouldEnableFilter, getFilterOptions, handleColumnFilter } = useSelectDropFilter({
    tableData,
    columns: props.body,
    tableId: props.id,
    tableManager,
    isConfigMode: isSupportConfig,
  });

  // 表格保存方法
  const { save, batchSave } = useTableSave();

  // 汇总行处理
  const { setSummary, initSummaryRowData, summaryRowData } = useSummary(componentManager);
  useEffect(() => {
    if (isShowSummary && !isSupportConfig) {
      initSummaryRowData(rulesManager.get(10, props.id));
    }
  }, [isShowSummary, isSupportConfig, rulesManager, props.id]);

  // 分组聚合表格处理
  useEffect(() => {
    if (isShowGroupingAggregate && !isSupportConfig) {
      setGroupingAggregateData(rulesManager.get(9, props.id) || []);
    }
  }, [isShowGroupingAggregate, isSupportConfig, rulesManager, props.id]);

  // 表格规则初始化
  useEffect(() => {
    const initTableRules = async () => {
      if (isSupportEdit || isSupportPreview) {
        const rules = {
          linkageRowRule: rulesManager.get(5, props.id),
          submitRowRule: rulesManager.get(6, props.id),
          linkageCrossTableRule: rulesManager.get(7, props.id),
          submitCrossTableRule: rulesManager.get(8, props.id),
        };
        setTableRules(rules);

        if (!isEmpty(rules.submitCrossTableRule)) {
          validateManager.push(checkTableSubmitCrossTableRule);
        }

        if (isRequired) {
          validateManager.push(checkTableIsEmpty);
        }

        // 初始化联动监听器
        // tableLinkageWatcher.init();
        // crossLinkageWatcher.init();
      }
    };
    initTableRules();
  }, [isSupportEdit, isSupportPreview, rulesManager, props.id, validateManager, isRequired]);

  // 初始化表格数据
  const initTableData = useCallback(async () => {
    if (isSupportConfig) return;

    if (tableLoading) return;

    if (isSupportPreview) {
      tableManager.set(props.id, []);
      setTableData(tableManager.get(props.id));
      return;
    }

    const initParams = {
      bizIdentityCode,
      tenantId,
      queryParams: [],
      modelNames: props.modelCodeList,
    };

    if (foreignFieldName) {
      if (foreignFieldValue) {
        initParams[foreignFieldName] = foreignFieldValue;
      } else {
        return;
      }
    }

    if (!isEmpty(props.fieldSortTypeList)) {
      initParams.sortingFields = props.fieldSortTypeList;
    }

    if (!isEmpty(searchParams)) {
      initParams.queryParams = searchParams;
    }

    if (!isEmpty(tabConditionParams)) {
      initParams.queryParams.push(...tabConditionParams);
    }

    if (isShowPagination) {
      initParams.pageNo = pagingParam.pageNo;
      initParams.pageSize = pagingParam.pageSize;
    } else {
      initParams.pageNo = -1;
      initParams.pageSize = -1;
    }

    setTableLoading(true);
    try {
      const res = await DataAPI.getList(initParams);
      tableManager.set(props.id, res.data);
      setTableData(tableManager.get(props.id));

      setPagingParam({
        ...pagingParam,
        totalCount: res.totalCount,
        totalPage: res.totalPage,
        pageNo: res.currPage,
        pageSize: res.pageSize,
      });

      initTableSaveInfo();
      initTableCurrentIndex();
    } catch (error) {
      console.error('获取表格数据失败', error.message);
      message.error('获取表格数据失败');
    } finally {
      setTableLoading(false);
    }
  }, [isSupportConfig, isSupportPreview, foreignFieldName, foreignFieldValue, props.fieldSortTypeList, searchParams, tabConditionParams, isShowPagination, pagingParam, props.modelCodeList, props.id, bizIdentityCode, tenantId, tableManager, tableLoading]);

  // 初始化表格保存信息
  const initTableSaveInfo = useCallback(() => {
    const saveInfo = {
      params: {
        bizIdentityCode,
        tenantId,
        [`related${capitalizeFirstLetter(foreignFieldName)}`]: foreignFieldValue,
      },
      modelNames: props.modelCodeList,
    };
    return saveInfo;
  }, [bizIdentityCode, tenantId, foreignFieldName, foreignFieldValue, props.modelCodeList]);

  // 初始化表格当前行
  const initTableCurrentIndex = useCallback(() => {
    if (currentIndex !== -1 && currentIndex < tableData.length) {
      EventBus.emit(`table:${props.id}:currentChange`, {
        currentRow: tableData[currentIndex],
      });
    } else {
      const newIndex = tableData.length > 0 ? 0 : -1;
      setCurrentIndex(newIndex);
      if (newIndex !== -1) {
        EventBus.emit(`table:${props.id}:currentChange`, {
          currentRow: tableData[newIndex],
        });
      }
    }
  }, [currentIndex, tableData, props.id]);

  // 处理父表格当前行联动变化
  const handleParentTableCurrentChange = useCallback((data) => {
    try {
      const currentForeignFieldValue = getValueByJsonPath(
        data.currentRow,
        JSON.parse(props.initApiParam)?.dataBinding
      );
      if (foreignFieldValue !== currentForeignFieldValue) {
        setForeignFieldValue(currentForeignFieldValue);
      }
    } catch (error) {
      console.error('处理父表格联动变化失败:', error);
    }
  }, [props.initApiParam, foreignFieldValue]);

  // 处理行点击
  const handleRowClick = useCallback((row) => {
    EventBus.emit(`table:${props.id}:currentChange`, {
      currentRow: row,
    });
    const index = tableData.indexOf(row);
    setCurrentIndex(index);
  }, [tableData, props.id]);

  // 处理行双击编辑
  const handleRowDblClick = useCallback((row) => {
    if ((isSupportEdit || isSupportPreview) && !isEmpty(singleEditableColumnList)) {
      handleRowQuickEdit(row);
    }
  }, [isSupportEdit, isSupportPreview, singleEditableColumnList]);

  // 处理分页大小变化
  const handleSizeChange = useCallback((pageSize) => {
    setPagingParam(prev => ({
      ...prev,
      pageSize,
    }));
    initTableData();
  }, [initTableData]);

  // 处理页码变化
  const handleCurrentChange = useCallback((pageNo) => {
    setPagingParam(prev => ({
      ...prev,
      pageNo,
    }));
    initTableData();
  }, [initTableData]);

  // 处理选择变化
  const handleSelectionChange = useCallback((selection) => {
    if (isShowPolicyBind) {
      if (selection.length > 1) {
        const newSelection = [selection[selection.length - 1]];
        setSelectedRow(newSelection);
        return;
      }
      setSelectedRow(selection);
      return;
    }

    const newSelectedRow = selection.map((selectedItem) => {
      const index = tableData.findIndex((item) => item === selectedItem);

      if (isShowPagination) {
        const pagingIndex = index + (pagingParam.pageNo - 1) * pagingParam.pageSize;
        return {
          ...selectedItem,
          index: pagingIndex,
        };
      }
      return { ...selectedItem, index };
    });

    setSelectedRow(newSelectedRow);
  }, [isShowPolicyBind, tableData, isShowPagination, pagingParam]);

  // 处理批量保存
  const handleBatchSave = useCallback(async (rows) => {
    const saveInfo = initTableSaveInfo();
    await batchSave(saveInfo, rows);
    initTableData();
    if (props.parentTableId) {
      EventBus.emit(`table:${props.parentTableId}:refresh`);
    }
  }, [initTableSaveInfo, batchSave, initTableData, props.parentTableId]);

  // 处理保存
  const handleSave = useCallback(async (row) => {
    const saveInfo = initTableSaveInfo();
    await save(saveInfo, row);
    if (props.parentTableId) {
      EventBus.emit(`table:${props.parentTableId}:refresh`);
    }
  }, [initTableSaveInfo, save, props.parentTableId]);

  // 切换行编辑状态
  const handleRowQuickEdit = useCallback((row) => {
    const index = tableData.indexOf(row);
    if (currentEditingIndexSet.has(index)) {
      return;
    }

    if (currentEditingIndexSet.size > 0) {
      message.warning('请先完成当前编辑的内容');
      return;
    }

    const newEditingSet = new Set(currentEditingIndexSet);
    newEditingSet.add(index);
    setCurrentEditingIndexSet(newEditingSet);

    const newUneditedMap = new Map(uneditedTableRowMap);
    newUneditedMap.set(index, cloneDeep(row));
    setUneditedTableRowMap(newUneditedMap);
  }, [tableData, currentEditingIndexSet, uneditedTableRowMap]);

  // 设置单元格编辑状态
  const setCellEditStatus = useCallback((cellItem, index) => {
    if (isEmpty(singleEditableColumnList)) return false;
    return singleEditableColumnList.includes(cellItem.id) && currentEditingIndexSet.has(index);
  }, [singleEditableColumnList, currentEditingIndexSet]);

  // 检查是否可以保存
  const isAvailableSave = useCallback(async (row, index) => {
    const result = {
      success: true,
      messages: [] as string[],
    };

    // 行内保存规则校验
    if (tableRules?.submitRowRule?.length > 0) {
      const { success, messages } = useTableSubmitRowRule(
        tableRules.submitRowRule,
        row,
        componentManager
      );
      result.success = result.success && success;
      if (messages) {
        result.messages.push(...messages);
      }
    }

    if (result.success) return true;

    const isStopSave = result.messages.some((message) =>
      message.includes(`【${getRuleVerifyTypeShortLabel(RuleVerifyTypeEnum.STRONG_VERIFY)}】`)
    );

    return new Promise((resolve) => {
      Modal.confirm({
        title: '校验失败',
        content: `错误信息如下:<br>${result.messages.join('<br>')}`,
        dangerouslyUseHTMLString: true,
        okText: isStopSave ? '确定' : '忽略并继续提交',
        cancelText: '取消',
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
  }, [tableRules, componentManager]);

  // 处理编辑保存
  const handleEditSave = useCallback(async (row, index) => {
    if (!(await isAvailableSave(row, index))) return;

    try {
      setSaveLoading(true);
      await handleSave(row);

      const newEditingSet = new Set(currentEditingIndexSet);
      newEditingSet.delete(index);
      setCurrentEditingIndexSet(newEditingSet);

      const newUneditedMap = new Map(uneditedTableRowMap);
      newUneditedMap.delete(index);
      setUneditedTableRowMap(newUneditedMap);

      initTableData();
    } catch (error) {
      console.error(error);
      message.error('保存失败');
    } finally {
      setSaveLoading(false);
    }
  }, [isAvailableSave, handleSave, currentEditingIndexSet, uneditedTableRowMap, initTableData]);

  // 放弃编辑
  const handleEditCancel = useCallback((index) => {
    const newEditingSet = new Set(currentEditingIndexSet);
    newEditingSet.delete(index);
    setCurrentEditingIndexSet(newEditingSet);

    const originalRow = uneditedTableRowMap.get(index);
    if (originalRow) {
      const newTableData = [...tableData];
      newTableData[index] = originalRow;
      setTableData(newTableData);
      tableManager.setRow(props.id, index, originalRow);
    }

    const newUneditedMap = new Map(uneditedTableRowMap);
    newUneditedMap.delete(index);
    setUneditedTableRowMap(newUneditedMap);
  }, [currentEditingIndexSet, uneditedTableRowMap, tableData, tableManager, props.id]);

  // 打开配置抽屉
  const handleConfig = useCallback(() => {
    modalManager.showModal(
      'ConfigureTableDrawer',
      { id: props.id },
      (isChange) => {
        if (isChange) {
          updateSchema(DisplayModeEnum.CONFIG);
        }
      }
    );
  }, [modalManager, props.id, updateSchema]);

  // 显示详情抽屉
  const showDetailDrawer = useCallback((index, isCreate = false, isEditable = true, copyData = {}) => {
    setDetailDrawer({
      visible: true,
      params: {
        tableName: props.name,
        tableIndex: index,
        body: props.body,
        tableId: props.id,
        tableRules: tableRules || {},
        saveInfo: initTableSaveInfo(),
        singleEditableColumnList: singleEditableColumnList,
        isCreate,
        editable: isEditable && (isSupportEdit || isSupportPreview),
        copyData,
      },
    });
  }, [props.name, props.body, props.id, tableRules, initTableSaveInfo, singleEditableColumnList, isSupportEdit, isSupportPreview]);

  // 校验表格间保存规则
  const checkTableSubmitCrossTableRule = useCallback(() => {
    if (isEmpty(tableRules?.submitCrossTableRule)) return { success: true };

    return useTableSubmitCrossTableRule(
      tableRules.submitCrossTableRule,
      tableManager,
      componentManager
    );
  }, [tableRules, tableManager, componentManager]);

  // 检查表格是否为空
  const checkTableIsEmpty = useCallback(() => {
    if (isDisplay && isEmpty(tableData)) {
      return {
        success: false,
        messages: [
          `【${getRuleVerifyTypeShortLabel(RuleVerifyTypeEnum.STRONG_VERIFY)}】${props.name}至少需要填写一条数据`,
        ],
      };
    } else {
      return { success: true };
    }
  }, [isDisplay, tableData, props.name]);

  // 处理搜索
  const handleSearch = useCallback((params) => {
    setSearchParams(params);
    setPagingParam(prev => ({
      ...prev,
      pageNo: 1,
    }));
    initTableData();
  }, [initTableData]);

  // 是否显示预设事件
  const isShowPrepareEvent = useCallback((code) => {
    return props.eventTriggerList?.some((item) => item.eventCode == code);
  }, [props.eventTriggerList]);

  // 左表头预设事件实现
  const handleAdd = useCallback(() => {
    if (currentEditingIndexSet.size > 0) {
      message.warning('请先完成当前编辑的内容');
      return;
    }
    showDetailDrawer(tableData.length, true, true);
  }, [currentEditingIndexSet, tableData.length, showDetailDrawer]);

  const handleCopy = useCallback(() => {
    if (currentEditingIndexSet.size > 0) {
      message.warning('请先完成当前编辑的内容');
      return;
    }

    if (!selectedRow.length || selectedRow.length > 1) {
      message.error('请选择一行进行复制');
      return;
    }

    const cleanData = cloneDeep(selectedRow[0]);
    if (cleanData.main) {
      delete cleanData.main.id;
    }

    showDetailDrawer(tableData.length, true, true, cleanData);
  }, [currentEditingIndexSet, selectedRow, tableData.length, showDetailDrawer]);

  const handleBatchEdit = useCallback(() => {
    if (currentEditingIndexSet.size > 0) {
      message.warning('请先完成当前编辑的内容');
      return;
    }

    if (!selectedRow.length) {
      message.error('请选择要编辑的行');
      return;
    }

    setBatchEditDialog({
      visible: true,
      params: {
        selectedRow: selectedRow,
        batchEditFieldList: batchEditableColumnList,
        tableBody: props.body,
        saveInfo: initTableSaveInfo(),
      },
    });
  }, [currentEditingIndexSet, selectedRow, batchEditableColumnList, props.body, initTableSaveInfo]);

  const handleBatchDelete = useCallback(() => {
    if (currentEditingIndexSet.size > 0) {
      message.warning('请先完成当前编辑的内容');
      return;
    }

    if (!selectedRow.length) {
      message.error('请选择要删除的行');
      return;
    }

    Modal.confirm({
      title: '提示',
      content: `确定删除以下选中行？<br>序号 ${selectedRow.map((item) => item.index + 1).join('，')}`,
      dangerouslyUseHTMLString: true,
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          await DataAPI.deleteListRow({
            idList: selectedRow.map((item) => item.main.id),
            modelNames: props.modelCodeList[0],
          });
          message.success('删除成功');
          initTableData();
          if (props.parentTableId) {
            EventBus.emit(`table:${props.parentTableId}:refresh`);
          }
        } catch (error) {
          console.error(error);
          message.error('删除失败');
        }
      },
    });
  }, [currentEditingIndexSet, selectedRow, props.modelCodeList, initTableData, props.parentTableId]);

  // 行预设事件实现
  const handleDetail = useCallback((index) => {
    showDetailDrawer(index, false, false);
  }, [showDetailDrawer]);

  const handleUpdate = useCallback((index) => {
    if (currentEditingIndexSet.size > 0) {
      message.warning('请先完成当前编辑的内容');
      return;
    }
    showDetailDrawer(index, false, true);
  }, [currentEditingIndexSet, showDetailDrawer]);

  const handleDelete = useCallback((index) => {
    Modal.confirm({
      title: '提示',
      content: '确定删除？',
      okText: '确定',
      cancelText: '取消',
      onOk: async () => {
        try {
          await DataAPI.deleteListRow({
            idList: [tableData[index].main.id],
            modelNames: props.modelCodeList[0],
          });
          message.success('删除成功');
          initTableData();
          if (props.parentTableId) {
            EventBus.emit(`table:${props.parentTableId}:refresh`);
          }
        } catch (error) {
          console.error(error);
          message.error('删除失败');
        }
      },
    });
  }, [tableData, props.modelCodeList, initTableData, props.parentTableId]);

  // 重置搜索栏
  const clearSearch = useCallback(() => {
    // 实现重置搜索栏逻辑
  }, []);

  // 刷新表格
  const refreshTable = useCallback(() => {
    setPagingParam(prev => ({
      ...prev,
      pageNo: 1,
    }));
    clearSearch();
    initTableData();
  }, [clearSearch, initTableData]);

  // 获取列的固定位置
  const getFixedPosition = useCallback((index) => {
    if (index < props.leftFixed) {
      return 'left';
    } else if (index >= props.body.length - props.rightFixed) {
      return 'right';
    }
    return false;
  }, [props.leftFixed, props.body.length, props.rightFixed]);

  // 获取列的宽度
  const getColumnWidth = useCallback((width) => {
    if (width) {
      return width > 50 ? width : 100;
    }
    return 100;
  }, []);

  // 获取行在原始数据中的索引
  const getOriginalRowIndex = useCallback((row) => {
    let originalIndex = tableData.indexOf(row);
    if (originalIndex === -1 && row?.main?.id) {
      originalIndex = tableData.findIndex(
        (item) => item?.main?.id === row.main.id
      );
    }
    return originalIndex;
  }, [tableData]);

  // 初始化
  useEffect(() => {
    if (isSupportPreview) {
      initTableData();
    }

    if (props.initApiParam && !isEmpty(props.initApiParam)) {
      const unwatch = EventBus.on(`table:${props.parentTableId}:currentChange`, handleParentTableCurrentChange);
      return () => unwatch();
    } else if (!props.initApiParam || isEmpty(props.initApiParam)) {
      initTableData();
    } else if (isMainTable) {
      try {
        const dataBinding = JSON.parse(props.initApiParam)?.dataBinding;
        const value = scopeData.getData(dataBinding);
        if (foreignFieldValue !== value) {
          setForeignFieldValue(value);
        }
      } catch (error) {
        console.error('初始化外键值失败:', error);
      }
    }
  }, [props.initApiParam, props.parentTableId, isSupportPreview, isMainTable, foreignFieldValue, scopeData, handleParentTableCurrentChange, initTableData]);

  // 监听外键值变化
  useEffect(() => {
    if (foreignFieldValue) {
      initTableData();
    }
  }, [foreignFieldValue, initTableData]);

  // 注册事件监听
  useEffect(() => {
    const listeners = [
      EventBus.on(`table:${props.id}:linkageChange`, handleBatchSave),
      EventBus.on(`table:${props.id}:refresh`, initTableData),
    ];

    if (isCollectPersonInfo) {
      listeners.push(
        EventBus.on(`claim:importCollectPersonInfo`, (data) => {
          if ((isSupportEdit || isSupportPreview) && !isEmpty(singleEditableColumnList)) {
            if (tableData[0]) {
              if (currentEditingIndexSet.size > 0 && !currentEditingIndexSet.has(0)) {
                message.warning('请先完成当前编辑的内容');
                return;
              }
              handleRowQuickEdit(tableData[0]);
              tableManager.setRow(props.id, 0, {
                ...tableData[0],
                main: {
                  ...tableData[0].main,
                  collectName: data.collectName || tableData[0].main?.collectName || '',
                  collectIdentityNo: data.collectIdNumber || tableData[0].main?.collectIdentityNo || '',
                  collectIdentityDatePeriod: data.collectIdValidityPeriod.join(',') || tableData[0].main?.collectIdentityDatePeriod || '',
                  collectPhone: data.collectContact || tableData[0].main?.collectPhone || '',
                  collectContactAddress: data.collectAddress || tableData[0].main?.collectContactAddress || '',
                  personAccountNo: data.collectBankAccount || tableData[0].main?.personAccountNo || '',
                },
              });
            } else {
              showDetailDrawer(tableData.length, true, true, {
                main: {
                  collectName: data.collectName,
                  collectIdentityNo: data.collectIdNumber,
                  collectIdentityDatePeriod: data.collectIdValidityPeriod.join(','),
                  collectPhone: data.collectContact,
                  collectContactAddress: data.collectAddress,
                  personAccountNo: data.collectBankAccount,
                },
              });
            }
            message.success('内容导入成功但未保存，请检查后再保存信息');
          }
        })
      );
    }

    return () => {
      listeners.forEach(unwatch => unwatch());
    };
  }, [props.id, handleBatchSave, initTableData, isCollectPersonInfo, isSupportEdit, isSupportPreview, singleEditableColumnList, tableData, currentEditingIndexSet, handleRowQuickEdit, tableManager, showDetailDrawer]);

  // 设置scopeData
  useEffect(() => {
    scopeData.setData('refreshTable', refreshTable);
    scopeData.setData('tableSelectRows', selectedRow);
    scopeData.setData('tableData', tableData);
    scopeData.setData('customContentRefresh', () => {
      setCustomContentRefresh(true);
    });
  }, [scopeData, refreshTable, selectedRow, tableData]);

  // 表格列定义
  const columns = useMemo(() => {
    const cols = [];

    // 序号列
    if (isShowIndex) {
      cols.push({
        type: 'selection',
        align: 'center',
        width: 60,
        fixed: 'left',
      });
      cols.push({
        title: '序号',
        align: 'center',
        width: 60,
        fixed: 'left',
        render: (_, record, index) => {
          const originalIndex = getOriginalRowIndex(record);
          if (isShowPagination) {
            return originalIndex + 1 + (pagingParam.pageNo - 1) * pagingParam.pageSize;
          }
          return originalIndex + 1;
        },
      });
    }

    // 数据列
    props.body.forEach((item, index) => {
      const fixed = getFixedPosition(index);
      cols.push({
        title: (
          <>
            {item.required === 1 && <span style={{ color: 'red' }}>*</span>}
            <span>{item.showName}</span>
          </>
        ),
        dataIndex: `main.${item.id}`,
        key: item.id,
        width: getColumnWidth(item.width),
        align: 'center',
        fixed: fixed,
        filters: shouldEnableFilter(item) ? getFilterOptions(item) : undefined,
        onFilter: shouldEnableFilter(item) ? (value, row) => handleColumnFilter(value, row, item) : undefined,
        render: (_, record) => {
          const originalIndex = getOriginalRowIndex(record);
          return (
            <TableEditableCell
              tableItem={item}
              tableIndex={originalIndex}
              tableRowId={record.main?.id}
              tableId={props.id}
              isEditing={setCellEditStatus(item, originalIndex)}
            />
          );
        },
      });
    });

    // 操作列
    if (isShowOperate) {
      cols.push({
        title: '操作',
        align: 'center',
        width: 180,
        fixed: fixedOperatePosition,
        render: (_, record) => {
          const originalIndex = getOriginalRowIndex(record);
          const isEditing = currentEditingIndexSet.has(originalIndex);

          if (isEditing) {
            return (
              <Space>
                <Button
                  type="primary"
                  loading={saveLoading}
                  onClick={() => handleEditSave(record, originalIndex)}
                >
                  保存
                </Button>
                <Button
                  type="default"
                  onClick={() => handleEditCancel(originalIndex)}
                >
                  放弃
                </Button>
              </Space>
            );
          }

          return (
            <Space>
              {isShowPrepareEvent(PrepareEventCodeEnum.TABLE_DETAIL) && (
                <Button
                  type="primary"
                  ghost
                  onClick={() => handleDetail(originalIndex)}
                  disabled={isSupportConfig}
                >
                  查看
                </Button>
              )}
              {isShowPrepareEvent(PrepareEventCodeEnum.TABLE_EDIT) && (
                <Button
                  type="default"
                  onClick={() => handleUpdate(originalIndex)}
                  disabled={isSupportConfig}
                >
                  编辑
                </Button>
              )}
              {isShowPrepareEvent(PrepareEventCodeEnum.TABLE_DELETE) && (
                <Popconfirm
                  title="确定删除？"
                  onConfirm={() => handleDelete(originalIndex)}
                  okText="确定"
                  cancelText="取消"
                >
                  <Button
                    type="danger"
                    disabled={isSupportConfig}
                  >
                    删除
                  </Button>
                </Popconfirm>
              )}
              <ButtonGroup
                buttonList={rowEventList}
                ownerId={props.id}
                owner={EventOwnerEnum.TableRow}
                isTableRow
                tableRow={record}
                tableIndex={originalIndex}
              />
            </Space>
          );
        },
      });
    }

    return cols;
  }, [props.body, isShowIndex, isShowPagination, pagingParam, getFixedPosition, getColumnWidth, shouldEnableFilter, getFilterOptions, handleColumnFilter, setCellEditStatus, isShowOperate, fixedOperatePosition, currentEditingIndexSet, saveLoading, handleEditSave, handleEditCancel, isShowPrepareEvent, isSupportConfig, handleDetail, handleUpdate, handleDelete, rowEventList, props.id, getOriginalRowIndex]);

  if (!isShowTable) {
    return null;
  }

  return (
    <div className="table-box" style={{ width: '100%' }}>
      {/* 搜索栏 */}
      {isShowSearch && (
        <div className="w-full mb-4">
          <TableSearch
            fieldList={props.searchFieldList}
            onSearch={handleSearch}
          />
        </div>
      )}

      {/* 表格头区域 */}
      <div className="table-box__header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <div className="table-box__title" style={{ paddingLeft: '5px', fontSize: '14px', fontWeight: 600, color: '#333', borderLeft: '3px solid #1890ff' }}>
            <span>{props.name}</span>
            {props.prompt && (
              <Tooltip title={props.prompt} style={{ marginLeft: '8px' }}>
                <ExclamationCircleOutlined style={{ fontSize: '14px', color: '#faad14' }} />
              </Tooltip>
            )}
          </div>

          {/* 左表头按钮区域 */}
          <div style={{ marginLeft: '20px', display: 'flex', alignItems: 'center' }}>
            <ButtonGroup
              buttonList={leftHeaderEventList}
              ownerId={props.id}
              owner={EventOwnerEnum.TableLeftHeader}
            >
              {isShowPrepareEvent(PrepareEventCodeEnum.TABLE_CREATE) && (
                <Button
                  type="primary"
                  onClick={handleAdd}
                  disabled={isSupportConfig}
                  icon={<PlusOutlined />}
                >
                  新增
                </Button>
              )}
              {isShowPrepareEvent(PrepareEventCodeEnum.TABLE_BATCH_EDIT) && (
                <Button
                  type="default"
                  onClick={handleBatchEdit}
                  disabled={isSupportConfig}
                  icon={<EditOutlined />}
                >
                  批量编辑
                </Button>
              )}
              {isShowPrepareEvent(PrepareEventCodeEnum.TABLE_BATCH_DELETE) && (
                <Button
                  type="danger"
                  onClick={handleBatchDelete}
                  disabled={isSupportConfig}
                  icon={<DeleteOutlined />}
                >
                  批量删除
                </Button>
              )}
              {isShowPrepareEvent(PrepareEventCodeEnum.TABLE_COPY) && (
                <Button
                  type="default"
                  onClick={handleCopy}
                  disabled={isSupportConfig}
                  icon={<CopyOutlined />}
                >
                  复制
                </Button>
              )}
            </ButtonGroup>
          </div>
        </div>

        {/* 右表头按钮区域 */}
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <ButtonGroup
            buttonList={rightHeaderEventList}
            ownerId={props.id}
            owner={EventOwnerEnum.TableRightHeader}
          />

          {/* 配置按钮 */}
          {isSupportConfig && (
            <div style={{ borderLeft: '1px solid #e8e8e8', paddingLeft: '12px', marginLeft: '12px' }}>
              <Button type="primary" onClick={handleConfig}>
                设置表格
              </Button>
            </div>
          )}

          <DownOutlined
            style={{ marginLeft: '8px', color: '#666', cursor: 'pointer', transition: 'transform 0.3s' }}
            onClick={() => setIsExpand(!isExpand)}
          />
        </div>
      </div>

      {isShowCustomContent && isExpand && (
        <div className="mb-2 w-full">
          <CustomContent refresh={customContentRefresh} setRefresh={setCustomContentRefresh} />
        </div>
      )}

      {isShowReport && isExpand && (
        <div className="mb-2 w-full text-sm">
          {dataManager.getByJp("$.syncHintMsg.reportMessage")}
        </div>
      )}

      {/* 表格主体区域 */}
      {isExpand && (
        <div className={isShowPolicyBind ? 'is-policy-bind' : ''}>
          <Table
            key={props.id}
            className="table-box__body"
            loading={tableLoading}
            dataSource={tableData}
            columns={columns}
            bordered
            onRow={(record) => ({
              onClick: () => handleRowClick(record),
              onDoubleClick: () => handleRowDblClick(record),
            })}
            rowSelection={{
              selectedRowKeys: selectedRow.map(item => item.key || item.main?.id),
              onChange: handleSelectionChange,
              type: isShowPolicyBind ? 'radio' : 'checkbox',
            }}
            locale={{ emptyText: props.emptyPrompt || '暂无数据' }}
            showSummary={isShowSummary && summaryRowData && summaryRowData.length > 0}
            summary={setSummary}
            pagination={
              !isSupportConfig && isShowPagination ? {
                current: pagingParam.pageNo,
                pageSize: pagingParam.pageSize,
                pageSizeOptions: [10, 20, 30, 50],
                showTotal: () => `共 ${pagingParam.totalCount} 条`,
                onChange: handleCurrentChange,
                onShowSizeChange: handleSizeChange,
              } : false
            }
          />
        </div>
      )}

      {/* 表格底部 */}
      <div className="table-box__footer" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '8px' }}>
        {/* 当前选中行显示 */}
        {isMainTable && currentIndex !== -1 && isShowIndex && (
          <div style={{ color: '#666', fontSize: '12px', marginLeft: '8px' }}>
            <span>当前选中的序号为：</span>
            <span style={{ color: '#1890ff', fontWeight: 'bold' }}>
              {isShowPagination
                ? `${currentIndex + 1 + (pagingParam.pageNo - 1) * pagingParam.pageSize}`
                : `${currentIndex + 1}`}
            </span>
          </div>
        )}
      </div>

      {/* 分组聚合表格 */}
      {isShowGroupingAggregate && groupingAggregateData.length > 0 && (
        <div style={{ marginTop: '16px' }}>
          {groupingAggregateData.map((item) => (
            <GroupingAggregateTable
              key={item.id}
              rule={item}
              data={tableData}
              componentManager={componentManager}
            />
          ))}
        </div>
      )}

      {/* 详情抽屉 */}
      <TableDetailDrawer
        visible={detailDrawer.visible}
        {...detailDrawer.params}
        onClose={() => setDetailDrawer({ ...detailDrawer, visible: false })}
        onUpdate={initTableData}
      />

      {/* 批量编辑对话框 */}
      <BatchEditDialog
        visible={batchEditDialog.visible}
        {...batchEditDialog.params}
        onClose={() => setBatchEditDialog({ ...batchEditDialog, visible: false })}
        onUpdate={initTableData}
      />
    </div>
  );
};

export default TableComponent;