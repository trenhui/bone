<script setup lang="ts">
import PushFailAPI from "@/api/claimManage/pushfail";
import type { ClaimPushFail } from "@/api/claimManage/pushfail";
import RePushDialog from "../components/RePushDialog.vue";
import ReturnDialog from "../components/ReturnDialog.vue";
import { saveAs } from "file-saver";
import { PageCodeEnum, DisplayModeEnum } from "@/enums";
import dayjs from "dayjs";
defineOptions({
  name: "ClaimPushFail",
});

const router = useRouter();
const form = ref({
  batchNo: "",
  claimNo: "",
  policyNo: "",
  outInsureName: "",
  outInsureIdentityNo: "",
  insureName: "",
  insuranceName: "",
  branchName: "",
  errorType: "",
  auditingOperatorName: "",
  vipSign: "",
  pageNumber: 1,
  pageSize: 20,
});

const vipSignOptions = [
  { label: "普通", value: "0" },
  { label: "VIP1", value: "1" },
  { label: "VIP2", value: "2" },
  { label: "VIP3", value: "3" },
] as const;

const errorTypeOptions = [
  { label: "保司", value: "保司" },
  { label: "普康", value: "普康" },
] as const;

const claimPushFailList = ref<ClaimPushFail[]>([]);
const tableLoading = ref(false);
const total = ref(0);
const selectedRows = ref<ClaimPushFail[]>([]);

const initClaimPushFailList = async () => {
  tableLoading.value = true;
  try {
    const res = await PushFailAPI.getClaimPushFailList(form.value);
    claimPushFailList.value = res.data;
    total.value = res.totalCount;
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};

const resetForm = () => {
  form.value = {
    batchNo: "",
    claimNo: "",
    policyNo: "",
    outInsureName: "",
    outInsureIdentityNo: "",
    insureName: "",
    insuranceName: "",
    branchName: "",
    errorType: "",
    auditingOperatorName: "",
    vipSign: "",
    pageNumber: 1,
    pageSize: 20,
  };
  initClaimPushFailList();
};

const handleSizeChange = (size: number) => {
  form.value.pageSize = size;
  initClaimPushFailList();
};

const handleCurrentChange = (page: number) => {
  form.value.pageNumber = page;
  initClaimPushFailList();
};

const handleDetail = (row: ClaimPushFail) => {
  const route = router.resolve({
    name: "ClaimDetail",
    query: {
      claimId: row.claimNo,
      tenantId: row.tenantId,
      bizIdentityCode: row.bizIdentityCode,
      pageCode: PageCodeEnum.audit,
      displayMode: DisplayModeEnum.VIEW,
    },
  });

  window.open(route.href, "_blank");
};

initClaimPushFailList();

const rePushDialogRef = ref<InstanceType<typeof RePushDialog>>();
const handleRePush = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning("请选择需要重新推送的行");
    return;
  }

  try {
    await rePushDialogRef.value?.open(selectedRows.value);
    initClaimPushFailList();
  } catch (error) {
    console.error(error);
  }
};

const returnDialogRef = ref<InstanceType<typeof ReturnDialog>>();
const handleReturnAudit = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning("请选择需要退回审核的行");
    return;
  }

  try {
    await returnDialogRef.value?.open(selectedRows.value);
    initClaimPushFailList();
  } catch (error) {
    console.error(error);
  }
};

const exportLoading = ref(false);
const handleExport = async () => {
  try {
    exportLoading.value = true;
    const res = await PushFailAPI.exportClaimPushFail(form.value);
    saveAs(
      res.data,
      "推送失败数据_" + dayjs().format("YYYY-MM-DD_HH:mm:ss") + ".xlsx"
    );
  } catch (error) {
    console.error(error);
  } finally {
    exportLoading.value = false;
  }
};

const handleSelectionChange = (selection: ClaimPushFail[]) => {
  selectedRows.value = selection;
};
</script>

<template>
  <div>
    <el-card shadow="never">
      <div>
        <el-form label-width="auto" inline>
          <el-form-item label="普康批次号">
            <el-input v-model="form.batchNo" style="width: 200px" />
          </el-form-item>
          <el-form-item label="赔案号">
            <el-input v-model="form.claimNo" style="width: 200px" />
          </el-form-item>
          <el-form-item label="保单号">
            <el-input v-model="form.policyNo" style="width: 200px" />
          </el-form-item>
          <el-form-item label="出险人姓名">
            <el-input v-model="form.outInsureName" style="width: 200px" />
          </el-form-item>
          <el-form-item label="出险人证件号">
            <el-input v-model="form.outInsureIdentityNo" style="width: 200px" />
          </el-form-item>
          <el-form-item label="投保公司">
            <el-input v-model="form.insureName" style="width: 200px" />
          </el-form-item>
          <el-form-item label="保险公司">
            <el-input v-model="form.insuranceName" style="width: 200px" />
          </el-form-item>
          <el-form-item label="保险分公司">
            <el-input v-model="form.branchName" style="width: 200px" />
          </el-form-item>
          <el-form-item label="错误归类">
            <el-select v-model="form.errorType" style="width: 200px">
              <el-option
                v-for="item in errorTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="审核人员">
            <el-input
              v-model="form.auditingOperatorName"
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item label="赔案等级">
            <el-select v-model="form.vipSign" style="width: 200px">
              <el-option
                v-for="item in vipSignOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="initClaimPushFailList">
              搜索
            </el-button>
            <el-button type="warning" @click="resetForm">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div class="flex justify-end items-center mb-3">
        <el-button type="primary" @click="handleRePush">重新推送</el-button>
        <el-button type="primary" @click="handleReturnAudit">
          退回审核
        </el-button>
        <el-button
          type="primary"
          :loading="exportLoading"
          @click="handleExport"
        >
          导出推送失败
        </el-button>
      </div>
      <el-table
        v-adaptive
        stripe
        v-loading="tableLoading"
        :data="claimPushFailList"
        @selection-change="handleSelectionChange"
        border
      >
        <el-table-column type="selection" align="center" width="60" />
        <el-table-column label="序号" align="center" type="index" width="60" />
        <el-table-column
          label="错误归类"
          align="center"
          prop="errorType"
          min-width="120"
        />
        <el-table-column
          label="赔案等级"
          align="center"
          prop="vipSign"
          min-width="120"
        />
        <el-table-column
          label="保单号"
          align="center"
          prop="policyNo"
          min-width="120"
        />
        <el-table-column
          label="批次号"
          align="center"
          prop="batchNo"
          min-width="120"
        />
        <el-table-column
          label="赔案号"
          align="center"
          prop="claimNo"
          min-width="120"
        />
        <el-table-column
          label="失败时间"
          align="center"
          prop="failTime"
          min-width="120"
        />
        <el-table-column
          label="失败原因"
          align="center"
          prop="pushBackReason"
          show-overflow-tooltip
          min-width="120"
        />
        <el-table-column
          label="出险人姓名"
          align="center"
          prop="outInsureName"
          min-width="120"
        />
        <el-table-column
          label="出险人证件类型"
          align="center"
          prop="outInsureIdentityTypeCn"
          min-width="120"
        />
        <el-table-column
          label="出险人证件号码"
          align="center"
          prop="outInsureIdentityNo"
          min-width="120"
        />

        <el-table-column
          label="主被姓名"
          align="center"
          prop="mainInsureName"
          min-width="120"
        />
        <el-table-column
          label="主被证件类型"
          align="center"
          prop="mainInsureIdentityTypeCn"
          min-width="120"
        />
        <el-table-column
          label="主被证件号码"
          align="center"
          prop="mainInsureIdentityNo"
          min-width="120"
        />
        <el-table-column
          label="投保公司"
          align="center"
          prop="insureName"
          min-width="120"
        />
        <el-table-column
          label="保险公司"
          align="center"
          prop="insuranceName"
          min-width="120"
        />
        <el-table-column
          label="保险分公司"
          align="center"
          prop="branchName"
          min-width="120"
        />
        <el-table-column
          label="审核人员"
          align="center"
          prop="auditingOperatorName"
          min-width="120"
        />
        <el-table-column label="操作" align="center" width="160">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleDetail(row)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="flex justify-end items-center mt-3">
        <el-pagination
          v-model:current-page="form.pageNumber"
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
    <RePushDialog ref="rePushDialogRef" :selected-rows="selectedRows" />
    <ReturnDialog ref="returnDialogRef" :selected-rows="selectedRows" />
  </div>
</template>

<style lang="scss" scoped></style>
