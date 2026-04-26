<script setup>
import { BaseCompType } from "@/enums/baseComp/BaseCompEnum";
import { useModalLockScroll } from "@/hooks/common/useModalLockScroll";
import FieldAPI from "@/api/field";
import { cloneDeep, isEqual } from "lodash-es";

defineOptions({
  name: "UpdateFieldDrawer",
});

const emits = defineEmits(["close", "open"]);
const drawerVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  id: {
    type: String,
    default: "",
  },
  isTable: {
    type: Boolean,
    default: false,
  },
});

let initialConfig = {};
const config = ref({});
const isChange = ref(false);
const confirmLoading = ref(false);
const loading = ref(false);

const isTable = computed(() => props.isTable);
provide("isTable", isTable);

const initConfig = async () => {
  try {
    loading.value = true;
    initialConfig = await FieldAPI.getFieldPropsById(props.id);
    config.value = cloneDeep(initialConfig);
  } catch (error) {
    console.log(error);
  } finally {
    loading.value = false;
  }
};

watch(
  () => props.id,
  (newVal) => {
    if (newVal === "") return;
    initConfig();
  },
  { immediate: true }
);

//根据组件类型获取组件配置
const componentMap = {
  InputConfig: defineAsyncComponent(
    () => import("./components/config/InputConfig/index.vue")
  ),
  InputNumConfig: defineAsyncComponent(
    () => import("./components/config/InputNumConfig/index.vue")
  ),
  SelectCtrlConfig: defineAsyncComponent(
    () => import("./components/config/SelectCtrlConfig/index.vue")
  ),
  SelectDropConfig: defineAsyncComponent(
    () => import("./components/config/SelectDropConfig/index.vue")
  ),
  DateTimeConfig: defineAsyncComponent(
    () => import("./components/config/DateTimeConfig/index.vue")
  ),
  DateRangeConfig: defineAsyncComponent(
    () => import("./components/config/DateRangeConfig/index.vue")
  ),
};
const getComponent = (type) => {
  switch (type) {
    case BaseCompType.Input:
      return componentMap.InputConfig;
    case BaseCompType.InputNum:
      return componentMap.InputNumConfig;
    case BaseCompType.SelectCtrl:
      return componentMap.SelectCtrlConfig;
    case BaseCompType.SelectDrop:
      return componentMap.SelectDropConfig;
    case BaseCompType.DateTime:
      return componentMap.DateTimeConfig;
    case BaseCompType.DateRange:
      return componentMap.DateRangeConfig;
    default:
      return null;
  }
};

const selectedComponent = computed(() => getComponent(config.value?.type));

const handleConfirm = async () => {
  try {
    //校验展示标题不能为空
    if (!config.value.showName.trim()) {
      ElMessage.error("展示标题不能为空");
      return;
    }

    confirmLoading.value = true;
    await FieldAPI.updateFieldById(config.value);
    ElMessage.success("保存成功");
    isChange.value = true;
    onClose();
  } catch (error) {
    console.log(error);
  } finally {
    confirmLoading.value = false;
  }
};

const onClose = () => {
  drawerVisible.value = false;
  emits("close", isChange.value, config.value);
  config.value = {};
  isChange.value = false;
};

const handleClose = () => {
  if (!isEqual(config.value, initialConfig)) {
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
        onClose();
      });
  } else {
    onClose();
  }
};

const closeFieldLinkageRuleDrawer = () => {
  fieldLinkageRuleDrawer.value.visible = false;
  fieldLinkageRuleDrawer.value.params = {
    id: "",
    name: "",
    type: "",
  };
};
const fieldLinkageRuleDrawer = ref({
  visible: false,
  params: { id: "", name: "", type: "" },
  onClose: closeFieldLinkageRuleDrawer,
});
const handelClickRule = () => {
  fieldLinkageRuleDrawer.value.visible = true;
  fieldLinkageRuleDrawer.value.params = {
    id: props.id,
    name: config.value.name,
    type: config.value.type,
  };
};

useModalLockScroll(drawerVisible);
</script>

<template>
  <div>
    <el-drawer
      v-model="drawerVisible"
      title="编辑业务字段"
      :before-close="handleClose"
      destroy-on-close
      append-to-body
      size="40%"
    >
      <div class="pl-3 pr-5" v-loading="loading">
        <component
          :is="selectedComponent"
          v-model:config="config"
          :is-table="isTable"
          @edit-rule="handelClickRule"
        />
      </div>

      <template #footer>
        <span class="drawer-footer">
          <el-button @click="handleClose">取消</el-button>
          <el-button
            :loading="confirmLoading"
            type="primary"
            @click="handleConfirm"
          >
            确认
          </el-button>
        </span>
      </template>

      <FieldLinkageRuleDrawer
        v-model="fieldLinkageRuleDrawer.visible"
        v-bind="fieldLinkageRuleDrawer.params"
        @close="fieldLinkageRuleDrawer.onClose"
      />
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
:deep(.el-drawer__header) {
  padding: 10px 20px;
  margin-bottom: 0;
}

.drawer-footer button:first-child {
  margin-right: 10px;
}

:deep(.el-form-item) {
  margin-bottom: 10px;
}
</style>
