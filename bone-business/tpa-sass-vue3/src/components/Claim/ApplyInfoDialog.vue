<script setup lang="ts">
import ClaimAPI, { IApplyInfo } from "@/api/claim";
defineOptions({
  name: "HangupDialog",
});

const emit = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  claimId: {
    type: String,
    default: "",
  },
});

const applyInfo = ref<IApplyInfo>({
  applyName: "",
  applyIdentityNo: "",
  applyType: "",
  applyTime: "",
  applyPhone: "",
  outInsureName: "",
  outInsureIdentityNo: "",
  outInsureTime: "",
  policyNo: "",
  collectName: "",
  accountNo: "",
  bankName: "",
  bankAddress: "",
});

const getApplyInfo = async () => {
  const res = await ClaimAPI.applyInfo(props.claimId);
  applyInfo.value = res;
};

const onClose = () => {
  dialogVisible.value = false;
  emit("close");
};

onMounted(() => {
  getApplyInfo();
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    title="查看报案信息"
    width="30%"
    :close-on-click-modal="false"
  >
    <div class="p-2 max-h-[500px] overflow-y-auto">
      <el-form label-width="auto" label-position="left">
        <p>申请信息</p>
        <el-form-item label="申请人姓名">
          <el-input v-model="applyInfo.applyName" disabled />
        </el-form-item>
        <el-form-item label="申请人身份证">
          <el-input v-model="applyInfo.applyIdentityNo" disabled />
        </el-form-item>
        <el-form-item label="申请类型">
          <el-input v-model="applyInfo.applyType" disabled />
        </el-form-item>
        <el-form-item label="申请时间">
          <el-input v-model="applyInfo.applyTime" disabled />
        </el-form-item>
        <el-form-item label="联系方式">
          <el-input v-model="applyInfo.applyPhone" disabled />
        </el-form-item>
        <p>报案信息</p>
        <el-form-item label="出险人姓名">
          <el-input v-model="applyInfo.outInsureName" disabled />
        </el-form-item>
        <el-form-item label="出险人证件号">
          <el-input v-model="applyInfo.outInsureIdentityNo" disabled />
        </el-form-item>
        <el-form-item label="出险时间">
          <el-input v-model="applyInfo.outInsureTime" disabled />
        </el-form-item>
        <el-form-item label="保单号">
          <el-input v-model="applyInfo.policyNo" disabled />
        </el-form-item>
        <el-form-item label="领款人姓名">
          <el-input v-model="applyInfo.collectName" disabled />
        </el-form-item>
        <el-form-item label="银行卡号">
          <el-input v-model="applyInfo.accountNo" disabled />
        </el-form-item>
        <el-form-item label="开户行">
          <el-input v-model="applyInfo.bankName" disabled />
        </el-form-item>
        <el-form-item label="开户行地址">
          <el-input v-model="applyInfo.bankAddress" disabled />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="flex justify-center">
        <el-button @click="onClose">取消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped></style>
