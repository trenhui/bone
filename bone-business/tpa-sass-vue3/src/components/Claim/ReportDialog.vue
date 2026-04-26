<script lang="ts">
const outInsureIdentityTypeOptions = [
  { label: "居民身份证" },
  { label: "护照" },
  { label: "学生证" },
  { label: "军官证" },
  { label: "其他" },
  { label: "户口本" },
  { label: "港澳通行证" },
  { label: "台湾通行证" },
  { label: "港澳台居民居住证" },
  { label: "武警身份证明" },
  { label: "组织机构代码" },
  { label: "社会信用代码" },
  { label: "外国人永久居留身份证" },
  { label: "台湾居民往来大陆通行证" },
  { label: "澳门居民身份证" },
  { label: "香港(永久性)居民身份证" },
];

const outInsureGenderOptions = [{ label: "男" }, { label: "女" }];

const outInsureIdentityOptions = [
  { label: "被保险人" },
  { label: "附属被保险人" },
  { label: "不记名附属被保险人" },
];

const relationTypeOptions = [
  { label: "其他" },
  { label: "本人" },
  { label: "配偶" },
  { label: "子女" },
  { label: "父母" },
  { label: "代理人" },
  { label: "委托人" },
];

const responsibilityTypeOptions = [
  {
    value: "P",
    label: "普通责任",
  },
  {
    value: "C",
    label: "公共责任",
  },
];

const diseaseReasonOptions = [
  {
    label: "A00-B99",
  },
  {
    label: "Z00-Z99",
  },
  {
    label: "Q00-Q99",
  },
  {
    label: "U00-U99",
  },
  {
    label: "E00-E90",
  },
  {
    label: "J00-J99",
  },
  {
    label: "S00-T98",
  },
  {
    label: "O00-O99",
  },
  {
    label: "I00-I99",
  },
  {
    label: "R00-R99",
  },
  {
    label: "V01-Y98",
  },
  {
    label: "N00-N99",
  },
  {
    label: "K00-K93",
  },
  {
    label: "L00-L99",
  },
  {
    label: "H00-H59",
  },
  {
    label: "G00-G99",
  },
  {
    label: "F00-F99",
  },
  {
    label: "H60-H95",
  },
  {
    label: "M00-M99",
  },
  {
    label: "C00-D48",
  },
  {
    label: "P00-P96",
  },
  {
    label: "D50-D89",
  },
];

const payoutAmountTypeOptions = [
  {
    label: "发票总金额-统筹",
    value: "noPoolingAmount",
  },
  {
    label: "发票总金额",
    value: "totalAmount",
  },

  {
    label: "自定义",
    value: "custom",
  },
];
</script>

<script setup lang="ts">
import ClaimAPI, { IReportInfo } from "@/api/claim";
import { ElForm } from "element-plus";
defineOptions({
  name: "ReportDialog",
});

const emit = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  claimId: {
    type: String,
    default: "",
  },
});

const reportFormRef = ref<InstanceType<typeof ElForm>>();
const loading = ref(false);
const reportInfo = ref<IReportInfo>({
  policyNo: "",
  outInsureName: "",
  outInsureIdentityType: "",
  outInsureIdentityNo: "",
  outInsureIdentity: "",
  outInsureGender: "",
  outInsureAge: 0,
  outInsureBirthDay: "",
  reportName: "",
  reportPhone: "",
  contactName: "",
  contactPhone: "",
  outInsureAddress: "",
  outInsureDetail: "",
  outInsureTime: "",
  relationType: "",
  diseaseReason: "",
  responsibilityType: "",
  kindCodeTypes: [],
  kindCode: "",
  itemCodeTypes: [],
  itemCode: "",
  secondaryItemCodeTypes: [],
  secondaryItemCode: "",
  payoutAmountMap: {
    noPoolingAmount: 0,
    totalAmount: 0,
    custom: 0,
  },
  payoutAmountType: "",
});

const computedPayoutAmount = computed({
  get: () => {
    return (
      reportInfo.value.payoutAmountMap[
        reportInfo.value
          .payoutAmountType as keyof typeof reportInfo.value.payoutAmountMap
      ] || undefined
    );
  },
  set: (value) => {
    // 只能修改custom的值
    if (reportInfo.value.payoutAmountType === "custom") {
      reportInfo.value.payoutAmountMap.custom = value || 0;
    }
    // 其他情况不修改
    else {
      return;
    }
  },
});

const computedItemCodeTypes = computed(() => {
  return reportInfo.value.itemCodeTypes.filter(
    (item) => item.parentCode === reportInfo.value.kindCode
  );
});

const computedSecondaryItemCodeTypes = computed(() => {
  return reportInfo.value.secondaryItemCodeTypes.filter(
    (item) => item.parentCode === reportInfo.value.itemCode
  );
});

const handleKindCodeChange = (value: string) => {
  reportInfo.value.itemCode = "";
  reportInfo.value.secondaryItemCode = "";
};

const handleItemCodeChange = (value: string) => {
  reportInfo.value.secondaryItemCode = "";
};

const reportRules = {
  outInsureIdentity: [
    { required: true, message: "请选择出险人身份", trigger: "change" },
  ],
  outInsureGender: [
    { required: true, message: "请选择出险人性别", trigger: "change" },
  ],
  outInsureAge: [
    { required: true, message: "请输入出险人年龄", trigger: "blur" },
  ],
  outInsureBirthDay: [
    { required: true, message: "请输入出险人出生日期", trigger: "blur" },
  ],
  outInsureTime: [
    { required: true, message: "请输入出险时间", trigger: "blur" },
  ],
  relationType: [
    { required: true, message: "请选择报案人与出险人关系", trigger: "blur" },
  ],
  kindCode: [{ required: true, message: "请选择条款代码", trigger: "blur" }],
  itemCode: [
    { required: true, message: "请选择一级责任代码", trigger: "blur" },
  ],
  secondaryItemCode: [
    { required: true, message: "请选择二级责任代码", trigger: "blur" },
  ],
  diseaseReason: [
    { required: true, message: "请输入疾病原因", trigger: "blur" },
  ],
};

const getReportInfo = async () => {
  const res = await ClaimAPI.getReportClaimCondition(props.claimId);
  reportInfo.value = res;
};

const onClose = () => {
  dialogVisible.value = false;
  reportInfo.value = {
    policyNo: "",
    outInsureName: "",
    outInsureIdentityType: "",
    outInsureIdentityNo: "",
    outInsureIdentity: "",
    outInsureGender: "",
    outInsureAge: 0,
    outInsureBirthDay: "",
    reportName: "",
    reportPhone: "",
    contactName: "",
    contactPhone: "",
    outInsureAddress: "",
    outInsureDetail: "",
    outInsureTime: "",
    relationType: "",
    diseaseReason: "",
    responsibilityType: "",
    kindCodeTypes: [],
    kindCode: "",
    itemCodeTypes: [],
    itemCode: "",
    secondaryItemCodeTypes: [],
    secondaryItemCode: "",
    payoutAmountMap: {
      noPoolingAmount: 0,
      totalAmount: 0,
      custom: 0,
    },
    payoutAmountType: "",
  };
  loading.value = false;
  reportFormRef.value?.resetFields();
  emit("close");
};

const handleConfirm = async () => {
  if (reportFormRef.value) {
    const valid = await reportFormRef.value.validate();
    if (!valid) {
      return;
    }
  }

  try {
    loading.value = true;
    await ClaimAPI.reportClaim(reportInfo.value);
    ElMessage.success("报案成功");
    loading.value = false;
    onClose();
    setTimeout(() => {
      window.location.reload();
    }, 1000);
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  getReportInfo();
});
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    title="报案"
    top="5vh"
    width="35%"
    :close-on-click-modal="false"
  >
    <div class="p-2 max-h-[600px] overflow-y-auto">
      <el-form
        ref="reportFormRef"
        :model="reportInfo"
        :rules="reportRules"
        label-width="auto"
        label-position="right"
      >
        <el-form-item label="保单号">
          {{ reportInfo.policyNo }}
        </el-form-item>
        <el-form-item label="出险人姓名">
          {{ reportInfo.outInsureName }}
        </el-form-item>
        <el-form-item label="出险人证件类型">
          <el-select v-model="reportInfo.outInsureIdentityType">
            <el-option
              v-for="item in outInsureIdentityTypeOptions"
              :key="item.label"
              :label="item.label"
              :value="item.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="出险人证件号码">
          {{ reportInfo.outInsureIdentityNo }}
        </el-form-item>
        <el-form-item label="报案人姓名">
          {{ reportInfo.reportName }}
        </el-form-item>
        <el-form-item label="报案人电话">
          {{ reportInfo.reportPhone }}
        </el-form-item>
        <el-form-item label="联系人姓名">
          {{ reportInfo.contactName }}
        </el-form-item>
        <el-form-item label="联系人电话">
          {{ reportInfo.contactPhone }}
        </el-form-item>
        <el-form-item label="出险地点">
          {{ reportInfo.outInsureAddress }}
        </el-form-item>
        <el-form-item label="出险经过">
          {{ reportInfo.outInsureDetail }}
        </el-form-item>
        <el-form-item label="出险人身份" prop="outInsureIdentity">
          <el-select v-model="reportInfo.outInsureIdentity">
            <el-option
              v-for="item in outInsureIdentityOptions"
              :key="item.label"
              :label="item.label"
              :value="item.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="出险人性别" prop="outInsureGender">
          <el-select v-model="reportInfo.outInsureGender">
            <el-option
              v-for="item in outInsureGenderOptions"
              :key="item.label"
              :label="item.label"
              :value="item.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="出险人年龄" prop="outInsureAge">
          <el-input-number
            :controls="false"
            :precision="0"
            v-model="reportInfo.outInsureAge"
            :min="0"
          />
        </el-form-item>
        <el-form-item label="出险人出生日期" prop="outInsureBirthDay">
          <el-date-picker
            style="width: 100%"
            v-model="reportInfo.outInsureBirthDay"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择出险人出生日期"
          />
        </el-form-item>
        <el-form-item label="出险时间" prop="outInsureTime">
          <el-date-picker
            style="width: 100%"
            v-model="reportInfo.outInsureTime"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择出险时间"
          />
        </el-form-item>
        <el-form-item label="报案人与出险人关系" prop="relationType">
          <el-select v-model="reportInfo.relationType">
            <el-option
              v-for="item in relationTypeOptions"
              :key="item.label"
              :label="item.label"
              :value="item.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="疾病原因" prop="diseaseReason">
          <el-select v-model="reportInfo.diseaseReason">
            <el-option
              v-for="item in diseaseReasonOptions"
              :key="item.label"
              :label="item.label"
              :value="item.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="责任类型" prop="responsibilityType">
          <el-select v-model="reportInfo.responsibilityType">
            <el-option
              v-for="item in responsibilityTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="条款代码" prop="kindCode">
          <el-select
            v-model="reportInfo.kindCode"
            @change="handleKindCodeChange"
          >
            <el-option
              v-for="item in reportInfo.kindCodeTypes"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="一级责任代码" prop="itemCode">
          <el-select
            v-model="reportInfo.itemCode"
            @change="handleItemCodeChange"
          >
            <el-option
              v-for="item in computedItemCodeTypes"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="二级责任代码" prop="secondaryItemCode">
          <el-select v-model="reportInfo.secondaryItemCode">
            <el-option
              v-for="item in computedSecondaryItemCodeTypes"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="估损金额" prop="payoutAmountMap.custom">
          <div class="flex items-center w-90% gap-2">
            <el-select class="flex-1" v-model="reportInfo.payoutAmountType">
              <el-option
                v-for="item in payoutAmountTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <el-input-number
              class="w-120px"
              v-model="computedPayoutAmount"
              :controls="false"
              :precision="2"
              :min="0"
              :disabled="reportInfo.payoutAmountType !== 'custom'"
            />
          </div>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="flex justify-center">
        <el-button type="primary" @click="handleConfirm" :loading="loading">
          确认
        </el-button>
        <el-button @click="onClose">取消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped></style>
