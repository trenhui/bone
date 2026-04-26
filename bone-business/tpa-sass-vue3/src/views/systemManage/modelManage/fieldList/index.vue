<script setup lang="ts">
import ModelAPI from "@/api/model";
import {
  BaseCompTypeOptions,
  getBaseCompTypeName,
} from "@/enums/baseComp/BaseCompEnum";
import { useRoute } from "vue-router";
defineOptions({
  name: "FieldManage",
});

const route = useRoute();
const modelCode = ref(route.query.modelCode);
const modelName = ref(route.query.modelName);
const form = ref({
  fieldName: "",
  componentType: "",
});
const tableLoading = ref(false);
const fieldList = ref<any>([]);

const pagingParams = ref({
  pageNum: 1,
  pageSize: 20,
  totalSize: 0,
});

const initFieldList = async () => {
  tableLoading.value = true;

  try {
    const res = await ModelAPI.getFieldListByModelCode({
      modelCode: modelCode.value as string,
      pageNum: pagingParams.value.pageNum,
      pageSize: pagingParams.value.pageSize,
      ...form.value,
    });
    fieldList.value = res.rows;
    pagingParams.value.totalSize = res.totalSize;
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};

initFieldList();

const handelSizeChange = (size: number) => {
  pagingParams.value.pageNum = 1;
  pagingParams.value.pageSize = size;
  initFieldList();
};

const handelCurrentChange = (page: number) => {
  pagingParams.value.pageNum = page;
  initFieldList();
};

const resetForm = () => {
  form.value = {
    fieldName: "",
    componentType: "",
  };
  initFieldList();
};
</script>

<template>
  <div class="app-container">
    <div class="search-container">
      <el-form ref="queryFormRef" :inline="true">
        <el-form-item label="业务字段名称">
          <el-input v-model="form.fieldName" placeholder="请输入业务字段名称" />
        </el-form-item>
        <el-form-item label="组件类型">
          <el-select v-model="form.componentType" style="width: 200px">
            <el-option
              v-for="item in BaseCompTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="initFieldList">
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
      <div class="ml-2 mb-4">
        <!-- <span class="text-sm font-[#909399]">数据模型类别：</span>
        <span class="ml-2 font-600 table-title">{{ blockName }}</span> -->
        <span class="ml-6 text-sm font-[#909399]">数据模型名称：</span>
        <span class="ml-2 font-600 table-title">{{ modelName }}</span>
      </div>
      <el-table
        v-adaptive
        v-loading="tableLoading"
        stripe
        :data="fieldList"
        border
      >
        <el-table-column label="业务字段名称" align="center" prop="fieldName" />
        <el-table-column label="字段标识" align="center" prop="fieldCode" />
        <el-table-column label="组件类型" align="center" prop="componentType">
          <template #default="scope">
            {{ getBaseCompTypeName(scope.row.componentType) }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" />
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

<style scoped lang="scss">
.table-title {
  color: var(--el-color-primary);
}
</style>
