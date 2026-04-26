<template>
  <div class="system-role-dialog-container">
    <el-dialog
      title="导入表"
      v-model="state.dialog.isShowDialog"
      width="800px"
      top="80px"
    >
      <el-form
        ref="roleDialogFormRef"
        :model="state.ruleForm"
        label-width="auto"
        size="default"
        inline
      >
        <el-form-item label="数据源" prop="dataSourceConfigId">
          <el-select
            v-model="state.ruleForm.dataSourceConfigId"
            placeholder="请选择模板类型"
            style="width: 220px"
          >
            <el-option
              v-for="item in state.options"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="表名称" prop="name">
          <el-input
            v-model="state.ruleForm.name"
            placeholder="请输入表名称"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item label="表描述" prop="url">
          <el-input
            v-model="state.ruleForm.comment"
            placeholder="请输入表描述"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="getSearch">
            <template #icon>
              <Search />
            </template>
            搜索
          </el-button>
          <el-button @click="getReset">
            <template #icon>
              <Refresh />
            </template>
            重置
          </el-button>
        </el-form-item>
      </el-form>
      <el-row>
        <el-table
          v-loading="state.loading"
          ref="table"
          :data="state.dbTableList"
          height="260px"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="55" />
          <el-table-column
            prop="name"
            label="表名称"
            :show-overflow-tooltip="true"
          />
          <el-table-column
            prop="comment"
            label="表描述"
            :show-overflow-tooltip="true"
          />
        </el-table>
      </el-row>
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

<script setup>
import CodeGenerationAPI from "@/api/codeGeneration";
import dataSourceConfigAPI from "@/api/dataSourceConfig";

// 定义子组件向父组件传值/事件
const emit = defineEmits(["refresh"]);

// 定义变量内容
const roleDialogFormRef = ref();
const state = reactive({
  ruleForm: {
    name: "",
    comment: "",
    dataSourceConfigId: "",
  },
  dialog: {
    isShowDialog: false,
    type: "",
  },
  options: [],
  loading: false,
  dbTableList: [],
  tables: [],
});
// 打开弹窗
const openDialog = () => {
  state.dialog.isShowDialog = true;
  dataSourceConfigAPI.getDataSourceConfigList().then((response) => {
    state.options = response.data;
    state.ruleForm.dataSourceConfigId = state.options[0].id;
    getList();
  });
};
const getList = () => {
  state.loading = true;
  CodeGenerationAPI.getSchemaTableList(state.ruleForm)
    .then((res) => {
      state.dbTableList = res;
    })
    .finally(() => {
      // ElMessage.error('数据加载失败');
      state.loading = false;
    });
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
const getSearch = () => {
  getList();
};
const getReset = () => {
  state.ruleForm = {
    comment: "",
    name: "",
    dataSourceConfigId: state.options[0].id,
  };
  getList();
};
const handleSelectionChange = (selection) => {
  state.tables = selection.map((item) => item.name);
};
// 提交
const onSubmit = () => {
  CodeGenerationAPI.createCodegenList({
    dataSourceConfigId: state.ruleForm.dataSourceConfigId,
    tableNames: state.tables,
  })
    .then((res) => {
      state.dialog.isShowDialog = false;
      ElMessage.success("导入成功");
      emit("refresh");
    })
    .catch(() => {
      ElMessage.error("导入失败");
    });
};
// 暴露变量
defineExpose({
  openDialog,
});
</script>

<style scoped lang="scss">
.el-overlay .el-overlay-dialog {
  height: 60%;
}
</style>
