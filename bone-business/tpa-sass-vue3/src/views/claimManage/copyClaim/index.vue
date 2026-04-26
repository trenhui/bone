<script setup lang="ts">
import CopyClaimAPI from "@/api/claimManage/copyClaim";
import type { CopyClaimRecord } from "@/api/claimManage/copyClaim";
import CopyClaimDialog from "./components/copyDialog.vue";
defineOptions({
  name: "CopyClaim",
});

const form = ref({
  policyNo: "",
  oldClaimNo: "",
  newClaimNo: "",
  operateTime: [] as string[],
  operator: "",
  pageNo: 1,
  pageSize: 20,
});

const copyClaimRecordList = ref<CopyClaimRecord[]>([]);
const tableLoading = ref(false);
const total = ref(0);
const copyClaimDialogRef = ref<InstanceType<typeof CopyClaimDialog>>();

const initCopyClaimRecordList = async () => {
  tableLoading.value = true;
  try {
    const params = formatSearchParams(form.value);
    const res = await CopyClaimAPI.queryCopyClaimRecord(params);
    copyClaimRecordList.value = res.data;
    total.value = res.totalCount;
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};

// 查询方式，IN，LIKE，BETWEEN，EQUAL
const SEARCH_TYPE = {
  IN: "IN",
  LIKE: "LIKE",
  BETWEEN: "BETWEEN",
  EQUAL: "EQUAL",
};

const formatSearchParams = (form: any) => {
  const params = {
    pageNo: form.pageNo,
    pageSize: form.pageSize,
    queryParams: [] as any[],
  };
  if (form.policyNo) {
    params.queryParams.push({
      field: "policyNo",
      value: form.policyNo,
      type: SEARCH_TYPE.EQUAL,
    });
  }
  if (form.oldClaimNo) {
    params.queryParams.push({
      field: "oldClaimNo",
      value: form.oldClaimNo,
      type: SEARCH_TYPE.EQUAL,
    });
  }
  if (form.newClaimNo) {
    params.queryParams.push({
      field: "newClaimNo",
      value: form.newClaimNo,
      type: SEARCH_TYPE.EQUAL,
    });
  }
  if (form.operator) {
    params.queryParams.push({
      field: "operator",
      value: form.operator,
      type: SEARCH_TYPE.EQUAL,
    });
  }
  if (form.operateTime && form.operateTime.length > 0) {
    params.queryParams.push({
      field: "operateTime",
      value: form.operateTime,
      type: SEARCH_TYPE.BETWEEN,
    });
  }
  return params;
};

const resetForm = () => {
  form.value = {
    policyNo: "",
    oldClaimNo: "",
    newClaimNo: "",
    operateTime: [] as string[],
    operator: "",
    pageNo: 1,
    pageSize: 20,
  };
  initCopyClaimRecordList();
};

const handleCurrentChange = (page: number) => {
  form.value.pageNo = page;
  initCopyClaimRecordList();
};

const handleSizeChange = (size: number) => {
  form.value.pageSize = size;
  initCopyClaimRecordList();
};

const handleCopyClaim = () => {
  copyClaimDialogRef.value?.open().then(() => {
    resetForm();
  });
};

initCopyClaimRecordList();
</script>

<template>
  <div>
    <el-card shadow="never">
      <div>
        <el-form label-width="auto" inline>
          <el-form-item label="保单号">
            <el-input v-model="form.policyNo" style="width: 240px" />
          </el-form-item>
          <el-form-item label="原赔案号">
            <el-input v-model="form.oldClaimNo" style="width: 240px" />
          </el-form-item>
          <el-form-item label="新赔案号">
            <el-input v-model="form.newClaimNo" style="width: 240px" />
          </el-form-item>
          <el-form-item label="操作人员">
            <el-input v-model="form.operator" style="width: 240px" />
          </el-form-item>
          <el-form-item label="复制时间">
            <el-date-picker
              v-model="form.operateTime"
              type="daterange"
              style="width: 240px"
              value-format="YYYY-MM-DD"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="initCopyClaimRecordList">
              搜索
            </el-button>
            <el-button type="warning" @click="resetForm">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="flex items-center mb-3">
        <el-button type="primary" @click="handleCopyClaim">复制赔案</el-button>
      </div>

      <el-table
        v-adaptive
        stripe
        v-loading="tableLoading"
        :data="copyClaimRecordList"
        border
      >
        <el-table-column label="序号" align="center" type="index" width="60" />
        <el-table-column
          label="原赔案批次"
          align="center"
          prop="oldBatchNo"
          min-width="120"
        />
        <el-table-column
          label="原赔案号"
          align="center"
          prop="oldClaimNo"
          min-width="120"
        />
        <el-table-column
          label="原签收时间"
          align="center"
          prop="oldSignTime"
          min-width="120"
        />
        <el-table-column
          label="新赔案批次"
          align="center"
          prop="newBatchNo"
          min-width="120"
        />
        <el-table-column
          label="新赔案号"
          align="center"
          prop="newClaimNo"
          min-width="120"
        />
        <el-table-column
          label="新签收时间"
          align="center"
          prop="newSignTime"
          min-width="120"
        />
        <el-table-column
          label="操作人员"
          align="center"
          prop="operator"
          show-overflow-tooltip
          min-width="120"
        />
        <el-table-column
          label="复制时间"
          align="center"
          prop="operateTime"
          min-width="120"
        />
        <el-table-column
          label="备注"
          align="center"
          prop="remark"
          show-overflow-tooltip
          min-width="120"
        />
      </el-table>

      <div class="flex justify-end items-center mt-3">
        <el-pagination
          v-model:current-page="form.pageNo"
          v-model:page-size="form.pageSize"
          :total="total"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          layout="total, sizes, prev, pager, next, jumper"
          :page-sizes="[5, 10, 20, 30, 40, 50]"
          background
        />
      </div>
    </el-card>

    <CopyClaimDialog ref="copyClaimDialogRef" />
  </div>
</template>

<style lang="scss" scoped></style>
