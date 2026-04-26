<script setup lang="ts">
import PersonalQuotaAPI, {
  PersonalQuotaChangeParams,
} from "@/api/personalQuota";
defineOptions({
  name: "PersonalQuotaRecordDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  policyNo: string;
  insuredName: string;
  insuredCertificateType: string;
  insuredCertificateNumber: string;
  quotaInitial: number;
}>();

const params = ref<PersonalQuotaChangeParams>({
  pageNo: 1,
  pageSize: 20,
  policyNo: props.policyNo,
  insuredName: props.insuredName,
  insuredCertificateType: props.insuredCertificateType,
  insuredCertificateNumber: props.insuredCertificateNumber,
});
const totalCount = ref(0);
const tableLoading = ref(false);
const tableData = ref<any[]>([]);

const getPersonalQuotaRecordList = async () => {
  try {
    tableLoading.value = true;
    const res = await PersonalQuotaAPI.getPersonalQuotaChangeList(params.value);
    tableData.value = res.data;
    totalCount.value = res.totalCount;
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};

watch(
  () => props.policyNo,
  () => {
    if (!props.policyNo) return;
    params.value = {
      pageNo: 1,
      pageSize: 20,
      policyNo: props.policyNo,
      insuredName: props.insuredName,
      insuredCertificateType: props.insuredCertificateType,
      insuredCertificateNumber: props.insuredCertificateNumber,
    };
    getPersonalQuotaRecordList();
  },
  { immediate: true }
);

const handleClose = () => {
  dialogVisible.value = false;
  emits("close");
};

/**
 * 处理表格分页大小变化
 * @param pageSize 分页大小
 */
const handleSizeChange = (pageSize: number) => {
  params.value.pageSize = pageSize;
  getPersonalQuotaRecordList();
};

/**
 * 处理表格页码变化
 * @param pageNo 页码
 */
const handleCurrentChange = (pageNo: number) => {
  params.value.pageNo = pageNo;
  getPersonalQuotaRecordList();
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    append-to-body
    :close-on-click-modal="false"
    title="个人专属额度的保全记录"
    width="55%"
  >
    <div class="dialog-content">
      <div class="mb-4">查看该保单下具体被保险人的个人专属额度的保全记录</div>
      <el-table border :data="[1]">
        <el-table-column label="普康保单号" align="center">
          <template #default>{{ props.policyNo }}</template>
        </el-table-column>
        <el-table-column label="被保险人姓名" align="center">
          <template #default>{{ props.insuredName }}</template>
        </el-table-column>
        <el-table-column label="被保险人证件号" align="center">
          <template #default>{{ props.insuredCertificateNumber }}</template>
        </el-table-column>
        <el-table-column label="初始个人额度" align="center">
          <template #default>{{ props.quotaInitial }}</template>
        </el-table-column>
      </el-table>

      <el-divider />

      <el-table border :data="tableData" :loading="tableLoading">
        <el-table-column label="序号" align="center" type="index" width="60" />
        <el-table-column
          label="变动前剩余个人额度"
          align="center"
          prop="quotaBefore"
        />
        <el-table-column label="变动金额" align="center" prop="quotaChange" />
        <el-table-column
          label="变动后剩余个人额度"
          align="center"
          prop="quotaAfter"
        />
        <el-table-column label="变动时间" align="center" prop="createTime" />
        <el-table-column
          label="操作批次"
          align="center"
          prop="batchName"
          show-overflow-tooltip
        />
        <el-table-column label="操作人" align="center" prop="createPeople" />
      </el-table>

      <div class="flex justify-end mt-4">
        <el-pagination
          background
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
          :current-page="params.pageNo"
          :page-sizes="[5, 10, 20, 50, 100]"
          :page-size="params.pageSize"
          :total="totalCount"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </div>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 50vh;
  padding: 20px;
  overflow-y: auto;
}
</style>
