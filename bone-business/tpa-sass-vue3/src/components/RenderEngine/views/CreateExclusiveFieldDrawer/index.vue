<script setup>
import { BaseCompTypeOptions } from "@/enums/baseComp/BaseCompEnum";
import { AlignmentTypeEnum } from "@/enums/baseComp/AlignmentTypeEnum";
import { useModalLockScroll } from "@/hooks/common/useModalLockScroll";
import ModelAPI from "@/api/model";
import FieldAPI from "@/api/field";
import { cloneDeep, isEqual } from "lodash-es";

defineOptions({
  name: "CreateExclusiveFieldDrawer",
});

const emits = defineEmits(["close"]);
const drawerVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  bizIdentityCode: {
    type: String,
    default: "",
  },
});

const models = ref([]);
const isChange = ref(false);
const isAllowQuit = ref(true);
const confirmLoading = ref(false);
const form = ref({});
const initForm = {
  modelId: "",
  bizName: "",
  componentType: "",
  title: "",
  bizCode: "",
  alignment: AlignmentTypeEnum.Left,
  bizIdentityCode: props.bizIdentityCode,
};

const resetForm = () => {
  form.value = cloneDeep(initForm);
};

const initModels = async () => {
  models.value = await ModelAPI.getBizIdentityModelList(props.bizIdentityCode);
};

watch(
  () => drawerVisible.value,
  (newVal) => {
    if (newVal && props.bizIdentityCode) {
      initModels();
      resetForm();
    }
  },
  { immediate: true }
);

const rules = ref({
  modelId: [{ required: true, message: "数据模型不能为空", trigger: "blur" }],
  componentType: [
    { required: true, message: "组件类型不能为空", trigger: "blur" },
  ],
  bizName: [
    { required: true, message: "业务字段名称不能为空", trigger: "blur" },
    {
      pattern: /^[^\d]/,
      message: "业务字段名称不能以数字开头",
      trigger: "blur",
    },
    {
      pattern: /^(?!\d+$)/,
      message: "业务字段名称不能为纯数字",
      trigger: "blur",
    },
    { max: 30, message: "业务字段名称最多30个字符", trigger: "blur" },
  ],
  bizCode: [
    { required: true, message: "字段标识不能为空", trigger: "blur" },
    { max: 30, message: "字段标识最多30个字符", trigger: "blur" },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_]+$/,
      message: "字段标识只能以字母开头，包含字母、数字、下划线",
      trigger: "blur",
    },
  ],
});

const formRef = ref(null);
const handleConfirm = async () => {
  if (formRef.value) {
    const valid = await formRef.value.validate();
    if (!valid) {
      return;
    }
  }

  confirmLoading.value = true;
  try {
    await FieldAPI.createExclusiveField({
      ...form.value,
      bizCode: `EX_${form.value.bizCode}`,
    });
    ElMessage.success("新增成功");
    isChange.value = true;
    isAllowQuit.value = true;
    onClose();
  } catch (error) {
    console.error(error);
  } finally {
    confirmLoading.value = false;
  }
};

const onClose = () => {
  drawerVisible.value = false;
  resetForm();
  emits("close", isChange.value);
  isChange.value = false;
  isAllowQuit.value = true;
};

const handleClose = () => {
  if (!isEqual(form.value, initForm)) {
    isAllowQuit.value = false;
  }

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
        onClose();
      });
  } else {
    onClose();
  }
};

useModalLockScroll(drawerVisible);
</script>

<template>
  <div class="create-exclusive-field-drawer">
    <el-drawer
      v-model="drawerVisible"
      title="新增专属字段"
      :before-close="handleClose"
      destroy-on-close
      size="40%"
    >
      <div class="drawer-content">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
        >
          <el-form-item required label="数据模型" prop="modelId">
            <el-select v-model="form.modelId" placeholder="请选择数据模型">
              <el-option
                v-for="item in models"
                :key="item.modelId"
                :label="item.modelName"
                :value="item.modelId"
              />
            </el-select>
          </el-form-item>
          <el-form-item required label="业务字段名称" prop="bizName">
            <el-input
              v-model="form.bizName"
              placeholder="请输入业务字段名称"
              maxlength="30"
            />
          </el-form-item>
          <el-form-item required label="组件类型" prop="componentType">
            <el-select
              v-model="form.componentType"
              placeholder="请选择组件类型"
            >
              <el-option
                v-for="item in BaseCompTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item required label="字段标识" prop="bizCode">
            <el-input
              v-model="form.bizCode"
              placeholder="请输入字段标识"
              maxlength="30"
            >
              <template #prepend>EX_</template>
            </el-input>
          </el-form-item>
          <el-form-item label="展示标题" prop="title">
            <el-input
              v-model="form.title"
              placeholder="请输入展示标题"
              maxlength="30"
            />
          </el-form-item>
          <el-form-item label="对齐方式">
            <el-input model-value="左对齐" disabled />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <span class="drawer-footer">
          <el-button @click="handleClose">取消</el-button>
          <el-button
            type="primary"
            :loading="confirmLoading"
            @click="handleConfirm"
            :disabled="
              !form.modelId ||
              !form.bizName ||
              !form.componentType ||
              models.length === 0
            "
          >
            确认
          </el-button>
        </span>
      </template>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.drawer-content {
  padding: 0 20px;
}
</style>
