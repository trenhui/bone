<script setup lang="ts">
import ClaimAPI, { IPersonInfo } from "@/api/claim";
import EventBus from "@/utils/eventBus";
defineOptions({
  name: "PersonInfoDialog",
});

const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  claimId: string;
}>();

const dataType = ref<number>(1);
const personInfo = ref<IPersonInfo>({
  primaryInsuredName: "",
  primaryInsuredIdNumber: "",
  primaryInsuredIdType: "",
  primaryInsuredIdValidityPeriod: [],
  collectName: "",
  collectIdNumber: "",
  collectIdType: "",
  collectIdValidityPeriod: [],
  collectContact: "",
  collectAddress: "",
  collectBankName: "",
  collectBankAccount: "",
});

const getPersonInfo = async () => {
  const res = await ClaimAPI.queryPersonInfo({
    claimNo: props.claimId,
    dataType: dataType.value,
  });
  personInfo.value = res;
};

watch(
  dialogVisible,
  (newVal) => {
    if (newVal) {
      getPersonInfo();
    }
  },
  { immediate: true }
);

const onClose = () => {
  dialogVisible.value = false;
  dataType.value = 1;
};

const handleImport = () => {
  const importPersonInfo = {
    collectName: personInfo.value.collectName,
    collectIdNumber: personInfo.value.collectIdNumber,
    collectIdType: personInfo.value.collectIdType,
    collectIdValidityPeriod: personInfo.value.collectIdValidityPeriod,
    collectContact: personInfo.value.collectContact,
    collectAddress: personInfo.value.collectAddress,
    collectBankName: personInfo.value.collectBankName,
    collectBankAccount: personInfo.value.collectBankAccount,
  };

  onClose();
  EventBus.emit(`claim:importCollectPersonInfo`, importPersonInfo);
};

const handleChangeDataType = () => {
  getPersonInfo();
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    title="个人信息"
    width="35%"
    top="5vh"
    :close-on-click-modal="false"
  >
    <div class="content">
      <el-radio-group
        v-model="dataType"
        @change="handleChangeDataType"
        style="margin-bottom: 20px"
      >
        <el-radio-button :value="1">历史赔案信息</el-radio-button>
        <el-radio-button :value="2">承保信息</el-radio-button>
        <el-radio-button :value="3">录入信息</el-radio-button>
      </el-radio-group>

      <el-form label-width="auto">
        <el-form-item label="主被保险人姓名">
          <el-input v-model="personInfo.primaryInsuredName" disabled />
        </el-form-item>
        <el-form-item label="主被证件类型">
          <el-input v-model="personInfo.primaryInsuredIdType" disabled />
        </el-form-item>
        <el-form-item label="主被证件号码">
          <el-input v-model="personInfo.primaryInsuredIdNumber" disabled />
        </el-form-item>
        <el-form-item label="主被证件有效期">
          <el-date-picker
            v-model="personInfo.primaryInsuredIdValidityPeriod"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            disabled
          />
        </el-form-item>
        <el-form-item label="领款人姓名">
          <el-input v-model="personInfo.collectName" disabled />
        </el-form-item>
        <el-form-item label="领款人证件类型">
          <el-input v-model="personInfo.collectIdType" disabled />
        </el-form-item>
        <el-form-item label="领款人证件号码">
          <el-input v-model="personInfo.collectIdNumber" disabled />
        </el-form-item>
        <el-form-item label="领款人证件有效期">
          <el-date-picker
            v-model="personInfo.collectIdValidityPeriod"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            disabled
          />
        </el-form-item>
        <el-form-item label="领款人联系方式">
          <el-input v-model="personInfo.collectContact" disabled />
        </el-form-item>
        <el-form-item label="领款人地址">
          <el-input v-model="personInfo.collectAddress" disabled />
        </el-form-item>
        <el-form-item label="领款人开户行">
          <el-input v-model="personInfo.collectBankName" disabled />
        </el-form-item>
        <el-form-item label="领款人银行卡号">
          <el-input v-model="personInfo.collectBankAccount" disabled />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="flex justify-center">
        <el-button type="primary" @click="handleImport">一键导入</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.content {
  max-height: 600px;
  padding: 10px;
  overflow-y: auto;
}
</style>
