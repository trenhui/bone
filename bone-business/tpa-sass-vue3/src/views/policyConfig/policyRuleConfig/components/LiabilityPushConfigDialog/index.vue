<script setup lang="ts">
import BatchUpdateDialog from "./BatchUpdateDialog.vue";
import LiabilityAPI, { LiabilityPushConfigDTO } from "@/api/liability";
import { cloneDeep } from "lodash-es";
defineOptions({
  name: "LiabilityPushConfigDialog",
});

const emits = defineEmits(["close", "confirm"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  policyNo: string;
}>();

const handleClose = () => {
  dialogVisible.value = false;
};

const tableLoading = ref(false);
const liabilityMappingList = ref<LiabilityPushConfigDTO[]>([]);
const queryLiabilityMappingList = async () => {
  try {
    tableLoading.value = true;
    liabilityMappingList.value = await LiabilityAPI.getLiabilityPushConfig(
      props.policyNo
    );
  } catch (error) {
    console.error(error);
  } finally {
    tableLoading.value = false;
  }
};

onMounted(() => {
  queryLiabilityMappingList();
});

const currentEditingRow = ref<LiabilityPushConfigDTO | null>(null);
const handleEdit = (row: LiabilityPushConfigDTO) => {
  if (currentEditingRow.value) {
    ElMessage.warning("请先完成正在编辑的行");
    return;
  }

  currentEditingRow.value = cloneDeep(row);
  row.isEditing = true;
};

const saveLoading = ref(false);
const handleSave = async (row: LiabilityPushConfigDTO) => {
  try {
    saveLoading.value = true;
    await LiabilityAPI.updateLiabilityPushConfig(row);
    row.isEditing = false;
    currentEditingRow.value = null;
    ElMessage.success("保存成功");
    queryLiabilityMappingList();
  } catch (error) {
    console.error(error);
  } finally {
    saveLoading.value = false;
  }
};

const handleCancel = (row: LiabilityPushConfigDTO) => {
  row.isEditing = false;
  if (currentEditingRow.value) {
    row.insuranceCompanyCoverage =
      currentEditingRow.value.insuranceCompanyCoverage;
    row.insuranceCompanyLiability =
      currentEditingRow.value.insuranceCompanyLiability;
    row.insuranceCompanyLiabilitySub =
      currentEditingRow.value.insuranceCompanyLiabilitySub;
    row.claimAccident = currentEditingRow.value.claimAccident;
    row.insuranceCompanyAccount =
      currentEditingRow.value.insuranceCompanyAccount;
  }
  currentEditingRow.value = null;
};

const selectedRows = ref<LiabilityPushConfigDTO[]>([]);
const handleSelectionChange = (rows: LiabilityPushConfigDTO[]) => {
  selectedRows.value = rows;
};

const batchUpdateDialogRef = ref<InstanceType<typeof BatchUpdateDialog>>();
const handleBatchUpdate = () => {
  if (currentEditingRow.value) {
    ElMessage.warning("请先完成正在编辑的行");
    return;
  }

  if (selectedRows.value.length === 0) {
    ElMessage.warning("请选择需要批量修改的行");
    return;
  }

  batchUpdateDialogRef.value?.open(selectedRows.value);
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    append-to-body
    :close-on-click-modal="false"
    title="责任推送配置"
    top="10vh"
    width="80%"
  >
    <div class="dialog-content">
      <div class="mb-3 flex justify-between">
        <div>
          <el-button @click="handleBatchUpdate">批量修改</el-button>
        </div>
        <div>
          <el-button type="info" disabled>同步主体推送配置</el-button>
          <el-button type="info" disabled>复制其他保单责任配置</el-button>
          <el-button type="info" disabled>批量导入责任配置</el-button>
        </div>
      </div>
      <el-table
        v-loading="tableLoading"
        :data="liabilityMappingList"
        border
        @selection-change="handleSelectionChange"
      >
        <el-table-column label="序号（选择）" type="selection" align="center" />
        <el-table-column label="责任名称" prop="liabilityName" align="center" />
        <el-table-column
          label="发票医疗类型"
          prop="invoiceMedicalTypeCN"
          align="center"
        />
        <el-table-column
          label="保司险种代码"
          prop="insuranceCompanyCoverage"
          align="center"
        >
          <template #default="{ row }">
            <el-input
              v-if="row.isEditing"
              v-model="row.insuranceCompanyCoverage"
            />
            <span v-else>{{ row.insuranceCompanyCoverage }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="保司责任代码"
          prop="insuranceCompanyLiability"
          align="center"
        >
          <template #default="{ row }">
            <el-input
              v-if="row.isEditing"
              v-model="row.insuranceCompanyLiability"
            />
            <span v-else>{{ row.insuranceCompanyLiability }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="保司责任子码"
          prop="insuranceCompanyLiabilitySub"
          align="center"
        >
          <template #default="{ row }">
            <el-input
              v-if="row.isEditing"
              v-model="row.insuranceCompanyLiabilitySub"
            />
            <span v-else>{{ row.insuranceCompanyLiabilitySub }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="索赔事故性质"
          prop="claimAccident"
          align="center"
        >
          <template #default="{ row }">
            <el-input v-if="row.isEditing" v-model="row.claimAccident" />
            <span v-else>{{ row.claimAccident }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="保司个账公账"
          prop="insuranceCompanyAccount"
          align="center"
        >
          <template #default="{ row }">
            <el-input
              v-if="row.isEditing"
              v-model="row.insuranceCompanyAccount"
            />
            <span v-else>{{ row.insuranceCompanyAccount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作人员" align="center">
          <template #default="{ row }">
            {{ row.updateUser ?? row.createUser }}
          </template>
        </el-table-column>
        <el-table-column label="创建时间" prop="createTime" align="center" />
        <el-table-column
          label="最近更新时间"
          prop="updateTime"
          align="center"
        />
        <el-table-column label="操作" align="center">
          <template #default="{ row }">
            <el-button
              v-if="!row.isEditing"
              type="primary"
              link
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <template v-else>
              <el-button
                type="success"
                icon="Check"
                link
                :loading="saveLoading"
                @click="handleSave(row)"
              >
                保存
              </el-button>
              <el-button
                type="warning"
                icon="Close"
                link
                @click="handleCancel(row)"
              >
                放弃
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">关闭</el-button>
      </span>
    </template>

    <BatchUpdateDialog
      ref="batchUpdateDialogRef"
      @confirm="queryLiabilityMappingList"
    />
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 60vh;
  padding: 20px;
  overflow-y: auto;
}
</style>
