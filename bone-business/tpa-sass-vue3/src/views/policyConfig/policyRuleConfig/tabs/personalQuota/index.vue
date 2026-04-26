<script lang="ts">
const insuredCertificateTypeOptions = [
  { label: "身份证", value: "0" },
  { label: "军官证", value: "1" },
  { label: "中国护照", value: "2" },
  { label: "出生证", value: "3" },
  { label: "异常身份证", value: "4" },
  { label: "港澳居民来往内地通行证", value: "5" },
  { label: "港澳台居民居住证", value: "6" },
  { label: "外国护照", value: "7" },
  { label: "外国人永久居留证", value: "8" },
  { label: "台湾居民来往大陆通行证", value: "9" },
  { label: "其他", value: "10" },
  { label: "户口本", value: "11" },
  { label: "港澳居民居住证", value: "12" },
  { label: "台湾居民居住证", value: "13" },
  { label: "警察证", value: "14" },
  { label: "返乡证", value: "15" },
  { label: "士兵证", value: "16" },
  { label: "驾驶执照", value: "17" },
  { label: "学生证", value: "18" },
  { label: "武警身份证明", value: "19" },
  { label: "澳门居民身份证", value: "20" },
  { label: "香港(永久性)居民身份证", value: "21" },
  { label: "护照", value: "22" },
  { label: "组织机构代码", value: "23" },
  { label: "社会信用代码", value: "24" },
  { label: "军人证", value: "26" },
  { label: "银行", value: "27" },
  { label: "工作证", value: "28" },
  { label: "社保号", value: "29" },
  { label: "无证件", value: "30" },
  { label: "老挝国民身份证", value: "31" },
  { label: "港澳通行证", value: "32" },
  { label: "台湾通行证", value: "33" },
] as const;
</script>

<script setup lang="ts">
import PersonalQuotaAPI, { PersonalQuotaParams } from "@/api/personalQuota";
import PersonalQuotaDialog from "../../components/PersonalQuotaDialog/index.vue";
import PersonalQuotaRecordDialog from "../../components/PersonalQuotaRecordDialog/index.vue";
import { UPLOAD_CODE } from "@/api/upload";
defineOptions({
  name: "PersonalQuota",
});

const props = defineProps({
  policyNo: {
    type: String,
    required: true,
  },
  tenantId: {
    type: String,
    required: true,
  },
});

const params = ref<PersonalQuotaParams>({
  pageNo: 1,
  pageSize: 20,
  policyNo: props.policyNo,
  insuredName: undefined,
  insuredCertificateType: undefined,
  insuredCertificateNumber: undefined,
  batchName: undefined,
});
const totalCount = ref(0);

const resetForm = () => {
  params.value = {
    pageNo: 1,
    pageSize: 20,
    policyNo: props.policyNo,
    insuredName: undefined,
    insuredCertificateType: undefined,
    insuredCertificateNumber: undefined,
    batchName: undefined,
  };
  getPersonalQuotaList();
};

const tableLoading = ref(false);
const tableData = ref<any[]>([]);

const batchPageingParams = ref({
  pageNo: 1,
  pageSize: 20,
  totalPage: 0,
});
const batchNameList = ref<any[]>([]);

const getBatchNameList = async () => {
  try {
    const res = await PersonalQuotaAPI.getOperateBatchList(
      params.value.policyNo,
      batchPageingParams.value.pageNo,
      batchPageingParams.value.pageSize
    );

    if (batchPageingParams.value.pageNo === 1) {
      batchNameList.value = res.data;
    } else {
      batchNameList.value.push(...res.data);
    }

    batchPageingParams.value.totalPage = res.totalPage;
  } catch (error) {
    console.error(error);
  }
};

const resetBatchNameList = async () => {
  batchPageingParams.value.pageNo = 1;
  batchNameList.value = [];
  batchPageingParams.value.totalPage = 0;
  await getBatchNameList();
};

const loadMoreBatchNameList = async () => {
  if (batchPageingParams.value.pageNo < batchPageingParams.value.totalPage) {
    batchPageingParams.value.pageNo++;
    await getBatchNameList();
  }
};

const getPersonalQuotaList = async () => {
  try {
    tableLoading.value = true;
    const res = await PersonalQuotaAPI.getPersonalQuotaList(params.value);
    tableData.value = res.data;
    totalCount.value = res.totalCount;
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};

/**
 * 处理表格分页大小变化
 * @param pageSize 分页大小
 */
const handleSizeChange = (pageSize: number) => {
  params.value.pageSize = pageSize;
  getPersonalQuotaList();
};

/**
 * 处理表格页码变化
 * @param pageNo 页码
 */
const handleCurrentChange = (pageNo: number) => {
  params.value.pageNo = pageNo;
  getPersonalQuotaList();
};

const personalQuotaDialog = ref({
  isVisible: false,
  params: {
    policyNo: props.policyNo,
    dialogType: "",
    tenantId: "",
    uploadCode: "",
  },
  onClose: (isNeedRefresh: boolean) => {
    personalQuotaDialog.value.isVisible = false;
    if (isNeedRefresh) {
      getPersonalQuotaList();
    }
  },
});

const handleMaintainPersonalQuota = () => {
  personalQuotaDialog.value.params = {
    policyNo: props.policyNo,
    dialogType: "maintain",
    tenantId: props.tenantId,
    uploadCode: UPLOAD_CODE.PERSONAL_QUOTA_CHANGE,
  };
  personalQuotaDialog.value.isVisible = true;
};

const handleInitializePersonalQuota = () => {
  personalQuotaDialog.value.params = {
    policyNo: props.policyNo,
    dialogType: "initialize",
    tenantId: props.tenantId,
    uploadCode: UPLOAD_CODE.PERSONAL_QUOTA_INITIALIZE,
  };
  personalQuotaDialog.value.isVisible = true;
};

const handleReducePeople = () => {
  personalQuotaDialog.value.params = {
    policyNo: props.policyNo,
    dialogType: "reduce",
    tenantId: props.tenantId,
    uploadCode: UPLOAD_CODE.PERSONAL_QUOTA_REMOVE_PEOPLE,
  };
  personalQuotaDialog.value.isVisible = true;
};

const personalQuotaRecordDialog = ref({
  isVisible: false,
  params: {
    policyNo: props.policyNo,
    insuredName: "",
    insuredCertificateType: "",
    insuredCertificateNumber: "",
    quotaInitial: 0,
  },
  onClose: () => {
    personalQuotaRecordDialog.value.params = {
      policyNo: "",
      insuredName: "",
      insuredCertificateType: "",
      insuredCertificateNumber: "",
      quotaInitial: 0,
    };
    personalQuotaRecordDialog.value.isVisible = false;
  },
});

const handleViewPersonalQuotaRecord = (row: any) => {
  personalQuotaRecordDialog.value.params = {
    policyNo: props.policyNo,
    insuredName: row.insuredName,
    insuredCertificateType: row.insuredCertificateType,
    insuredCertificateNumber: row.insuredCertificateNumber,
    quotaInitial: row.quotaInitial,
  };
  personalQuotaRecordDialog.value.isVisible = true;
};

onMounted(() => {
  getBatchNameList();
  getPersonalQuotaList();
});
</script>

<template>
  <div>
    <el-form inline>
      <el-form-item label="被保险人姓名" label-width="100px">
        <el-input v-model="params.insuredName" style="width: 180px" clearable />
      </el-form-item>
      <el-form-item label="被保险人证件类型" label-width="130px">
        <el-select
          v-model="params.insuredCertificateType"
          style="width: 180px"
          placeholder="请选择"
          clearable
        >
          <el-option
            v-for="item in insuredCertificateTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.label"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="被保险人证件号" label-width="120px">
        <el-input
          v-model="params.insuredCertificateNumber"
          style="width: 180px"
          clearable
        />
      </el-form-item>
      <el-form-item label="操作批次" label-width="70px">
        <el-select
          v-model="params.batchName"
          style="width: 180px"
          placeholder="请选择"
          @focus="resetBatchNameList"
          v-loadMore="loadMoreBatchNameList"
          clearable
        >
          <el-option
            v-for="item in batchNameList"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="getPersonalQuotaList">搜索</el-button>
        <el-button type="warning" @click="resetForm">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="mb-3 flex justify-between items-center">
      <div>
        <el-button type="default" @click="handleMaintainPersonalQuota">
          保全个人额度
        </el-button>
        <el-button type="default" @click="handleReducePeople">
          保全减人
        </el-button>
      </div>

      <el-button type="primary" @click="handleInitializePersonalQuota">
        初始化个人额度
      </el-button>
    </div>

    <el-table
      v-loading="tableLoading"
      v-adaptive="70"
      stripe
      :data="tableData"
      border
      show-overflow-tooltip
    >
      <el-table-column label="序号" align="center" width="60">
        <template #default="{ $index }">
          {{ $index + 1 + (params.pageNo - 1) * params.pageSize }}
        </template>
      </el-table-column>
      <el-table-column label="被保险人姓名" align="center" prop="insuredName" />
      <el-table-column
        label="被保险人证件类型"
        align="center"
        prop="insuredCertificateType"
      />
      <el-table-column
        label="被保险人证件号"
        align="center"
        prop="insuredCertificateNumber"
      />
      <el-table-column
        label="初始个人额度"
        align="center"
        prop="quotaInitial"
      />
      <el-table-column label="已用理赔额度" align="center" prop="quotaClaim" />
      <el-table-column
        label="剩余个人额度"
        align="center"
        prop="quotaRemaining"
      />
      <el-table-column label="当前冻结额度" align="center" prop="quotaFrozen" />
      <el-table-column
        label="当前可用额度"
        align="center"
        prop="quotaAvailable"
      />
      <el-table-column label="最近保全时间" align="center" prop="updateTime" />
      <el-table-column label="操作" align="center" width="140">
        <template #default="{ row }">
          <el-button
            type="primary"
            link
            @click="handleViewPersonalQuotaRecord(row)"
          >
            保全记录
          </el-button>
        </template>
      </el-table-column>
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
        size="small"
        layout="total, sizes, prev, pager, next, jumper"
      />
    </div>

    <PersonalQuotaDialog
      v-model="personalQuotaDialog.isVisible"
      v-bind="personalQuotaDialog.params"
      @close="personalQuotaDialog.onClose"
    />

    <PersonalQuotaRecordDialog
      v-model="personalQuotaRecordDialog.isVisible"
      v-bind="personalQuotaRecordDialog.params"
      @close="personalQuotaRecordDialog.onClose"
    />
  </div>
</template>

<style lang="scss" scoped>
:deep(.el-form--inline .el-form-item) {
  margin-right: 20px;
}
</style>
