<script setup>
import { useModalLockScroll } from "@/hooks/common/useModalLockScroll";
import { useDataBinding } from "@/components/RenderEngine/hooks/useDataBinding";
import {
  useTableLinkageRowRule,
  cleanupWatchers,
} from "@/components/RenderEngine/hooks/useTableLinkageRowRuleInDetail";
import { useTableSubmitRowRule } from "@/components/RenderEngine/hooks/useTableSubmitRowRule";
import { cloneDeep } from "lodash-es";
import { useScopeData } from "@/components/RenderEngine/hooks/useScopeData";
import { getValueByJsonPath, setValueByJsonPath } from "@/utils/jsonpathUtils";
import { useTableSave } from "@/components/RenderEngine/hooks/useTableSave";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import {
  RuleVerifyTypeEnum,
  getRuleVerifyTypeShortLabel,
} from "@/enums/rule/RuleVerifyTypeEnum";

defineOptions({
  name: "PKTableDetailDrawer",
});

const scopeData = useScopeData();
const tableManager = scopeData.getData("tableManager");
const displayMode = scopeData.getData("displayMode");
const componentManager = scopeData.getData("componentManager");
const { save: handleSave } = useTableSave();

const emits = defineEmits(["close", "update"]);
const drawerVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  tableName: {
    type: String,
    default: "",
  },
  tableIndex: {
    type: Number,
    default: -1,
  },
  tableId: {
    type: String,
    default: "",
  },
  body: {
    type: Array,
    default: () => [],
  },
  tableRules: {
    type: Object,
    default: () => ({}),
  },
  saveInfo: {
    type: Object,
    default: () => ({}),
  },
  singleEditableColumnList: {
    type: Array,
    default: () => [],
  },
  isCreate: {
    type: Boolean,
    default: false,
  },
  editable: {
    type: Boolean,
    default: true,
  },
  copyData: {
    type: Object,
    default: () => ({}),
  },
});

const isSupportPreview = computed(
  () => displayMode.value === DisplayModeEnum.PREVIEW
);

// 是否可编辑，最高级权限控制
const canEdit = computed(() => props.editable);
const singleEditableColumnList = computed(() => props.singleEditableColumnList);
const saveInfo = computed(() => props.saveInfo);
const tableRules = computed(() => props.tableRules);
const showEditButton = computed(() => {
  return (
    canEdit.value && // 有编辑权限
    (props.isCreate || singleEditableColumnList.value.length > 0)
  ); // 新增或可编辑字段存在
});

// 是否进入编辑态
const isEditing = ref(false);

watch(
  () => [props.isCreate, canEdit],
  ([isCreate, canEdit]) => {
    // 只有同时满足有权限且是新增时，才自动进入编辑态
    isEditing.value = isCreate && canEdit;
  },
  { immediate: true }
);

// 字段级的可编辑控制
const isFieldEditable = (fieldId) => {
  if (!canEdit.value) return false; // 首先判断权限
  if (!isEditing.value) return false; // 非编辑状态
  // 新增时所有字段可编辑，否则根据 singleEditableColumnList 判断
  return props.isCreate || singleEditableColumnList.value.includes(fieldId);
};

const isAllowQuit = ref(true);
const tableData = ref([]);
const saveLoading = ref(false);

watch(
  () => props.tableId,
  () => {
    if (props.tableId) {
      tableData.value = cloneDeep(tableManager.get(props.tableId));

      if (props.isCreate) {
        if (props.copyData) {
          tableData.value.push(props.copyData);
        } else {
          tableData.value.push({});
        }
      }

      if (
        tableRules.value?.linkageRowRule &&
        tableRules.value?.linkageRowRule.length > 0
      ) {
        useTableLinkageRowRule(
          tableRules.value.linkageRowRule,
          tableData.value[props.tableIndex],
          componentManager
        );
      }
    }
  },
  { immediate: true }
);

const fieldMap = computed(() => {
  const map = new Map();
  props.body.forEach((item) => {
    const { targetDataBinding, targetProp } = useDataBinding(
      item.dataBinding,
      props.tableIndex
    );

    const targetValue = getValueByJsonPath(
      tableData.value,
      targetDataBinding.value
    );

    map.set(item.id, {
      ...item,
      targetValue,
      targetDataBinding: targetDataBinding.value,
      targetProp: targetProp.value,
      isTableField: true,
      tableRow: tableData.value[props.tableIndex],
      isView: !isFieldEditable(item.id),
    });
  });
  return map;
});

const handelFieldChange = (newVal, item) => {
  const oldVal = getValueByJsonPath(
    tableData.value,
    fieldMap.value.get(item.id).targetDataBinding
  );
  if (oldVal == newVal) {
    return;
  }
  setValueByJsonPath(
    tableData.value,
    fieldMap.value.get(item.id).targetDataBinding,
    newVal
  );
  isAllowQuit.value = false;
};

const onClose = () => {
  drawerVisible.value = false;
  emits("close");
  tableData.value = [];
  isAllowQuit.value = true;
  isEditing.value = false;
  saveLoading.value = false;
};

const handleClose = () => {
  if (!isAllowQuit.value) {
    ElMessageBox.confirm("内容存在更改，是否保存数据?", "提示", {
      confirmButtonText: "保存",
      cancelButtonText: "放弃保存",
      type: "warning",
      center: true,
    })
      .then(() => {
        handleConfirm();
      })
      .catch(() => {
        handleCancel();
      });
  } else {
    handleCancel();
  }
};

const handleEdit = () => {
  isEditing.value = true;
};

const formRef = ref(null);
const isAvailableSave = async (row) => {
  const result = {
    success: true,
    messages: [],
  };

  if (formRef.value) {
    try {
      // 表单验证，只进行行内字段验证
      await formRef.value.validate();
    } catch (error) {
      result.success = false;
      const messages = Object.values(error)
        .flat()
        .map((item) => item.message);
      result.messages.push(...(messages || []));
    }
  }

  // 行内保存规则校验
  if (
    tableRules.value.submitRowRule &&
    tableRules.value.submitRowRule.length > 0
  ) {
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

const handleConfirm = async () => {
  const data = tableData.value[props.tableIndex];

  if (!data && props.isCreate) {
    ElMessage.error("请先添加数据后再进行保存");
    return;
  }

  if (!(await isAvailableSave(data))) return;

  if (isSupportPreview.value) {
    ElMessage.success("预览模式下，校验成功，不进行保存");
    cleanupWatchers();
    isAllowQuit.value = true;
    isEditing.value = false;
    onClose();
    return;
  }

  try {
    saveLoading.value = true;
    await handleSave(saveInfo.value, data);
    cleanupWatchers();
    ElMessage.success("保存成功");

    emits("update");

    isAllowQuit.value = true;
    isEditing.value = false;
    onClose();
  } catch (error) {
    console.error(error.message);
  } finally {
    saveLoading.value = false;
  }
};

const handleCancel = () => {
  if (formRef.value) {
    formRef.value.resetFields();
  }
  cleanupWatchers();
  onClose();
};

useModalLockScroll(drawerVisible);
</script>

<template>
  <div class="table-detail-drawer">
    <el-drawer
      size="35%"
      v-model="drawerVisible"
      :before-close="handleClose"
      destroy-on-close
      :show-close="false"
    >
      <template #header>
        <div class="flex justify-between items-center">
          <span class="text-lg text-[var(--el-text-color-primary)]">
            {{ `${tableName}详情` }}
          </span>
          <div v-if="showEditButton">
            <el-button v-if="!isEditing" type="primary" @click="handleEdit">
              编辑
            </el-button>
            <el-button
              v-else
              type="primary"
              round
              :loading="saveLoading"
              @click="handleConfirm"
            >
              保存
            </el-button>
          </div>
        </div>
      </template>
      <div class="mx-4">
        <el-form
          ref="formRef"
          :model="tableData"
          label-width="auto"
          label-position="left"
        >
          <component
            v-for="item in body"
            :key="item.id"
            :is="item.type"
            v-bind="fieldMap.get(item.id)"
            @change="handelFieldChange($event, item)"
          />
        </el-form>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.drawer-footer button:first-child {
  margin-right: 10px;
}

:deep(.el-drawer__header) {
  margin-bottom: 10px;
}
</style>
