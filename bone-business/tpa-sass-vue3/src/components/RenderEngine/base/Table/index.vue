<script setup>
import { Loading } from "@element-plus/icons-vue";
import TableEditableCell from "./components/TableEditableCell.vue";
const PKTableDetailDrawer = defineAsyncComponent(
  () => import("./components/DetailDrawer.vue")
);
const TableSearch = defineAsyncComponent(
  () => import("./components/TableSearch.vue")
);
const GroupingAggregateTable = defineAsyncComponent(
  () => import("./components/GroupingAggregateTable.vue")
);
const BatchEditDialog = defineAsyncComponent(
  () => import("./components/BatchEditDialog.vue")
);
const CustomContent = defineAsyncComponent(
  () => import("./components/CustomContent.vue")
);

import { OperationEnabledEnum } from "@/enums/table/OperationEnabledEnum";
import { OperationFixedEnum } from "@/enums/table/OperationFixedEnum";
import { SummaryRowStatusEnum } from "@/enums/table/SummaryRowStatusEnum";
import { OrderColumnEnableEnum } from "@/enums/table/OrderColumnEnableEnum";
import { EventOwnerEnum } from "@/enums/event/EventOwnerEnum";
import {
  PrepareEventCodeEnum,
  isPrepareEvent,
} from "@/enums/event/PrepareEventCodeEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { GroupingAggregateStatusEnum } from "@/enums/table/GroupingAggregateStatusEnum";
import {
  RuleVerifyTypeEnum,
  getRuleVerifyTypeShortLabel,
} from "@/enums/rule/RuleVerifyTypeEnum";
import { TableDisplayEnum } from "@/enums/table/TableDisplayEnum";
import DataAPI from "@/api/data";
import { createProps } from "./table";

import { useDataBinding } from "@/components/RenderEngine/hooks/useDataBinding";
import { useSummary } from "@/components/RenderEngine/hooks/useSummary";
import { useTableLinkageWatcher } from "@/components/RenderEngine/hooks/useTableLinkageWatcher";
import { useTableCrossLinkageWatcher } from "@/components/RenderEngine/hooks/useTableCrossLinkageWatcher";
import { useTableSubmitRowRule } from "@/components/RenderEngine/hooks/useTableSubmitRowRule";
import { useTableSubmitCrossTableRule } from "@/components/RenderEngine/hooks/useTableSubmitCrossTableRule";
import { useTableSave } from "@/components/RenderEngine/hooks/useTableSave";
import { useScopeData } from "../../hooks/useScopeData";
import { useSelectDropFilter } from "@/components/RenderEngine/hooks/useSelectDropFilter";

import { getValueByJsonPath } from "@/utils/jsonpathUtils";
import { cloneDeep, isEmpty } from "lodash-es";
import EventBus from "@/utils/eventBus";
import { capitalizeFirstLetter } from "@/utils/strUtils";
import { RequiredEnum } from "@/enums";
import { useBaseComponentProperty } from "../../hooks/useBaseComponentProperty";

defineOptions({
  name: "PKTable",
});

const scopeData = useScopeData();
const modalManager = scopeData.getData("modalManager");
const displayMode = scopeData.getData("displayMode");
const updateSchema = scopeData.getData("updateSchema");
const tableManager = scopeData.getData("tableManager");
const dataManager = scopeData.getData("dataManager");
const componentManager = scopeData.getData("componentManager");
const validateManager = scopeData.getData("validateManager");
const rulesManager = scopeData.getData("rulesManager");
const tenantId = scopeData.getData("tenantId");
const bizIdentityCode = scopeData.getData("bizIdentityCode");
const tabConditionParams = scopeData.getData("tableCondition") ?? [];

// 懒加载相关参数
const enableLazyLoading = false;
const lazyLoadThreshold = 0.1;
const lazyLoadRootMargin = "50px";

const props = defineProps(createProps());

const { currentDisplay } = useBaseComponentProperty(props);

const isDisplay = computed(() => {
  return currentDisplay.value === TableDisplayEnum.show;
});
const isSupportConfig = computed(() => {
  return displayMode.value === DisplayModeEnum.CONFIG;
});
const isSupportPreview = computed(() => {
  return displayMode.value === DisplayModeEnum.PREVIEW;
});
const isSupportEdit = computed(() => {
  return displayMode.value === DisplayModeEnum.EDIT;
});
const isMainTable = computed(() => {
  return !props.parentTableId;
});
const isShowTable = computed(() => {
  if (isSupportConfig.value) {
    return true;
  }

  if (!isDisplay.value) {
    return false;
  }

  if (isSupportPreview.value) {
    return true;
  }

  if (isMainTable.value) {
    return true;
  }

  if (foreignFieldName.value && foreignFieldValue.value) {
    return true;
  }

  return false;
});
const isShowOperate = computed(() => {
  return props.operationColumnEnabled === OperationEnabledEnum.ENABLED;
});
const fixedOperatePosition = computed(() => {
  return props.operationColumnFixed === OperationFixedEnum.FIXED
    ? "right"
    : false;
});
const isShowPagination = computed(() => {
  return props.paginationEnabled === 1;
});
const isShowTotalSize = computed(() => {
  return props.displayTotalSize === 1;
});
const defaultPageSize = computed(() => {
  return props.defaultPageSize || 5; // 减少分页大小
});

const pageLayout = computed(() => {
  if (isShowTotalSize.value) {
    return "total, sizes, prev, pager, next, jumper";
  }
  return "sizes, prev, pager, next, jumper";
});
const isShowSummary = computed(() => {
  return (
    props.enableDataSummary === SummaryRowStatusEnum.OPENED &&
    !isSupportConfig.value
  );
});
const isShowGroupingAggregate = computed(() => {
  return (
    props.enableDataAggregate === GroupingAggregateStatusEnum.OPENED &&
    !isSupportConfig.value
  );
});
const isShowSearch = computed(() => {
  return (
    props.enableSearch &&
    !isEmpty(props.searchFieldList) &&
    !isSupportConfig.value
  );
});
const isShowIndex = computed(() => {
  return props.enableOrderColumn === OrderColumnEnableEnum.ENABLE;
});

const foreignFieldName = computed(() => {
  try {
    if (!props.initApiParam || props.initApiParam === "") {
      return null;
    }
    return JSON.parse(props.initApiParam)?.fieldName;
  } catch (error) {
    console.error("解析 initApiParam 出错:", error);
    return null;
  }
});
const rowEventList = computed(() => {
  return props.eventTriggerList?.filter(
    (item) =>
      item.owner === EventOwnerEnum.TableRow &&
      item.prepare !== 1 &&
      !isPrepareEvent(item.eventCode)
  );
});
const leftHeaderEventList = computed(() => {
  return props.eventTriggerList?.filter(
    (item) =>
      item.owner === EventOwnerEnum.TableLeftHeader &&
      item.prepare !== 1 &&
      !isPrepareEvent(item.eventCode)
  );
});
const rightHeaderEventList = computed(() => {
  return props.eventTriggerList?.filter(
    (item) =>
      item.owner === EventOwnerEnum.TableRightHeader &&
      item.prepare !== 1 &&
      !isPrepareEvent(item.eventCode)
  );
});
const singleEditableColumnList = computed(() => {
  return props.editableColumnList
    ?.filter((item) => item.singleLineEditable)
    ?.map((item) => item.field.id);
});
const batchEditableColumnList = computed(() => {
  return props.editableColumnList
    ?.filter((item) => item.batchEditable)
    ?.map((item) => item.field.id);
});
const fillScreen = computed(() => {
  return props.fillScreen === 1;
});

const isShowCustomContent = computed(() => {
  return !isSupportConfig.value && props.modelCodeList.includes("claimInvoice");
});

const isShowReport = computed(() => {
  return (
    !isSupportConfig.value && props.modelCodeList.includes("adjustmentDetail")
  );
});

const isCollectPersonInfo = computed(() => {
  return (
    !isSupportConfig.value && props.modelCodeList.includes("collectPerson")
  );
});

const isRequired = computed(() => {
  return props.requiredData === RequiredEnum.required;
});

const isShowPolicyBind = computed(() => {
  return props.modelCodeList.includes("policyInfoModel");
});

const formRef = ref(null);
const tableData = ref([]);
const tableSearchRef = ref(null);
const tableSaveInfo = ref({});
const foreignFieldValue = ref("");
const selectedRow = ref([]);
const groupingAggregateData = ref([]);
const currentIndex = ref(-1);
const tableLoading = ref(false);
const tableRules = ref(null);
const tableRef = ref(null);

// 是否刷新自定义内容(写死的逻辑)
const customContentRefresh = ref(false);

// 懒加载相关
const tableContainerRef = ref(null);
const isTableInView = ref(false);
const isTableLoaded = ref(false);
const lazyLoadObserver = ref(null);

//表格到页面底部的距离
const computedSpace = computed(() =>
  !fillScreen.value || isSupportConfig.value
    ? false
    : isShowPagination.value
      ? 52
      : isMainTable.value && currentIndex.value !== -1 && isShowIndex.value
        ? 48
        : 25
);

// 筛选功能
const { shouldEnableFilter, getFilterOptions, handleColumnFilter } =
  useSelectDropFilter({
    tableData,
    columns: computed(() => props.body),
    tableId: props.id,
    tableManager,
    isConfigMode: isSupportConfig,
  });

//分页参数
const pagingParam = ref({
  pageNo: 1,
  pageSize: defaultPageSize.value,
  totalCount: 0,
  totalPage: 0,
});

// 搜索参数
const searchParams = ref([]);

const initTableData = async () => {
  if (isSupportConfig.value) return;

  // 性能优化：添加防抖，避免频繁请求
  if (tableLoading.value) return;

  // // 懒加载：只有表格进入视口时才加载数据
  // if (
  //   !isTableInView.value &&
  //   !isSupportConfig.value &&
  //   !isSupportPreview.value
  // ) {
  //   return;
  // }

  if (isSupportPreview.value) {
    tableManager.set(props.id, []);
    tableData.value = tableManager.get(props.id);
    return;
  }

  const initParams = {
    bizIdentityCode,
    tenantId,
    queryParams: [],
    modelNames: props.modelCodeList,
  };

  // 当存在外键值时，设置外键值
  if (foreignFieldName.value) {
    if (foreignFieldValue.value) {
      initParams[foreignFieldName.value] = foreignFieldValue.value;
    } else {
      return;
    }
  }

  // 当存在排序字段，设置排序字段
  if (!isEmpty(props.fieldSortTypeList)) {
    initParams.sortingFields = props.fieldSortTypeList;
  }

  // 当存在搜索参数，设置搜索参数
  if (!isEmpty(searchParams.value)) {
    initParams.queryParams = searchParams.value;
  }

  // 当存在条件参数，设置条件参数
  if (!isEmpty(tabConditionParams)) {
    initParams.queryParams.push(...tabConditionParams);
  }

  // 当开启分页时，设置分页参数
  if (isShowPagination.value) {
    initParams.pageNo = pagingParam.value.pageNo;
    initParams.pageSize = pagingParam.value.pageSize;
  } else {
    initParams.pageNo = -1;
    initParams.pageSize = -1;
  }

  tableLoading.value = true;
  try {
    const res = await DataAPI.getList(initParams);
    tableManager.set(props.id, res.data);
    tableData.value = tableManager.get(props.id);

    pagingParam.value.totalCount = res.totalCount;
    pagingParam.value.totalPage = res.totalPage;
    pagingParam.value.pageNo = res.currPage;
    pagingParam.value.pageSize = res.pageSize;

    initTableSaveInfo();
    initTableCurrentIndex();

    nextTick(() => {
      tableRef?.value?.doLayout();
      initPolicyBind();
    });
  } catch (error) {
    console.error("获取表格数据失败", error.message);
  } finally {
    tableLoading.value = false;
  }
};

const initPolicyBind = () => {
  if (!isShowPolicyBind.value) return;

  const row = tableData.value.find((item) => item?.main?.isBind);

  if (row && tableRef.value) {
    console.log(tableRef.value, row);
    tableRef.value.toggleRowSelection(row, true);
  }
};

/**
 * 设置表格保存信息
 */
const initTableSaveInfo = () => {
  tableSaveInfo.value = {
    params: {
      bizIdentityCode,
      tenantId,
      [`related${capitalizeFirstLetter(foreignFieldName.value)}`]:
        foreignFieldValue.value,
    },
    modelNames: props.modelCodeList,
  };
};

/**
 * 初始化表格当前行
 */
const initTableCurrentIndex = () => {
  if (
    currentIndex.value !== -1 &&
    currentIndex.value < tableData.value.length
  ) {
    EventBus.emit(`table:${props.id}:currentChange`, {
      currentRow: tableData.value[currentIndex.value],
    });
  } else {
    currentIndex.value = tableData.value.indexOf(tableData.value[0]);
    EventBus.emit(`table:${props.id}:currentChange`, {
      currentRow: tableData.value[currentIndex.value],
    });
  }
};

/** 表格联动规则监听 Hook */
const tableLinkageWatcher = useTableLinkageWatcher(
  tableRules,
  tableData,
  props.id,
  tableManager,
  componentManager
);

/** 跨表格联动规则监听 Hook */
const crossLinkageWatcher = useTableCrossLinkageWatcher(
  tableRules,
  tableManager,
  componentManager
);

/**
 * 处理父表格当前行联动变化
 */
const handleParentTableCurrentChange = (data) => {
  const currentForeignFieldValue = getValueByJsonPath(
    data.currentRow,
    JSON.parse(props.initApiParam)?.dataBinding
  );
  if (foreignFieldValue.value !== currentForeignFieldValue) {
    foreignFieldValue.value = currentForeignFieldValue;
  }
};

/**
 * 初始化懒加载观察器
 */
const initLazyLoading = () => {
  if (!tableContainerRef.value || !enableLazyLoading) {
    // 如果禁用懒加载，直接标记为可见
    isTableInView.value = true;
    return;
  }

  // 如果是配置模式或预览模式，直接标记为可见
  if (isSupportConfig.value || isSupportPreview.value) {
    isTableInView.value = true;
    return;
  }

  // 创建 Intersection Observer
  lazyLoadObserver.value = new IntersectionObserver(
    (entries) => {
      const entry = entries[0];
      if (entry.isIntersecting && !isTableLoaded.value) {
        console.log(`表格 ${props.name || props.id} 进入视口，开始加载数据`);
        isTableInView.value = true;
        isTableLoaded.value = true;

        // 进入视口后立即加载数据
        nextTick(() => {
          initTableData();
        });

        // 数据已加载，可以停止观察
        lazyLoadObserver.value?.unobserve(tableContainerRef.value);
      }
    },
    {
      root: null, // 相对于视口
      rootMargin: lazyLoadRootMargin, // 使用配置的边距
      threshold: lazyLoadThreshold, // 使用配置的阈值
    }
  );

  // 开始观察
  lazyLoadObserver.value.observe(tableContainerRef.value);
};

/**
 * 清理懒加载观察器
 */
const cleanupLazyLoading = () => {
  if (lazyLoadObserver.value) {
    lazyLoadObserver.value.disconnect();
    lazyLoadObserver.value = null;
  }
};

const handleLoad = () => {
  isTableInView.value = true;
  initTableData();
};

let unWatch = null;
const init = () => {
  // 初始化懒加载
  // nextTick(() => {
  //   initLazyLoading();
  // });

  if (isSupportPreview.value) {
    initTableData();
  }
  /**
   * 监听外键值变化
   */
  unWatch =
    props.initApiParam && !isEmpty(props.initApiParam)
      ? watch(
          foreignFieldValue,
          (newVal) => {
            if (newVal) {
              initTableData();
            }
          },
          { immediate: true, deep: true }
        )
      : null;

  // 如果没有 initApiParam，直接初始化表格
  if (!props.initApiParam || isEmpty(props.initApiParam)) {
    initTableData();
  } else if (isMainTable.value) {
    if (
      foreignFieldValue.value !==
      scopeData.getData(JSON.parse(props.initApiParam)?.dataBinding)
    ) {
      foreignFieldValue.value = scopeData.getData(
        JSON.parse(props.initApiParam)?.dataBinding
      );
    }
  }
};

const handleImportCollectPersonInfo = (data) => {
  if (
    (isSupportEdit.value || isSupportPreview.value) &&
    !isEmpty(singleEditableColumnList.value)
  ) {
    if (tableData.value[0]) {
      if (
        currentEditingIndexSet.value.size > 0 &&
        !currentEditingIndexSet.value.has(
          tableData.value.indexOf(tableData.value[0])
        )
      ) {
        ElMessage.warning("请先完成当前编辑的内容");
        return;
      }
      handleRowQuickEdit(tableData.value[0]);
      tableManager.setRow(props.id, 0, {
        ...tableData.value[0],
        main: {
          ...tableData.value[0].main,
          collectName:
            data.collectName || tableData.value[0].main?.collectName || "",
          collectIdentityNo:
            data.collectIdNumber ||
            tableData.value[0].main?.collectIdentityNo ||
            "",
          collectIdentityType:
            data.collectIdType ||
            tableData.value[0].main?.collectIdentityType ||
            "",
          collectIdentityDatePeriod:
            data.collectIdValidityPeriod.join(",") ||
            tableData.value[0].main?.collectIdentityDatePeriod ||
            "",
          collectPhone:
            data.collectContact || tableData.value[0].main?.collectPhone || "",
          collectContactAddress:
            data.collectAddress ||
            tableData.value[0].main?.collectContactAddress ||
            "",
          personBankCode:
            data.collectBankName ||
            tableData.value[0].main?.personBankCode ||
            "",
          personAccountNo:
            data.collectBankAccount ||
            tableData.value[0].main?.personAccountNo ||
            "",
        },
      });
    } else {
      showDetailDrawer(tableData.value.length, true, true, {
        main: {
          ...(tableData.value?.[0]?.main || {}),
          collectName: data.collectName,
          collectIdentityNo: data.collectIdNumber,
          // collectIdentityType: data.collectIdType,
          collectIdentityDatePeriod: data.collectIdValidityPeriod.join(","),
          collectPhone: data.collectContact,
          collectContactAddress: data.collectAddress,
          // personBankCode: data.collectBankName,
          personAccountNo: data.collectBankAccount,
        },
      });
    }
    ElMessage.success("内容导入成功但未保存，请检查后再保存信息");
  }
};

onMounted(() => {
  if (props.parentTableId) {
    EventBus.on(
      `table:${props.parentTableId}:currentChange`,
      handleParentTableCurrentChange
    );
  }

  EventBus.on(`table:${props.id}:linkageChange`, handleBatchSave);
  EventBus.on(`table:${props.id}:refresh`, initTableData);
  if (isCollectPersonInfo.value) {
    EventBus.on(`claim:importCollectPersonInfo`, handleImportCollectPersonInfo);
  }
  init();
});

onBeforeUnmount(() => {
  if (props.parentTableId) {
    EventBus.off(
      `table:${props.parentTableId}:currentChange`,
      handleParentTableCurrentChange
    );
  }

  EventBus.off(`table:${props.id}:linkageChange`, handleBatchSave);
  EventBus.off(`table:${props.id}:refresh`, initTableData);
  if (isCollectPersonInfo.value) {
    EventBus.off(
      `claim:importCollectPersonInfo`,
      handleImportCollectPersonInfo
    );
  }
  // 清理联动规则监听器
  tableLinkageWatcher.cleanup();
  crossLinkageWatcher.cleanup();

  // 清理懒加载观察器
  cleanupLazyLoading();

  unWatch && unWatch();
});

/** 汇总行处理 */
const { setSummary, initSummaryRowData, summaryRowData } =
  useSummary(componentManager);
if (isShowSummary.value && !isSupportConfig.value) {
  initSummaryRowData(rulesManager.get(10, props.id));
}

/** 分组聚合表格处理 */
const initGroupingAggregateRules = () => {
  if (isSupportConfig.value) return;

  groupingAggregateData.value = rulesManager.get(9, props.id);
};
if (isShowGroupingAggregate.value && !isSupportConfig.value) {
  initGroupingAggregateRules();
}

/** 表格规则初始化 */
const initTableRules = async () => {
  // 只有编辑的时候需要表格规则
  if (isSupportEdit.value || isSupportPreview.value) {
    tableRules.value = {
      linkageRowRule: rulesManager.get(5, props.id),
      submitRowRule: rulesManager.get(6, props.id),
      linkageCrossTableRule: rulesManager.get(7, props.id),
      submitCrossTableRule: rulesManager.get(8, props.id),
    };

    if (!isEmpty(tableRules.value.submitCrossTableRule)) {
      validateManager.push(checkTableSubmitCrossTableRule);
    }

    if (isRequired.value) {
      validateManager.push(checkTableIsEmpty);
    }

    // 表格规则初始化完成后，初始化联动监听器
    tableLinkageWatcher.init();
    crossLinkageWatcher.init();
  }
};
initTableRules();

/**
 * 单击行，切换当前选中行以及联动其他表格变化
 * @param row 行数据
 */
const handleRowClick = (row) => {
  EventBus.emit(`table:${props.id}:currentChange`, {
    currentRow: row,
  });
  // 始终使用原始tableData来获取正确的索引
  currentIndex.value = tableData.value.indexOf(row);
};

/**
 * 双击行编辑
 * @param row 行数据
 */
const handleRowDblClick = (row) => {
  if (
    (isSupportEdit.value || isSupportPreview.value) &&
    !isEmpty(singleEditableColumnList.value)
  ) {
    handleRowQuickEdit(row);
  }
};

/**
 * 处理表格分页大小变化
 * @param pageSize 分页大小
 */
const handleSizeChange = (pageSize) => {
  pagingParam.value.pageSize = pageSize;
  initTableData();
};

/**
 * 处理表格页码变化
 * @param pageNo 页码
 */
const handleCurrentChange = (pageNo) => {
  pagingParam.value.pageNo = pageNo;
  initTableData();
};

/**
 * 处理表格选择变化
 * @param selection 选择的数据
 */
const handleSelectionChange = (selection) => {
  if (isShowPolicyBind.value) {
    if (selection.length > 1) {
      const preRow = selection[0];
      // 2. 再把选中数组中的第一项（前一项）删除
      selection.splice(0, 1);
      // 3. 再根据前一项的数据去表格中取消选中对应的那一行
      tableRef.value.toggleRowSelection(preRow, false);
    }
    selectedRow.value = selection;
    return;
  }

  selectedRow.value = selection.map((selectedItem) => {
    // 始终使用原始tableData来获取正确的索引
    const index = tableData.value.findIndex((item) => item === selectedItem);

    if (isShowPagination.value) {
      const pagingIndex =
        index + (pagingParam.value.pageNo - 1) * pagingParam.value.pageSize;
      return {
        ...selectedItem,
        index: pagingIndex,
      };
    }
    return { ...selectedItem, index };
  });
};

/**
 * 表格保存方法
 */
const { save, batchSave } = useTableSave();
const handleBatchSave = async (rows) => {
  await batchSave(tableSaveInfo.value, rows);
  initTableData();
  if (props.parentTableId) {
    EventBus.emit(`table:${props.parentTableId}:refresh`);
  }
};
const handleSave = async (row) => {
  await save(tableSaveInfo.value, row);
  if (props.parentTableId) {
    EventBus.emit(`table:${props.parentTableId}:refresh`);
  }
};

/** 当前编辑的行索引集合 */
const currentEditingIndexSet = ref(new Set());
/** 当前编辑的行数据备份集合（保存编辑前的数据，用于放弃编辑时恢复） */
const uneditedTableRowMap = ref(new Map());

/**
 * 切换行编辑状态，保存编辑前的数据
 * @param row 行数据
 */
const handleRowQuickEdit = (row) => {
  if (currentEditingIndexSet.value.has(tableData.value.indexOf(row))) {
    return;
  }

  //同时只能有一个行在编辑状态, 而且要判断是否是当前行，如果是当前行，则不进行编辑
  if (currentEditingIndexSet.value.size > 0) {
    ElMessage.warning("请先完成当前编辑的内容");
    return;
  }

  // 始终使用原始tableData来获取正确的索引
  const index = tableData.value.indexOf(row);
  currentEditingIndexSet.value.add(index);
  uneditedTableRowMap.value.set(index, cloneDeep(row));
};

/**
 * 设置单元格编辑状态，需要判断是否在可编辑列中
 * @param scope 行数据
 * @returns 是否编辑状态
 */
const setCellEditStatus = (cellItem, index) => {
  if (isEmpty(singleEditableColumnList.value)) return false;

  return (
    singleEditableColumnList.value.includes(cellItem.id) &&
    currentEditingIndexSet.value.has(index)
  );
};

const isAvailableSave = async (row, index) => {
  const result = {
    success: true,
    messages: [],
  };

  // 表单验证
  if (formRef.value) {
    try {
      const fields = props.body.map(
        (item) => useDataBinding(item.dataBinding, index).targetProp.value
      );
      await formRef.value.validateField(fields);
    } catch (error) {
      result.success = false;
      const messages = Object.values(error)
        .flat()
        .map((item) => item.message);
      result.messages.push(...(messages || []));
    }
  }

  // 行内保存规则校验
  if (tableRules.value.submitRowRule?.length > 0) {
    const { success, messages } = useTableSubmitRowRule(
      tableRules.value.submitRowRule,
      row,
      componentManager
    );
    result.success = result.success && success;
    result.messages.push(...(messages || []));
  }

  // 如果校验通过,直接返回 true
  if (result.success) return true;

  const isStopSave = result.messages.some((message) =>
    message.includes(
      `【${getRuleVerifyTypeShortLabel(RuleVerifyTypeEnum.STRONG_VERIFY)}】`
    )
  );

  // 校验失败时,显示确认框
  try {
    await ElMessageBox.confirm(
      "错误信息如下:<br>" + result.messages.join("<br>"),
      "校验失败",
      {
        confirmButtonText: "忽略并继续提交",
        cancelButtonText: "取消",
        dangerouslyUseHTMLString: true,
        draggable: true,
        closeOnClickModal: false,
        closeOnPressEscape: false,
        showClose: false,
        showCancelButton: true,
        showConfirmButton: !isStopSave,
        lockScroll: false,
      }
    );
    return true;
  } catch {
    return false;
  }
};

const saveLoading = ref(false);
const handleEditSave = async ({ row, $index: index }) => {
  if (!(await isAvailableSave(row, index))) return;

  try {
    saveLoading.value = true;
    await handleSave(row);

    currentEditingIndexSet.value.delete(index);
    uneditedTableRowMap.value.delete(index);

    initTableData();
  } catch (error) {
    console.error(error);
  } finally {
    saveLoading.value = false;
  }
};

/**
 * 放弃编辑
 * @param index 行索引
 */
const handleEditCancel = (index) => {
  currentEditingIndexSet.value.delete(index);
  tableManager.setRow(props.id, index, uneditedTableRowMap.value.get(index));
  uneditedTableRowMap.value.delete(index);

  // 清除表单验证状态
  if (formRef.value) {
    const fields = props.body.map(
      (item) => useDataBinding(item.dataBinding, index).targetProp.value
    );
    formRef.value.clearValidate(fields);
  }

  // 数据恢复后，联动规则会自动重新计算（通过表格数据监听器）
};

/**
 * 打开配置抽屉
 */
const handleConfig = () => {
  modalManager.showModal(
    "ConfigureTableDrawer",
    { id: props.id },
    (isChange) => {
      if (isChange) {
        updateSchema(DisplayModeEnum.CONFIG);
      }
    }
  );
};

/**
 * 详情抽屉
 */
const detailDrawer = ref({
  visible: false,
  params: {
    tableName: "",
    tableIndex: -1,
    body: [],
    tableId: "",
    isCreate: false,
    tableRules: {},
    singleEditableColumnList: [],
    saveInfo: {},
    editable: true,
    copyData: {},
  },
  onClose: () => {
    detailDrawer.value.visible = false;
    detailDrawer.value.params = {
      tableName: "",
      tableIndex: -1,
      body: [],
      tableId: "",
      isCreate: false,
      tableRules: {},
      saveInfo: {},
      singleEditableColumnList: [],
      editable: true,
      copyData: {},
    };
  },
  onUpdate: async () => {
    // 数据更新后刷新表格
    initTableData();
  },
});

const batchEditDialog = ref({
  visible: false,
  params: {
    selectedRow: [],
    batchEditFieldList: [],
    tableBody: [],
    saveInfo: tableSaveInfo.value,
  },
  onClose: () => {
    batchEditDialog.value.visible = false;
    batchEditDialog.value.params = {
      selectedRow: [],
      batchEditFieldList: [],
      tableBody: [],
      saveInfo: {},
    };
  },
  onUpdate: () => {
    initTableData();
  },
});

/**
 * 显示详情抽屉
 * @param index 行索引
 * @param isCreate 是否为新增
 * @param isEditable 是否可编辑
 * @param copyIndex 要被复制的行索引
 */
const showDetailDrawer = (
  index,
  isCreate = false,
  isEditable = true,
  copyData = {}
) => {
  detailDrawer.value.visible = true;
  detailDrawer.value.params = {
    tableName: props.name,
    tableIndex: index,
    body: props.body,
    tableId: props.id,
    tableRules: tableRules.value,
    saveInfo: tableSaveInfo.value,
    singleEditableColumnList: singleEditableColumnList.value,
    isCreate: isCreate,
    editable: isEditable && (isSupportEdit.value || isSupportPreview.value),
    copyData: copyData,
  };
};

/**
 * 校验表格间保存规则，规则不存在或者为空时，默认校验通过
 */
function checkTableSubmitCrossTableRule() {
  if (isEmpty(tableRules.value.submitCrossTableRule)) return { success: true };

  return useTableSubmitCrossTableRule(
    tableRules.value.submitCrossTableRule,
    tableManager,
    componentManager
  );
}

function checkTableIsEmpty() {
  if (isDisplay.value && isEmpty(tableData.value)) {
    return {
      success: false,
      messages: [
        `【${getRuleVerifyTypeShortLabel(RuleVerifyTypeEnum.STRONG_VERIFY)}】${props.name}至少需要填写一条数据`,
      ],
    };
  } else {
    return { success: true };
  }
}

/**
 * 搜索
 * @param params 搜索参数
 */
const handleSearch = (params) => {
  searchParams.value = params;
  pagingParam.value.pageNo = 1;
  initTableData();
};

/**
 * 是否显示预设事件
 * @param code 事件编码
 * @returns 是否显示
 */
const isShowPrepareEvent = (code) => {
  return props.eventTriggerList?.some((item) => item.eventCode == code);
};

/**
 * 左表头预设事件实现
 */
const handleAdd = () => {
  if (currentEditingIndexSet.value.size > 0) {
    ElMessage.warning("请先完成当前编辑的内容");
    return;
  }

  showDetailDrawer(tableData.value.length, true, true);
};
const handleCopy = () => {
  if (currentEditingIndexSet.value.size > 0) {
    ElMessage.warning("请先完成当前编辑的内容");
    return;
  }

  if (!selectedRow.value.length || selectedRow.value.length > 1) {
    ElMessage.error("请选择一行进行复制");
    return;
  }

  const cleanData = cloneDeep(selectedRow.value[0]);
  if (cleanData.main) {
    delete cleanData.main.id;
  }

  showDetailDrawer(tableData.value.length, true, true, cleanData);
};
const handleBatchEdit = () => {
  if (currentEditingIndexSet.value.size > 0) {
    ElMessage.warning("请先完成当前编辑的内容");
    return;
  }

  if (!selectedRow.value.length) {
    ElMessage.error("请选择要编辑的行");
    return;
  }

  batchEditDialog.value.visible = true;
  batchEditDialog.value.params = {
    selectedRow: selectedRow.value,
    batchEditFieldList: batchEditableColumnList.value,
    tableBody: props.body,
    saveInfo: tableSaveInfo.value,
  };
};
const handleBatchDelete = () => {
  // 只有在没有编辑状态的行时，才支持批量删除
  if (currentEditingIndexSet.value.size > 0) {
    ElMessage.warning("请先完成当前编辑的内容");
    return;
  }

  if (!selectedRow.value.length) {
    ElMessage.error("请选择要删除的行");
    return;
  }

  ElMessageBox.confirm(
    `确定删除以下选中行？<br>序号 ${selectedRow.value.map((item) => item.index + 1).join("，")}`,
    "提示",
    {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      type: "warning",
      dangerouslyUseHTMLString: true,
    }
  ).then(async () => {
    try {
      await DataAPI.deleteListRow({
        idList: selectedRow.value.map((item) => item.main.id),
        modelNames: props.modelCodeList[0],
      });
      ElMessage.success("删除成功");
      initTableData();
      if (props.parentTableId) {
        EventBus.emit(`table:${props.parentTableId}:refresh`);
      }
    } catch (error) {
      console.error(error);
    }
  });
};

/**
 * 行预设事件实现
 */
const handleDetail = (index) => {
  showDetailDrawer(index, false, false);
};
const handleUpdate = (index) => {
  //同时只能有一个行在编辑状态
  if (currentEditingIndexSet.value.size > 0) {
    ElMessage.warning("请先完成当前编辑的内容");
    return;
  }

  showDetailDrawer(index, false, true);
};
const handleDelete = (index) => {
  ElMessageBox.confirm("确定删除？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    dangerouslyUseHTMLString: true,
  }).then(async () => {
    try {
      await DataAPI.deleteListRow({
        idList: [tableData.value[index].main.id],
        modelNames: props.modelCodeList[0],
      });
      ElMessage.success("删除成功");
      initTableData();
      if (props.parentTableId) {
        EventBus.emit(`table:${props.parentTableId}:refresh`);
      }
    } catch (error) {
      console.error(error);
    }
  });
};

/**
 * 重置搜索栏
 */
const clearSearch = () => {
  if (tableSearchRef.value) {
    tableSearchRef.value.reset();
  }
};

/**
 * 刷新表格
 */
const refreshTable = () => {
  pagingParam.value.pageNo = 1;
  clearSearch();
  initTableData();
};

/**
 * 获取列的固定位置
 * @param index 列索引
 * @returns "left" | "right" | false
 */
const getFixedPosition = (index) => {
  if (index < props.leftFixed) {
    return "left";
  } else if (index >= props.body.length - props.rightFixed) {
    return "right";
  }
  return false;
};

/**
 * 获取列的宽度
 * @param width 宽度
 * @returns 宽度
 */
const getColumnWidth = (width) => {
  if (width) {
    return width > 50 ? width + "px" : 100 + "px";
  }
  return 100 + "px";
};

// 获取行在原始数据中的索引（用于编辑、删除等操作）
const getOriginalRowIndex = (row) => {
  // 直接在原始数据中查找
  let originalIndex = tableData.value.indexOf(row);

  // 如果还是找不到，尝试通过ID匹配（更可靠的方式）
  if (originalIndex === -1 && row?.main?.id) {
    originalIndex = tableData.value.findIndex(
      (item) => item?.main?.id === row.main.id
    );
  }

  return originalIndex;
};

const isExpand = ref(true);

scopeData.setData("refreshTable", refreshTable);
scopeData.setData("tableSelectRows", selectedRow);
scopeData.setData("tableData", tableData);
scopeData.setData("customContentRefresh", () => {
  customContentRefresh.value = true;
});
</script>

<template>
  <div ref="tableContainerRef" class="table-box" style="width: 100%">
    <template v-if="isShowTable">
      <!-- 懒加载占位符 -->
      <div
        v-if="
          enableLazyLoading &&
          !isTableInView &&
          !isSupportConfig &&
          !isSupportPreview
        "
        class="table-lazy-placeholder"
      >
        <div class="placeholder-content">
          <el-icon class="loading-icon"><Loading /></el-icon>
          <p class="placeholder-text">{{ name || "表格" }} 准备中...</p>
          <el-button type="primary" size="small" @click="handleLoad">
            立即加载
          </el-button>
        </div>
      </div>

      <!-- 表格内容 -->
      <div v-else>
        <!-- 搜索栏 -->
        <div v-if="isShowSearch" class="w-full">
          <Suspense>
            <template #default>
              <TableSearch
                ref="tableSearchRef"
                :field-list="props.searchFieldList"
                @search="handleSearch"
              />
            </template>
            <template #fallback>
              <el-skeleton :rows="1" animated />
            </template>
          </Suspense>
        </div>

        <!-- 表格头区域，包括标题、左表头按钮、右表头按钮 -->
        <div class="table-box__header">
          <div class="flex items-center">
            <div class="table-box__title">
              <span>{{ name }}</span>
              <span class="ml-2" v-if="prompt">
                <el-tooltip effect="dark" :content="prompt" placement="top">
                  <svg-icon icon-class="prompt" size="1em" />
                </el-tooltip>
              </span>
            </div>

            <!-- 左表头按钮区域 -->
            <div class="ml-5 flex items-center">
              <!-- 左表头事件 -->
              <PKButtonGroup
                class="pk-button-group"
                :buttonList="leftHeaderEventList"
                :ownerId="id"
                :owner="EventOwnerEnum.TableLeftHeader"
              >
                <template #prepare>
                  <!-- 预设左表头事件 -->
                  <el-button
                    v-if="isShowPrepareEvent(PrepareEventCodeEnum.TABLE_CREATE)"
                    type="primary"
                    @click="handleAdd"
                    :disabled="isSupportConfig"
                  >
                    新增
                  </el-button>
                  <el-button
                    v-if="
                      isShowPrepareEvent(PrepareEventCodeEnum.TABLE_BATCH_EDIT)
                    "
                    type="warning"
                    @click="handleBatchEdit"
                    :disabled="isSupportConfig"
                  >
                    批量编辑
                  </el-button>
                  <el-button
                    v-if="
                      isShowPrepareEvent(
                        PrepareEventCodeEnum.TABLE_BATCH_DELETE
                      )
                    "
                    type="danger"
                    @click="handleBatchDelete"
                    :disabled="isSupportConfig"
                  >
                    批量删除
                  </el-button>
                  <el-button
                    v-if="isShowPrepareEvent(PrepareEventCodeEnum.TABLE_COPY)"
                    type="primary"
                    @click="handleCopy"
                    :disabled="isSupportConfig"
                  >
                    复制
                  </el-button>
                </template>
              </PKButtonGroup>
            </div>
          </div>

          <!-- 右表头按钮区域 -->
          <div class="flex items-center">
            <!-- 右表头事件, 右表头暂无预设事件 -->
            <PKButtonGroup
              class="pk-button-group"
              :buttonList="rightHeaderEventList"
              :ownerId="id"
              :owner="EventOwnerEnum.TableRightHeader"
            />

            <!-- 配置按钮 -->
            <div
              v-if="isSupportConfig"
              class="border-l-1 border-l-solid border-l-[var(--el-border-color)] pl-3 ml-3"
            >
              <el-button type="primary" @click="handleConfig">
                设置表格
              </el-button>
            </div>

            <i-ep-arrow-down
              class="ml-2 text-[var(--el-text-color-regular)] transition-transform duration-300 cursor-pointer"
              :class="{ 'rotate-180': !isExpand }"
              @click="isExpand = !isExpand"
            />
          </div>
        </div>

        <div v-if="isShowCustomContent" v-show="isExpand" class="mb-2 w-full">
          <CustomContent v-model:refresh="customContentRefresh" />
        </div>

        <div v-if="isShowReport" v-show="isExpand" class="mb-2 w-full text-sm">
          {{ dataManager.getByJp("$.syncHintMsg.reportMessage") }}
        </div>

        <!-- 表格主体区域 -->
        <div v-show="isExpand" :class="{ 'is-policy-bind': isShowPolicyBind }">
          <el-form ref="formRef" :model="tableData">
            <el-table
              ref="tableRef"
              :key="id"
              class="table-box__body"
              v-adaptive="computedSpace"
              v-loading="tableLoading"
              :data="tableData"
              border
              @row-dblclick="handleRowDblClick"
              @row-click="handleRowClick"
              @selection-change="handleSelectionChange"
              :empty-text="emptyPrompt || '暂无数据'"
              highlight-current-row
              :show-summary="
                isShowSummary && summaryRowData && summaryRowData.length > 0
              "
              :summary-method="setSummary"
              table-layout="fixed"
            >
              <!-- 序号列 -->
              <template v-if="isShowIndex">
                <el-table-column
                  type="selection"
                  align="center"
                  width="60"
                  fixed="left"
                />
                <el-table-column
                  label="序号"
                  align="center"
                  width="60"
                  fixed="left"
                >
                  <template #default="scope">
                    <span v-if="isShowPagination">
                      {{
                        getOriginalRowIndex(scope.row) +
                        1 +
                        (pagingParam.pageNo - 1) * pagingParam.pageSize
                      }}
                    </span>
                    <span v-else>{{ getOriginalRowIndex(scope.row) + 1 }}</span>
                  </template>
                </el-table-column>
              </template>

              <template v-if="isShowPolicyBind">
                <el-table-column
                  type="selection"
                  align="center"
                  width="60"
                  fixed="left"
                />
              </template>

              <!-- 数据列 -->
              <el-table-column
                v-for="(item, index) in body"
                :key="item.id"
                :column-key="item.id"
                :label="item.showName"
                :min-width="getColumnWidth(item.width)"
                align="center"
                :fixed="getFixedPosition(index)"
                :filters="
                  shouldEnableFilter(item) ? getFilterOptions(item) : undefined
                "
                :filter-method="
                  shouldEnableFilter(item)
                    ? (value, row, column) =>
                        handleColumnFilter(value, row, column, item)
                    : undefined
                "
              >
                <template #header>
                  <span v-if="item.required === 1" class="text-red-500">*</span>
                  <span>{{ item.showName }}</span>
                </template>
                <template #filter-icon>
                  <el-icon><Filter /></el-icon>
                </template>
                <template #default="scope">
                  <TableEditableCell
                    v-bind="{
                      tableItem: item,
                      tableIndex: getOriginalRowIndex(scope.row),
                      tableRowId: scope.row.main?.id,
                      tableId: props.id,
                      isEditing: setCellEditStatus(
                        item,
                        getOriginalRowIndex(scope.row)
                      ),
                    }"
                  />
                </template>
              </el-table-column>

              <!-- 操作列 -->
              <el-table-column
                v-if="isShowOperate"
                label="操作"
                align="center"
                :width="180"
                :min-width="180"
                :fixed="fixedOperatePosition"
              >
                <template #default="scope">
                  <!-- 编辑状态时的操作按钮 -->
                  <div
                    v-if="
                      currentEditingIndexSet.has(getOriginalRowIndex(scope.row))
                    "
                  >
                    <el-button
                      type="success"
                      icon="Check"
                      link
                      :loading="saveLoading"
                      @click="handleEditSave(scope)"
                    >
                      保存
                    </el-button>
                    <el-button
                      type="warning"
                      icon="Close"
                      link
                      @click="handleEditCancel(getOriginalRowIndex(scope.row))"
                    >
                      放弃
                    </el-button>
                  </div>

                  <!-- 非编辑状态时的按钮 -->
                  <div v-else>
                    <!-- 行事件 -->
                    <PKButtonGroup
                      class="pk-button-group"
                      :buttonList="rowEventList"
                      :ownerId="id"
                      :owner="EventOwnerEnum.TableRow"
                      :isTableRow="true"
                      :tableRow="scope.row"
                      :tableIndex="getOriginalRowIndex(scope.row)"
                      align="center"
                    >
                      <!-- 预设行事件 -->
                      <template #prepare>
                        <el-button
                          v-if="
                            isShowPrepareEvent(
                              PrepareEventCodeEnum.TABLE_DETAIL
                            )
                          "
                          type="primary"
                          link
                          :disabled="isSupportConfig"
                          @click.stop="
                            handleDetail(getOriginalRowIndex(scope.row))
                          "
                        >
                          查看
                        </el-button>
                        <el-button
                          v-if="
                            isShowPrepareEvent(PrepareEventCodeEnum.TABLE_EDIT)
                          "
                          type="warning"
                          link
                          :disabled="isSupportConfig"
                          @click.stop="
                            handleUpdate(getOriginalRowIndex(scope.row))
                          "
                        >
                          编辑
                        </el-button>
                        <el-button
                          v-if="
                            isShowPrepareEvent(
                              PrepareEventCodeEnum.TABLE_DELETE
                            )
                          "
                          type="danger"
                          link
                          :disabled="isSupportConfig"
                          @click.stop="
                            handleDelete(getOriginalRowIndex(scope.row))
                          "
                        >
                          删除
                        </el-button>
                      </template>
                    </PKButtonGroup>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-form>
        </div>

        <div class="table-box__footer">
          <!-- 当前选中行显示 -->
          <div class="color-[var(--el-text-color-regular)] text-sm ml-2">
            <template v-if="isMainTable && currentIndex !== -1 && isShowIndex">
              <span>当前选中的序号为：</span>
              <span class="color-[var(--el-color-primary)] font-bold">
                {{
                  isShowPagination
                    ? `${currentIndex + 1 + (pagingParam.pageNo - 1) * pagingParam.pageSize}`
                    : `${currentIndex + 1}`
                }}
              </span>
            </template>
          </div>

          <!-- 分页器 -->
          <div
            class="table-pagination"
            v-if="!isSupportConfig && isShowPagination"
          >
            <el-pagination
              background
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
              :current-page="pagingParam.pageNo"
              :page-sizes="[10, 20, 30, 50]"
              :page-size="pagingParam.pageSize"
              :layout="pageLayout"
              size="small"
              :total="pagingParam.totalCount"
            />
          </div>
        </div>

        <!-- 分组聚合表格 -->
        <div v-if="isShowGroupingAggregate && groupingAggregateData.length > 0">
          <div v-for="item in groupingAggregateData" :key="item.id">
            <Suspense>
              <template #default>
                <GroupingAggregateTable
                  :rule="item"
                  :data="tableData"
                  :component-manager="componentManager"
                />
              </template>
              <template #fallback>
                <div class="flex justify-center items-center h-20">
                  <el-icon class="is-loading">
                    <Loading />
                  </el-icon>
                  <span class="ml-2 text-sm text-gray-500">
                    加载聚合表格组件中...
                  </span>
                </div>
              </template>
            </Suspense>
          </div>
        </div>

        <!-- 详情抽屉，用于查看、编辑、新增 -->
        <PKTableDetailDrawer
          v-model="detailDrawer.visible"
          v-bind="detailDrawer.params"
          @close="detailDrawer.onClose"
          @update="detailDrawer.onUpdate"
        />

        <BatchEditDialog
          v-model="batchEditDialog.visible"
          v-bind="batchEditDialog.params"
          @close="batchEditDialog.onClose"
          @update="batchEditDialog.onUpdate"
        />
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.table-box {
  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  &__title {
    padding-left: 5px;
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-regular);
    border-left: 3px solid var(--el-color-primary);
  }

  &__body {
    :deep(.el-form-item) {
      margin-bottom: 0;
    }
  }

  &__footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 8px;
  }
}

:deep(.is-policy-bind) {
  thead {
    th:nth-child(1) {
      .cell {
        display: none;
      }
    }
  }
}

// 懒加载占位符样式
.table-lazy-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  margin: 20px 0;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;

  .placeholder-content {
    color: var(--el-text-color-regular);
    text-align: center;

    .loading-icon {
      margin-bottom: 12px;
      font-size: 24px;
      color: var(--el-color-primary);
      animation: pulse 2s infinite;
    }

    .placeholder-text {
      margin: 12px 0 16px;
      font-size: 14px;
      color: var(--el-text-color-regular);
    }
  }
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.5;
  }
}

:deep(.pk-button-group .el-button + .el-button) {
  margin-left: 0;
}

:deep(.el-table th.el-table__cell) {
  background-color: var(--el-fill-color) !important;
}

:deep(
  .el-table.has-footer.el-table--scrollable-y tr:last-child td.el-table__cell
),
:deep(
  .el-table.has-footer.el-table--fluid-height tr:last-child td.el-table__cell
) {
  border-bottom-color: var(--el-table-border-color) !important;
}
</style>
