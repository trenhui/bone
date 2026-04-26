<template>
  <div class="system-role-dialog-container">
    <el-dialog
      title="批量导出"
      v-model="state.dialog.isShowDialog"
      width="500px"
    >
      <el-form
        ref="roleDialogFormRef"
        :model="state.ruleForm"
        size="default"
        label-width="100px"
        :rules="rules"
      >
        <el-form-item label="模块名" prop="name">
          <el-input
            v-model="state.ruleForm.name"
            placeholder="请输入模块名"
            clearable
          />
        </el-form-item>
        <el-form-item label="基础包名" prop="url">
          <el-input
            v-model="state.ruleForm.url"
            placeholder="请输入基础包名"
            clearable
          />
        </el-form-item>
        <el-form-item label="groupld" prop="username">
          <el-input
            v-model="state.ruleForm.username"
            placeholder="请输入groupld"
            clearable
          />
        </el-form-item>
        <el-form-item label="模板类型" prop="modelType">
          <el-select
            v-model="state.ruleForm.modelType"
            placeholder="请选择模板类型"
          >
            <el-option
              v-for="item in state.options"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="onCancel" size="default">取 消</el-button>
          <el-button type="primary" @click="onSubmit" size="default">
            确认
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="systemRoleDialog">
import { reactive, ref, computed } from "vue";
import type { ComponentSize, FormInstance, FormRules } from "element-plus";
import { ElMessageBox, ElMessage } from "element-plus";
import CodeGenerationAPI from "@/api/codeGeneration";

// 定义子组件向父组件传值/事件
const emit = defineEmits(["refresh"]);

// 定义变量内容
const roleDialogFormRef = ref();
interface RuleForm {
  id: string;
  name: string;
  url: string;
  username: string;
  password: string;
  modelType: number;
}
interface Item {
  id: number;
}

const state = reactive({
  ruleForm: {
    id: "",
    name: "",
    url: "",
    username: "",
    modelType: 2,
  },
  dialog: {
    isShowDialog: false,
    type: "",
  },
  options: [
    {
      value: 2,
      label: "标准化",
    },
    {
      value: 1,
      label: "拓展化",
    },
  ],
});
const selectedData = ref<Item[]>([]);

const rules = reactive({
  name: [{ required: true, message: "请输入模块名", trigger: "blur" }],
  url: [{ required: true, message: "请输入基础包名", trigger: "blur" }],
  username: [{ required: true, message: "请输入groupld", trigger: "blur" }],
  modelType: [{ required: true, message: "请选择模板类型", trigger: "blur" }],
});
// 打开弹窗
const openDialog = (row: Item[]) => {
  state.dialog.isShowDialog = true;
  selectedData.value = JSON.parse(JSON.stringify(row));
};
// 关闭弹窗
const closeDialog = () => {
  state.dialog.isShowDialog = false;
  roleDialogFormRef.value.resetFields();
};
// 取消
const onCancel = () => {
  closeDialog();
};
// 提交
const onSubmit = () => {
  roleDialogFormRef.value.validate((valid: boolean) => {
    if (!valid) return;
    CodeGenerationAPI.downloadCodegen({
      tableId: selectedData.value.map((item) => item.id).join(","),
      basePackage: state.ruleForm.url,
      model: state.ruleForm.name,
      modelType: state.ruleForm.modelType,
      groupId: state.ruleForm.username,
    }).then((res: any) => {
      state.dialog.isShowDialog = false;
      downloadFile(res.data, state.ruleForm.name, "zip");
    });
  });
};

const downloadFile = (obj: any, name: any, suffix: any) => {
  const url = window.URL.createObjectURL(new Blob([obj]));
  const link = document.createElement("a");
  link.style.display = "none";
  link.href = url;
  const fileName = name + "." + suffix;
  link.setAttribute("download", fileName);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
};
// 暴露变量
defineExpose({
  openDialog,
});
</script>

<style scoped lang="scss">
.system-role-dialog-container {
  .menu-data-tree {
    width: 100%;
    padding: 5px;
    border: 1px solid var(--el-border-color);
    border-radius: var(--el-input-border-radius, var(--el-border-radius-base));
  }
}
</style>
