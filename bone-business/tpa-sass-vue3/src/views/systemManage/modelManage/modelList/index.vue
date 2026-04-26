<script setup lang="ts">
import ModelAPI from "@/api/model";
defineOptions({
  name: "ModelManage",
});

const router = useRouter();

const form = ref({
  modelName: "",
  modelCode: "",
});
const tableLoading = ref(false);
const modelList = ref<any>([]);

const pagingParams = ref({
  pageNum: 1,
  pageSize: 20,
  totalSize: 0,
});

const initModelList = async () => {
  tableLoading.value = true;

  try {
    const res = await ModelAPI.getModelList({
      ...pagingParams.value,
      ...form.value,
    });
    modelList.value = res.rows;
    pagingParams.value.totalSize = res.totalSize;
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};
initModelList();

const handleFieldManage = (row: any) => {
  router.push({
    name: "FieldManage",
    query: {
      modelCode: row.modelCode,
      modelName: row.modelName,
    },
  });
};

const handelSizeChange = (size: number) => {
  pagingParams.value.pageNum = 1;
  pagingParams.value.pageSize = size;
  initModelList();
};

const handelCurrentChange = (page: number) => {
  pagingParams.value.pageNum = page;
  initModelList();
};

const syncLoading = ref(false);
const handleSync = async () => {
  syncLoading.value = true;
  try {
    await ModelAPI.updateFieldWithMetaData();
    ElMessage.success("同步成功");
  } catch (error) {
    console.log(error);
  } finally {
    syncLoading.value = false;
  }
};

const resetForm = () => {
  form.value = {
    modelName: "",
    modelCode: "",
  };
  initModelList();
};
</script>

<template>
  <div class="app-container">
    <div class="search-container">
      <el-form :inline="true">
        <el-form-item label="数据模型名称">
          <el-input v-model="form.modelName" placeholder="请输入数据模型名称" />
        </el-form-item>
        <el-form-item label="数据模型标识">
          <el-input v-model="form.modelCode" placeholder="请输入数据模型标识" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="initModelList">
            <i-ep-search class="mr-1" />
            搜索
          </el-button>
          <el-button type="warning" @click="resetForm">
            <i-ep-refresh class="mr-1" />
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card shadow="never" class="table-container">
      <div class="flex justify-end items-center mb-4">
        <el-button
          :loading="syncLoading"
          type="primary"
          link
          @click="handleSync"
        >
          同步元数据至本系统
        </el-button>
      </div>
      <el-table
        v-adaptive
        v-loading="tableLoading"
        stripe
        :data="modelList"
        border
      >
        <el-table-column label="数据模型名称" align="center" prop="modelName" />
        <!-- <el-table-column label="数据模型类别" align="center" prop="blockName" /> -->
        <el-table-column label="数据模型标识" align="center" prop="modelCode" />
        <el-table-column label="创建时间" align="center" prop="createTime" />
        <el-table-column label="修改时间" align="center" prop="updateTime" />
        <!-- <el-table-column label="备注" align="center" prop="remark" /> -->
        <el-table-column label="操作" align="center" width="140">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleFieldManage(row)">
              字段管理
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end items-center mt-3">
        <el-pagination
          v-model:current-page="pagingParams.pageNum"
          v-model:page-size="pagingParams.pageSize"
          :total="pagingParams.totalSize"
          @size-change="handelSizeChange"
          @current-change="handelCurrentChange"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[5, 10, 20, 30, 40, 50]"
          background
        />
      </div>
    </el-card>
  </div>
</template>
