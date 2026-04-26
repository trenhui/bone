<template>
  <div>
    <div class="app-container">
      <el-card shadow="never" class="table-container">
        <div>
          <el-button
            size="default"
            type="primary"
            @click="onOpenAddRole('add')"
            style="margin-bottom: 15px"
          >
            <template #icon>
              <Plus />
            </template>
            新增
          </el-button>
        </div>
        <el-table
          :data="state.tableData.data"
          v-loading="state.tableData.loading"
          stripe
          style="width: 100%"
          border
        >
          <el-table-column
            prop="id"
            label="主键编号"
            show-overflow-tooltip
            align="center"
          />
          <el-table-column
            prop="name"
            label="数据源名称"
            show-overflow-tooltip
            align="center"
          />
          <el-table-column
            prop="url"
            label="数据源连接"
            show-overflow-tooltip
            align="center"
          />
          <el-table-column
            prop="username"
            label="用户名"
            show-overflow-tooltip
            align="center"
          />
          <el-table-column
            prop="createTimeStr"
            label="创建时间"
            show-overflow-tooltip
            align="center"
          />
          <el-table-column label="操作" width="150" align="center">
            <template #default="scope">
              <el-button
                size="small"
                text
                type="primary"
                @click="onOpenEditRole('edit', scope.row)"
              >
                修改
              </el-button>
              <el-button
                size="small"
                text
                type="primary"
                @click="onRowDel(scope.row)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          @size-change="onHandleSizeChange"
          @current-change="onHandleCurrentChange"
          class="mt15"
          :pager-count="5"
          :page-sizes="[10, 20, 30]"
          v-model:current-page="state.tableData.param.pageNum"
          background
          v-model:page-size="state.tableData.param.pageSize"
          layout="total, sizes, prev, pager, next, jumper"
          :total="state.tableData.total"
        />
      </el-card>
    </div>
    <RoleDialog ref="roleDialogRef" @refresh="getTableData({})" />
  </div>
</template>

<script setup lang="ts">
import dataSourceConfigAPI from "@/api/dataSourceConfig";

// // 引入组件
const RoleDialog = defineAsyncComponent(
  () =>
    import(
      "@/views/codeTools/dataSourceConfiguration/components/editDialog.vue"
    )
);

// 定义变量内容
const roleDialogRef = ref();
const state = reactive({
  tableData: {
    data: [],
    total: 0,
    loading: false,
    param: {
      search: "",
      pageNum: 1,
      pageSize: 10,
    },
  },
});
// 初始化表格数据
const getTableData = (val: object) => {
  state.tableData.loading = true;
  dataSourceConfigAPI.getDataSourceConfigList(val).then((res: any) => {
    state.tableData.data = res.data;
    state.tableData.total = res.totalCount;
  });
  setTimeout(() => {
    state.tableData.loading = false;
  }, 500);
};
// 打开新增角色弹窗
const onOpenAddRole = (type: string) => {
  roleDialogRef.value.openDialog(type);
};
// // 打开修改角色弹窗
const onOpenEditRole = (type: string, row: Object) => {
  roleDialogRef.value.openDialog(type, row);
};
// 删除角色
const onRowDel = (row: any) => {
  ElMessageBox.confirm(
    `是否确认删除数据源配置编号为“${row.id}”的数据项?`,
    "提示",
    {
      confirmButtonText: "确认",
      cancelButtonText: "取消",
      type: "warning",
    }
  )
    .then(() => {
      dataSourceConfigAPI.deleteDataSourceConfig(row.id).then((res: any) => {
        getTableData({});
        ElMessage.success("删除成功");
      });
    })
    .catch(() => {});
};
// 分页改变
const onHandleSizeChange = (val: number) => {
  state.tableData.param.pageSize = val;
  getTableData({});
};
// 分页改变
const onHandleCurrentChange = (val: number) => {
  state.tableData.param.pageNum = val;
  getTableData({
    pageSize: state.tableData.param.pageSize,
    pageNo: state.tableData.param.pageNum,
  });
};
// 页面加载时
onMounted(() => {
  getTableData({});
});
</script>

<style scoped lang="scss">
.system-role-container {
  .system-role-padding {
    padding: 15px;

    .el-table {
      flex: 1;
    }
  }
}
</style>
