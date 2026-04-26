<script setup>
import { isBaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { useScopeData } from "@/components/RenderEngine/hooks/useScopeData";
import { treeMapping } from "@/utils/treeUtils";
import { getValueByJsonPath, setValueByJsonPath } from "@/utils/jsonpathUtils";
import { isEmpty } from "lodash-es";

defineOptions({
  name: "Render",
});

const router = useRouter();
const route = useRoute();

const emits = defineEmits(["update:schema"]);
const data = defineModel("data", { type: Object, default: () => ({}) });
const props = defineProps({
  schema: {
    type: Object,
    default: () => ({}),
    required: true,
  },
  rules: {
    type: Object,
    default: () => ({}),
  },
  displayMode: {
    type: String,
    default: DisplayModeEnum.EDIT,
  },
  pageData: {
    type: Object,
    default: () => ({}),
  },
});

/** 组件映射表 */
const componentMap = ref(new Map());
/** 统一验证管理 */
const validateList = ref([]);
/** 模态框状态管理 */
const currentModalComponent = shallowRef(null);
const modalVisible = ref(false);
const modalParams = ref({});
const modalCallback = ref(null);
/** 表格管理 */
const tableMap = ref(new Map());

/** 计算 schema, 并设置组件映射表 */
const computedSchema = computed(() => {
  if (isEmpty(props.schema)) {
    return null;
  }

  try {
    let result = treeMapping(
      props.schema,
      (node) => node.body,
      (node, subNodes) => (node.body = subNodes),
      (node) => {
        if (!node?.type) {
          console.warn("节点缺少type属性:", node);
          return node;
        }
        if (isBaseCompType(node.type) || node.type === "Table") {
          componentMap.value.set(node.id, node);
        }

        // 特殊处理适用责任这个字段
        if (node.code === "relateLiability") {
          return { ...node, type: "PKCustom" };
        }

        return { ...node, type: "PK" + node.type };
      }
    );
    return result;
  } catch (error) {
    console.error("Schema映射失败:", error);
    return null;
  }
});

/** getter setter */
const getComponent = (id) => {
  return componentMap.value.get(id);
};
const setComponent = (id, component) => {
  componentMap.value.set(id, component);
};

/** 向验证列表中添加验证方法 */
const pushValidateToValidateList = (validate) => {
  validateList.value.push(validate);
};
/** 验证所有验证方法 */
const validate = async () => {
  const allResult = { success: true, messages: [] };
  for (const validate of validateList.value) {
    // result的格式应为{success: true, messages?: []}
    const result = await Promise.resolve(validate());
    console.log("result", result);
    // messages可能不存在
    allResult.messages.push(...(result.messages || []));
    allResult.success = allResult.success && result.success;
  }
  return allResult;
};

/** 接收属性名以.分割的路径 */
const getValueInData = (path) => {
  return path.split(".").reduce((acc, key) => acc?.[key], data.value);
};

const setValueInData = (path, value) => {
  const parts = path.split(".");
  const last = parts.pop();
  const target = parts.reduce(
    (acc, key) => (acc[key] = acc[key] || {}),
    data.value
  );
  target[last] = value;
};

const initData = (initialData) => {
  data.value = { ...data.value, ...initialData };
};

/** 接收JsonPath格式的路径 */
const getValueInDataByJsonPath = (jsonPath) => {
  return getValueByJsonPath(data.value, jsonPath);
};

const setValueInDataByJsonPath = (jsonPath, value) => {
  setValueByJsonPath(data.value, jsonPath, value);
};

const getTable = (id) => {
  return tableMap.value.get(id);
};
const setTable = (id, table) => {
  tableMap.value.set(id, table);
};
const getTableRow = (id, index) => {
  return tableMap.value.get(id)?.[index];
};
const setTableRow = (id, index, row) => {
  tableMap.value.get(id)[index] = row;
};
const getTableValueByJsonPath = (id, jsonPath) => {
  return getValueByJsonPath(tableMap.value.get(id), jsonPath);
};
const setTableValueByJsonPath = (id, jsonPath, value) => {
  setValueByJsonPath(tableMap.value.get(id), jsonPath, value);
};

const showModal = (componentName, componentParams, callback = null) => {
  currentModalComponent.value = componentName;
  modalParams.value = componentParams;
  modalCallback.value = callback;
  modalVisible.value = true;
};
const closeModal = (params = null) => {
  modalVisible.value = false;
  modalCallback.value && modalCallback.value(params);
  // 添加延迟以允许关闭动画执行
  setTimeout(() => {
    currentModalComponent.value = null;
    modalParams.value = {};
    modalCallback.value = null;
  }, 300);
};

/**
 * 获取页面规则
 * RuleType:
 * 1：linkageRule,
 * 2：submitRule
 * 3：fieldTableRule
 * 4：linkedDisplayRule
 * 5：tablelinkageRowRule
 * 6：tableSubmitRowRule
 * 7: tableLinkageCrossTableRule
 * 8: tableSubmitCrossTableRule
 * 9: groupAggregateRule
 * 10: summaryRule
 * */
const getRules = (type, id) => {
  switch (type) {
    case 1:
      return (
        props?.rules?.fieldLinkageRuleList?.filter(
          (rule) => rule.fieldId == id
        ) || []
      );
    case 2:
      return props?.rules?.submitRuleList || [];
    case 3:
      return (
        props?.rules?.fieldTableRuleVOList?.filter(
          (rule) => rule.currentField.id == id
        ) || []
      );
    case 4:
      return (
        props?.rules?.linkedDisplayRuleVOList?.find(
          (rule) => rule.selectFieldId == id
        )?.otherField || []
      );
    case 5:
      return (
        props?.rules?.rowEditRuleVOList?.filter((rule) => rule.tableId == id) ||
        []
      );
    case 6:
      return (
        props?.rules?.rowVerifyRuleVOList?.filter(
          (rule) => rule.tableId == id
        ) || []
      );
    case 7:
      return (
        props?.rules?.crossTableDataEditVOList?.filter(
          (rule) => rule.currentTableId == id
        ) || []
      );
    case 8:
      return (
        props?.rules?.crossTableDataVerifyRuleVOList?.filter(
          (rule) => rule.currentTableId == id
        ) || []
      );
    case 9:
      return (
        props?.rules?.groupAggregateRuleVOList?.filter(
          (rule) => rule.tableId == id
        ) || []
      );
    case 10:
      return (
        props?.rules?.summaryRuleList?.find((rule) => rule.tableId == id)
          ?.dataSummaryRuleList || []
      );
    default:
      return [];
  }
};

/** 更新 schema */
const updateSchema = (displayMode = props.displayMode) => {
  emits("update:schema", displayMode);
};

/** 创建数据作用域 */
const scope = useScopeData({
  /** 组件Schema管理 */
  componentManager: {
    get: getComponent,
    set: setComponent,
  },

  /** 模态框管理 */
  modalManager: {
    showModal,
    closeModal,
  },

  /** 统一验证管理 */
  validateManager: {
    push: pushValidateToValidateList,
    validate,
  },

  /** 业务数据 */
  data,
  /** 业务数据管理 */
  dataManager: {
    get: getValueInData,
    set: setValueInData,
    getByJp: getValueInDataByJsonPath,
    setByJp: setValueInDataByJsonPath,
    initData: initData,
  },

  /** 表格数据 */
  tableMap,
  /** 表格管理 */
  tableManager: {
    get: getTable,
    set: setTable,
    getRow: getTableRow,
    setRow: setTableRow,
    getByJp: getTableValueByJsonPath,
    setByJp: setTableValueByJsonPath,
  },

  /** 规则管理 */
  rulesManager: {
    get: getRules,
  },

  /** 页面显示模式 */
  displayMode: computed(() => props.displayMode),

  /** 触发更新 schema */
  updateSchema,

  /** 页面数据 */
  ...props.pageData,

  /** 路由 */
  router,
  route,
});

onUnmounted(() => {
  componentMap.value.clear();
  currentModalComponent.value = null;
  modalVisible.value = false;
  modalParams.value = {};
  modalCallback.value = null;
  validateList.value = [];
});
</script>

<template>
  <div>
    <template v-if="!isEmpty(computedSchema)">
      <!-- 组件渲染 -->
      <component :is="computedSchema?.type" v-bind="computedSchema" />
      <!-- 模态框渲染 -->
      <teleport to="body">
        <component
          v-if="currentModalComponent"
          :is="currentModalComponent"
          v-model="modalVisible"
          v-bind="modalParams"
          @close="closeModal"
        />
      </teleport>
    </template>

    <!-- 空状态 -->
    <template v-else>
      <el-empty />
    </template>
  </div>
</template>

<style scoped lang="scss"></style>
