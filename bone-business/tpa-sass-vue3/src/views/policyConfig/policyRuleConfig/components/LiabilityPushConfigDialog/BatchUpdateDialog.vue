<script setup lang="ts">
import LiabilityAPI, { LiabilityPushConfigDTO } from "@/api/liability";

const emits = defineEmits(["close", "confirm"]);
const dialogVisible = ref(false);
const loading = ref(false);
const formData = ref({
  insuranceCompanyCoverage: "",
  insuranceCompanyLiability: "",
  insuranceCompanyLiabilitySub: "",
  claimAccident: "",
  insuranceCompanyAccount: "",
});
const handleClose = () => {
  dialogVisible.value = false;
  formData.value = {
    insuranceCompanyCoverage: "",
    insuranceCompanyLiability: "",
    insuranceCompanyLiabilitySub: "",
    claimAccident: "",
    insuranceCompanyAccount: "",
  };
};

const selectedRows = ref<LiabilityPushConfigDTO[]>([]);
const open = (rows: LiabilityPushConfigDTO[]) => {
  dialogVisible.value = true;
  selectedRows.value = rows;
};

const handleConfirm = async () => {
  try {
    loading.value = true;
    await LiabilityAPI.batchUpdateLiabilityMapping(
      selectedRows.value.map((item) => ({
        id: item.id,
        insuranceCompanyCoverage: formData.value.insuranceCompanyCoverage,
        insuranceCompanyLiability: formData.value.insuranceCompanyLiability,
        insuranceCompanyLiabilitySub:
          formData.value.insuranceCompanyLiabilitySub,
        claimAccident: formData.value.claimAccident,
        insuranceCompanyAccount: formData.value.insuranceCompanyAccount,
      }))
    );
    ElMessage.success("保存成功");
    emits("confirm");
    handleClose();
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const getSelectedRowsText = () => {
  return selectedRows.value
    .map((item) => `${item.liabilityName}、${item.invoiceMedicalTypeCN}`)
    .join("；");
};

defineExpose({
  open,
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    append-to-body
    :close-on-click-modal="false"
    title="责任推送配置"
    width="60%"
  >
    <div class="dialog-content">
      <div>已选责任：{{ getSelectedRowsText() }}</div>
      <el-divider />
      <div class="mb-3">
        请填写需要修改的代码，无需修改则不填写保留各责任的原有代码。
      </div>
      <el-table :data="[1]" border>
        <el-table-column label="保司险种代码" align="center">
          <template #default>
            <el-input v-model="formData.insuranceCompanyCoverage" />
          </template>
        </el-table-column>
        <el-table-column label="保司责任代码" align="center">
          <template #default>
            <el-input v-model="formData.insuranceCompanyLiability" />
          </template>
        </el-table-column>
        <el-table-column label="保司责任子码" align="center">
          <template #default>
            <el-input v-model="formData.insuranceCompanyLiabilitySub" />
          </template>
        </el-table-column>
        <el-table-column label="索赔事故性质" align="center">
          <template #default>
            <el-input v-model="formData.claimAccident" />
          </template>
        </el-table-column>
        <el-table-column label="保司个账公账">
          <template #default>
            <el-input v-model="formData.insuranceCompanyAccount" />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">关闭</el-button>
        <el-button type="primary" :loading="loading" @click="handleConfirm">
          确定
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 60vh;
  padding: 20px;
  overflow-y: auto;
}
</style>
