<template>
  <div class="system-role-dialog-container">
    <el-dialog
      :title="state.dialog.title"
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
        <el-form-item label="数据源名称" prop="name">
          <el-input
            v-model="state.ruleForm.name"
            placeholder="请输入参数名称"
            clearable
          />
        </el-form-item>
        <el-form-item label="数据源连接" prop="url">
          <el-input
            v-model="state.ruleForm.url"
            placeholder="请输入数据源连接"
            clearable
          />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="state.ruleForm.username"
            placeholder="请输入用户名"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="state.ruleForm.password"
            placeholder="请输入密码"
            clearable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="onCancel" size="default">取 消</el-button>
          <el-button type="primary" @click="onSubmit" size="default">
            {{ state.dialog.submitTxt }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
// import { createDataSourceConfig, updateDataSourceConfig, deleteDataSourceConfig, getDataSourceConfig, getDataSourceConfigList } from "@/api/dataSourceConfig";
import dataSourceConfigAPI from "@/api/dataSourceConfig";
import { log } from "console";

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
}
const state = reactive({
  ruleForm: {
    id: "",
    name: "",
    url: "",
    username: "",
    password: "",
  },
  dialog: {
    isShowDialog: false,
    type: "",
    title: "",
    submitTxt: "",
  },
});

const rules = reactive({
  name: [{ required: true, message: "请输入参数名称", trigger: "blur" }],
  url: [{ required: true, message: "请输入数据源连接", trigger: "blur" }],
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
});
// 打开弹窗
const openDialog = (type: string, row: any) => {
  state.dialog.type = type;
  state.dialog.isShowDialog = true;
  if (type === "edit") {
    state.ruleForm = row;
    state.ruleForm.id = row.id;
    state.dialog.title = "编辑数据源配置";
    state.dialog.submitTxt = "修 改";
  } else {
    state.ruleForm = {} as RuleForm;
    state.dialog.title = "添加数据源配置";
    state.dialog.submitTxt = "新 增";
  }
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
    if (state.dialog.type === "add") {
      dataSourceConfigAPI
        .createDataSourceConfig(state.ruleForm)
        .then((res: any) => {
          ElMessage.success("新增成功");
          closeDialog();
          emit("refresh");
        });
    } else {
      dataSourceConfigAPI
        .updateDataSourceConfig(state.ruleForm)
        .then((res: any) => {
          ElMessage.success("修改成功");
          closeDialog();
          emit("refresh");
        });
    }
  });
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
