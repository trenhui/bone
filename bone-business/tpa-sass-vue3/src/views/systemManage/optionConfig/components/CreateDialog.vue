<script setup lang="ts">
import OptionConfigAPI, {
  ICreateOption,
  ICreateOptionValue,
} from "@/api/systemManage/optionConfig";
import { cloneDeep } from "lodash-es";
import { FormInstance } from "element-plus";
defineOptions({
  name: "SystemOptionCreateDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });

let emptyDetail: ICreateOption = {
  optionSetId: "",
  setName: "",
  setCode: "",
  setDesc: "",
  lastVersion: "",
  valueList: [],
};

const detail = ref<ICreateOption>(cloneDeep(emptyDetail));

const rules = {
  setName: [
    { required: true, message: "请填写选项集名称", trigger: "blur" },
    {
      pattern: /^[\u4e00-\u9fa5a-zA-Z0-9_]{1,50}$/,
      message: "选项集名称只能包含1-50字符的中文、字母、数字和下划线",
      trigger: "blur",
    },
  ],
  setCode: [
    { required: true, message: "请填写选项集标识", trigger: "blur" },
    {
      pattern: /^[a-zA-Z0-9_]{1,50}$/,
      message: "选项集标识只能包含1-50字符的字母、数字和下划线",
      trigger: "blur",
    },
  ],
};

const isChange = ref(false);
const confirmLoading = ref(false);

watch(
  () => dialogVisible.value,
  (value) => {
    if (value) {
      detail.value = cloneDeep(emptyDetail);
    }
  },
  { immediate: true }
);

/**
 * 选项值编辑逻辑
 */
const originValues = ref<Record<string, ICreateOptionValue>>({});
const getRowKey = (row: ICreateOptionValue): string => {
  // 新添加的使用 _tempId
  return row._tempId!;
};

const handleAddingValue = () => {
  const hasEditingRow = detail.value.valueList.some(
    (item) => item.isAdding || item.isUpdating
  );
  if (hasEditingRow) {
    ElMessage.warning("请先完成当前编辑");
    return;
  }

  detail.value.valueList.push({
    valueCode: "",
    valueName: "",
    valueEnable: 1,
    _tempId: `temp_${Date.now()}_${Math.random().toString(36).slice(2, 11)}`,
    isAdding: true, // 新添加的行处于添加状态
    isUpdating: false,
    extraPropertyObj: {},
  });
};

const handleConfirmValue = (row: ICreateOptionValue) => {
  if (!row.valueCode?.trim()) {
    ElMessage.warning("选项值标识必填");
    return;
  }
  if (!row.valueName?.trim()) {
    ElMessage.warning("选项值名称必填");
    return;
  }

  // 添加重复检查
  const isDuplicate = detail.value.valueList.some(
    (item) =>
      item !== row &&
      (item.valueCode === row.valueCode || item.valueName === row.valueName)
  );
  if (isDuplicate) {
    ElMessage.warning("选项值名称或标识存在重复");
    return;
  }

  // //添加格式校验
  // if (!/^[a-zA-Z0-9_]{1,20}$/.test(row.valueCode)) {
  //   ElMessage.warning("选项值标识只能包含1-20字符的字母、数字和下划线");
  //   return;
  // } else if (!/^[\u4e00-\u9fa5a-zA-Z0-9_]{1,50}$/.test(row.valueName)) {
  //   ElMessage.warning("选项值名称只能包含1-50字符的中文、字母、数字和下划线");
  //   return;
  // }

  // 数据规范化
  row.valueCode = row.valueCode.trim();
  row.valueName = row.valueName.trim();

  if (row.isAdding) {
    row.isAdding = false;
  } else {
    row.isUpdating = false;
    delete originValues.value[getRowKey(row)];
  }
};

const handleCancelValue = (row: ICreateOptionValue) => {
  if (row.isAdding) {
    detail.value.valueList = detail.value.valueList.filter(
      (item) => item !== row
    );
  } else {
    const originRow = originValues.value[getRowKey(row)];
    row.valueCode = originRow.valueCode;
    row.valueName = originRow.valueName;
    row.valueEnable = originRow.valueEnable;
    row.extraPropertyObj = originRow.extraPropertyObj;
    row.isUpdating = false;
    delete originValues.value[getRowKey(row)];
  }
};

const handleEditValue = (row: ICreateOptionValue) => {
  const hasEditingRow = detail.value.valueList.some(
    (item) => item.isAdding || item.isUpdating
  );
  if (hasEditingRow) {
    ElMessage.warning("请先完成当前编辑");
    return;
  }

  // 在开始编辑前保存原始值
  originValues.value[getRowKey(row)] = cloneDeep(row);
  row.isUpdating = true;
};

const handleDeleteValue = (row: ICreateOptionValue) => {
  ElMessageBox.confirm("确定删除该选项值吗？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(() => {
      detail.value.valueList = detail.value.valueList.filter(
        (item) => item !== row
      );
    })
    .catch(() => {});
};

const handleAddExtraProperty = () => {
  ElMessageBox.prompt("请输入字段名称", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
  })
    .then(({ value }) => {
      //需要唯一校验
      detail.value.extraPropertyKeyArr = detail.value.extraPropertyKeyArr || [];
      if (detail.value.extraPropertyKeyArr.some((item) => item === value)) {
        ElMessage.error("字段名称在同一选项集内需唯一");
        return;
      }
      detail.value.extraPropertyKeyArr.push(value);
    })
    .catch(() => {});
};

const formRef = ref<FormInstance>();
const handleClose = () => {
  dialogVisible.value = false;
  originValues.value = {};
  detail.value = cloneDeep(emptyDetail);
  formRef.value?.resetFields();
  confirmLoading.value = false;
  emits("close", isChange.value);
  isChange.value = false;
};

const handleSave = async () => {
  if (formRef.value) {
    const valid = await formRef.value.validate();
    if (!valid) return;
  }

  // 数据处理
  detail.value.extraPropertyKey = JSON.stringify(
    detail.value.extraPropertyKeyArr
  );
  detail.value.valueList.forEach((item) => {
    item.extraProperty = JSON.stringify(item.extraPropertyObj);
  });

  try {
    confirmLoading.value = true;
    await OptionConfigAPI.createOption(detail.value);
    ElMessage.success("创建成功");
    isChange.value = true;
    handleClose();
  } catch (error) {
    console.error(error);
  } finally {
    confirmLoading.value = false;
  }
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    title="创建选项集"
    width="50%"
    top="10vh"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div class="dialog-content">
      <el-form
        :model="detail"
        :rules="rules"
        ref="formRef"
        label-width="auto"
        class="w-[60%]"
      >
        <el-form-item label="选项集名称" prop="setName" required>
          <el-input v-model.trim="detail.setName" />
        </el-form-item>
        <el-form-item label="选项集标识" prop="setCode" required>
          <el-input v-model.trim="detail.setCode" />
        </el-form-item>
        <el-form-item label="描述" prop="setDesc">
          <el-input
            v-model="detail.setDesc"
            type="textarea"
            :rows="3"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
      </el-form>

      <el-divider />

      <div class="flex justify-between items-center">
        <span class="font-bold mb-2">选项值</span>
        <el-button type="primary" link @click="handleAddExtraProperty">
          添加字段
        </el-button>
      </div>
      <el-table border stripe :data="detail.valueList">
        <el-table-column label="选项值标识" prop="valueCode" align="center">
          <template #default="scope">
            <el-input
              v-if="scope.row.isAdding"
              v-model.trim="scope.row.valueCode"
            />
            <span v-else>{{ scope.row.valueCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="选项值名称" prop="valueName" align="center">
          <template #default="scope">
            <el-input
              v-if="scope.row.isAdding || scope.row.isUpdating"
              v-model.trim="scope.row.valueName"
            />
            <span v-else>{{ scope.row.valueName }}</span>
          </template>
        </el-table-column>

        <template v-for="key in detail.extraPropertyKeyArr" :key="key">
          <el-table-column :label="key" align="center">
            <template #default="scope">
              <el-input
                v-if="scope.row.isAdding || scope.row.isUpdating"
                v-model.trim="scope.row.extraPropertyObj[key]"
              />
              <span v-else>{{ scope.row.extraPropertyObj[key] }}</span>
            </template>
          </el-table-column>
        </template>

        <el-table-column label="启用状态" prop="valueEnable" align="center">
          <template #default="scope">
            <el-switch
              v-if="scope.row.isUpdating || scope.row.isAdding"
              v-model="scope.row.valueEnable"
              :active-value="1"
              :inactive-value="0"
            />
            <span v-else>
              {{ scope.row.valueEnable === 1 ? "启用" : "禁用" }}
            </span>
          </template>
        </el-table-column>

        <el-table-column
          label="操作"
          prop="optionValueDesc"
          width="160"
          align="center"
        >
          <template #default="scope">
            <template v-if="scope.row.isAdding || scope.row.isUpdating">
              <el-button
                type="success"
                icon="Check"
                link
                @click="handleConfirmValue(scope.row)"
              >
                确认
              </el-button>
              <el-button
                type="warning"
                icon="Close"
                link
                @click="handleCancelValue(scope.row)"
              >
                取消
              </el-button>
            </template>

            <template v-else>
              <el-button
                type="warning"
                icon="Edit"
                link
                @click="handleEditValue(scope.row)"
              >
                编辑
              </el-button>
              <el-button
                type="danger"
                icon="Delete"
                link
                @click="handleDeleteValue(scope.row)"
              >
                删除
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <el-button type="primary" link class="mt-3" @click="handleAddingValue">
        添加选项值
      </el-button>
    </div>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button :loading="confirmLoading" type="primary" @click="handleSave">
        保存
      </el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 65vh;
  padding: 20px;
  overflow-y: auto; // 添加垂直滚动条
}
</style>
