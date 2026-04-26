<template>
  <div class="app-container">
    <el-card shadow="never" class="table-container">
      <div class="header-container">
        <el-form ref="form" label-width="auto" inline size="mini">
          <el-form-item label="表名称">
            <el-input v-model="state.ruleForm.tableName" />
          </el-form-item>
          <el-form-item label="表描述">
            <el-input v-model="state.ruleForm.tableComment" />
          </el-form-item>
          <el-form-item label="创建时间" prop="createTime">
            <el-date-picker
              v-model="state.ruleForm.startTime"
              type="datetime"
              placeholder="开始日期"
            />
            <el-date-picker
              v-model="state.ruleForm.endTime"
              type="datetime"
              placeholder="结束日期"
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
          <el-row>
            <el-col :span="1.5">
              <el-button
                type="primary"
                @click="openImportTable"
                style="margin-bottom: 20px"
              >
                导入
              </el-button>
              <el-button
                type="primary"
                @click="onOpenEditRole"
                style="margin-bottom: 20px"
                :disabled="state.selectedata && state.selectedata.length == 0"
              >
                批量导出
              </el-button>
            </el-col>
          </el-row>
        </el-form>
      </div>
      <el-table
        :data="state.tableData.data"
        v-loading="state.tableData.loading"
        style="width: 100%"
        @selection-change="handleSelectionChange"
        stripe
        height="370px"
        border
      >
        <el-table-column type="selection" align="center" />
        <el-table-column
          prop="dataSourceConfigName"
          label="数据源"
          show-overflow-tooltip
          align="center"
        />
        <el-table-column
          prop="tableName"
          label="表名称"
          show-overflow-tooltip
          align="center"
        />
        <el-table-column
          prop="tableComment"
          label="表描述"
          show-overflow-tooltip
          align="center"
        />
        <el-table-column
          prop="className"
          label="实体"
          show-overflow-tooltip
          align="center"
        />
        <el-table-column
          prop="createTimeStr"
          label="创建时间"
          show-overflow-tooltip
          align="center"
        />
        <el-table-column
          prop="updateTimeStr"
          label="更新时间"
          show-overflow-tooltip
          align="center"
        />
        <el-table-column label="操作" width="150" align="center">
          <template #default="scope">
            <!-- <el-button size="small" text type="primary" @click="handleEditTable(scope.row)">修改</el-button> -->
            <el-button
              size="small"
              text
              type="primary"
              @click="handleSynchDb(scope.row)"
            >
              同步
            </el-button>
            <el-button
              size="small"
              text
              type="primary"
              @click="handleDelete(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        @size-change="onHandleSizeChange"
        @current-change="onHandleCurrentChange"
        style="margin-top: 30px"
        :pager-count="5"
        :page-sizes="[10, 20, 30]"
        v-model:current-page="state.tableData.param.pageNum"
        background
        v-model:page-size="state.tableData.param.pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="state.tableData.total"
      />
    </el-card>
    <exportTo ref="exportToRef" @refresh="getTableData({})" />
    <roleDialog ref="roleDialogRef" @refresh="getTableData({})" />
  </div>
</template>

<script setup>
import CodeGenerationAPI from "@/api/codeGeneration";
const exportTo = defineAsyncComponent(
  () => import("@/views/codeTools/codeGeneration/components/exportTo.vue")
);
const roleDialog = defineAsyncComponent(
  () => import("@/views/codeTools/codeGeneration/components/roleDialog.vue")
);
const exportToRef = ref();
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
  ruleForm: {
    tableName: "",
    tableComment: "",
    startTime: "",
    endTime: "",
  },
  selectedata: [],
});

const getTableData = (val) => {
  state.tableData.loading = true;
  CodeGenerationAPI.getCodegenTablePage({
    ...val,
    pageSize: state.tableData.param.pageSize,
    pageNo: state.tableData.param.pageNum,
  }).then((res) => {
    state.tableData.data = res.data;
    state.tableData.total = res.totalCount;
  });
  setTimeout(() => {
    state.tableData.loading = false;
  }, 500);
};
const onOpenEditRole = () => {
  roleDialogRef.value.openDialog(state.selectedata);
};
const openImportTable = () => {
  exportToRef.value.openDialog();
};
const handleEditTable = (row) => {
  // 	const tableId = row.id;
  //   const params = { id: tableId };
  //   console.log(params, 'hausgidjh');
  //   router.push({
  //     path: '/system/editTable',
  //     query: params
  //   })
};
// 分页改变
const onHandleSizeChange = (val) => {
  state.tableData.param.pageSize = val;
  getTableData({});
};
// 分页改变
const onHandleCurrentChange = (val) => {
  state.tableData.param.pageNum = val;
  getTableData({});
};

const getSearch = () => {
  getTableData(state.ruleForm);
};
const getReset = () => {
  state.ruleForm = {
    tableName: "",
    tableComment: "",
    startTime: "",
    endTime: "",
  };
  getTableData(state.ruleForm);
};
const handleSynchDb = (row) => {
  ElMessageBox.confirm(`确认要强制同步"${row.tableName}"表结构吗？`, "提示", {
    confirmButtonText: "确认",
    cancelButtonText: "取消",
    // type: 'warning',
  })
    .then(() => {
      CodeGenerationAPI.syncCodegenFromDB(row.id).then((res) => {
        if (res.code == 0) {
          getTableData({});
          ElMessage.success("同步成功");
        } else {
          ElMessage.warning(res.msg);
        }
      });
    })
    .catch(() => {});
};
const handleDelete = (row) => {
  ElMessageBox.confirm(
    `是否确认删除表名称为"${row.tableName}"的数据项?`,
    "提示",
    {
      confirmButtonText: "确认",
      cancelButtonText: "取消",
      // type: 'warning',
    }
  )
    .then(() => {
      CodeGenerationAPI.deleteCodegen(row.id).then((res) => {
        getTableData({});
        ElMessage.success("删除成功");
      });
    })
    .catch(() => {});
};
const handleSelectionChange = (val) => {
  state.selectedata = val;
};
// 页面加载时
onMounted(() => {
  getTableData({});
});
</script>

<style lang="scss"></style>
