<script setup lang="ts">
import RestrictObject from "./section/RestrictObject.vue";
import RestrictScope from "./section/RestrictScope.vue";
import RestricOutInsure from "./section/RestricOutInsure.vue";
import LiabilityDeduct from "./section/LiabilityDeduct.vue";
import TimesLimit from "./section/TimesLimit.vue";
import AdjustmentDetail from "./section/AdjustmentDetail.vue";
import PayPercent from "./section/PayPercent.vue";
import LiabilityType from "./section/LiabilityType.vue";
import { mergeObjects } from "@/utils/objectUtils";
import LiabilityAPI, {
  LiabilityDTO,
  CoverageDTO,
  PlanDTO,
} from "@/api/liability";
import LiabilityLimit from "./section/LiabilityLimit.vue";
defineOptions({
  name: "EditLiabilityDialog",
});

const emits = defineEmits(["close", "confirm"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  id: string;
}>();

const liabilityDeductRef = ref<InstanceType<typeof LiabilityDeduct>>();
const liabilityLimitRef = ref<InstanceType<typeof LiabilityLimit>>();
const timesLimitRef = ref<InstanceType<typeof TimesLimit>>();
const adjustmentDetailRef = ref<InstanceType<typeof AdjustmentDetail>>();
const restricOutInsureRef = ref<InstanceType<typeof RestricOutInsure>>();

const plan = ref<PlanDTO>({
  id: "",
  /** 保单号 */
  policyNo: "",
  /** 计划名称 */
  planName: "",
  /** 计划code */
  planCode: "",
  /** 计划额度 */
  planLimit: -1,
});
const coverage = ref<CoverageDTO>({
  id: "",
  /** 保单号 */
  policyNo: "",
  /** 关联的计划id */
  planId: "",
  /** 险种名称 */
  coverageName: "",
  /** 险种code */
  coverageCode: "",
  /** 险种额度 */
  coverageLimit: -1,
});
const liabilityDetail = ref<LiabilityDTO>({
  /** id */
  id: "",
  /** 责任UUID */
  uuid: "",
  /** 保单号 */
  policyNo: "",
  /** 关联的计划id */
  planId: "",
  /** 关联的险种Id */
  coverageId: "",
  /** 责任名称 */
  liabilityName: "",
  /** 责任形式 */
  liabilityType: "REIMBURSEMENT",
  /** 版本 */
  version: "",
  /** 参与的共保关系列表数组内容是共保关系的id */
  shareId: [],
  /** 后付责任的uuid */
  nextLiabilityUuid: "",
  /** 后付关系的类型TYPE1, 先付责任比例外也赔TYPE2, 先付责任比例外不赔TYPE3, 先付责任为0才赔 */
  nextLiabilityType: "",
  /** 是否能被设置为发票关联责任 */
  invoiceRelateAble: false,
  /** 给付依据（定额给付使用）SEVERE重疾标识DISEASE疾病种类DISABILITY失能标识 */
  paymentBasis: "",
  /** 津贴细则（津贴给付使用） */
  allowanceDetail: {
    /** 开始日期非保险或等待期期间怎么处理 */
    startOutPeriod: "",
    /** 日津贴金额 */
    allowancePerDay: 0,
    /** 期间天数上限 */
    inPeriodLimit: 0,
    /** 期满天数上限 */
    outPeriodLimit: 0,
    /** 津贴天数计算方式 */
    dayCountOption: "",
  },
  /** 适用对象 不指定时为null */
  restrictObject: null,
  /** 适用限定 */
  restrictScope: null,
  /** 适用出险 */
  restrictOutInsure: {
    type: [],
    visitType: [],
    medicalInsurance: "",
    invoiceFeeType: [],
    liabilityFeeType: [],
    accidentType: [],
    disabilityLevel: "",
  },
  /** 等待期。不需要等待时输入-1。 */
  waitingPeriod: -1,
  /** 等待期类型 */
  waitingPeriodType: -1,
  /** 赔付比例 */
  payPercent: {
    type: "SAME",
    percent: 0,
    factor: [],
    rangeMap: {},
  },
  /** 责任免赔 */
  liabilityDeduct: null,
  /** 次期限额 */
  timesLimit: null,
  /** 责任账户类型PERSONAL个账PUBLIC公账 */
  accountType: "",
  /** 控额方式 */
  quotaController: {
    type: "",
    policyNo: "",
  },
  /** 保额类型PRESET 预设保额PERSONAL 个单保额（本次暂不包含） */
  insuranceQuotaType: "PRESET",
  /** 责任额度 */
  liabilityLimit: {
    type: "",
    liabilityLimit: null,
    outpatientEmergencyLimit: null,
    inpatientLimit: null,
    pharmacyLimit: null,
    specialClinicLimit: null,
    fixedAmount: null,
  },
  /** 理算信息 */
  adjustmentDetail: [],
  /** 理算公式 */
  formula: "",
  /** 备注 */
  remark: "",
});
const confirmLoading = ref(false);

const init = async () => {
  try {
    const res = await LiabilityAPI.queryLiabilityDetail(props.id);
    console.log("res", res);

    // 深拷贝初始默认值，避免直接引用
    const defaultLiabilityDetail = JSON.parse(
      JSON.stringify(liabilityDetail.value)
    );
    // 合并API返回的数据与默认值
    liabilityDetail.value = mergeObjects(
      defaultLiabilityDetail,
      res.liabilityConfig
    );
    console.log(liabilityDetail.value);
    coverage.value = res.coverageDTO;
    plan.value = res.planDTO;

    // 如果保单号不为空，且直付保单号为空，则设置直付保单号
    if (
      plan.value.policyNo &&
      !liabilityDetail.value.quotaController.policyNo
    ) {
      liabilityDetail.value.quotaController.policyNo = plan.value.policyNo;
    }

    if (liabilityDetail.value.waitingPeriod === -1) {
      liabilityDetail.value.waitingPeriodType = -1;
    } else {
      liabilityDetail.value.waitingPeriodType = 1;
    }
  } catch (error) {
    console.log("error", error);
  }
};

watch(
  () => dialogVisible.value,
  () => {
    if (dialogVisible.value) {
      init();
    }
  },
  { immediate: true }
);

const handleClose = () => {
  dialogVisible.value = false;
};

const handleQuotaControllerTypeChange = (value: any) => {
  if (value === "TPA") {
    liabilityDetail.value.accountType = "NO_ACCOUNT";
  } else {
    liabilityDetail.value.accountType = "PERSONAL";
  }
};

const validate = () => {
  // 适用对象/适用限定/适用出险/赔付比例/责任免赔/控额方/保额类型/控额方式 都必填

  if (!liabilityDetail.value.restrictOutInsure.type?.length) {
    return { valid: false, message: "请选择适用出险" };
  }

  if (!liabilityDetail.value.payPercent.type) {
    return { valid: false, message: "请选择赔付比例" };
  }

  if (!liabilityDetail.value.quotaController.type) {
    return { valid: false, message: "请选择控额方" };
  }

  if (!liabilityDetail.value.liabilityLimit.type) {
    return { valid: false, message: "请选择控额方式" };
  }

  return { valid: true };
};

const handleConfirm = async () => {
  try {
    confirmLoading.value = true;

    // 校验feeType名称
    if (restricOutInsureRef.value) {
      const validateResult = restricOutInsureRef.value.validate();
      if (!validateResult.valid) {
        ElMessage.error(validateResult.message);
        confirmLoading.value = false;
        return;
      }
    }

    // 校验免赔数据
    if (liabilityDeductRef.value) {
      const validateResult = liabilityDeductRef.value.validate();
      if (!validateResult.valid) {
        ElMessage.error(validateResult.message);
        confirmLoading.value = false;
        return;
      }
    }

    // 校验额度数据
    if (liabilityLimitRef.value) {
      const validateResult = liabilityLimitRef.value.validate();
      if (!validateResult.valid) {
        ElMessage.error(validateResult.message);
        confirmLoading.value = false;
        return;
      }
    }

    // 校验次期限额
    if (timesLimitRef.value) {
      const validateResult = timesLimitRef.value.validate();
      if (!validateResult.valid) {
        ElMessage.error(validateResult.message);
        confirmLoading.value = false;
        return;
      }
    }

    // 校验理算信息
    if (adjustmentDetailRef.value) {
      const validateResult = adjustmentDetailRef.value.validate();
      if (!validateResult.valid) {
        ElMessage.error(validateResult.message);
        confirmLoading.value = false;
        return;
      }
    }

    // 必填校验
    const validateResult = validate();
    if (!validateResult.valid) {
      ElMessage.error(validateResult.message);
      confirmLoading.value = false;
      return;
    }

    if (liabilityDetail.value.waitingPeriodType === -1) {
      liabilityDetail.value.waitingPeriod = -1;
    }

    await LiabilityAPI.saveLiabilityConfig(liabilityDetail.value);

    ElMessage.success("保存成功");
    handleClose();
  } catch (error) {
    console.log("error", error);
  } finally {
    confirmLoading.value = false;
  }
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    append-to-body
    :close-on-click-modal="false"
    title="配置责任规则"
    width="70%"
    top="5vh"
  >
    <div class="dialog-content">
      <el-table border :data="[1]">
        <el-table-column label="保单号" align="center">
          <template #default>
            {{ plan.policyNo }}
          </template>
        </el-table-column>
        <el-table-column label="计划名称" align="center">
          <template #default>
            {{ plan.planName }}
          </template>
        </el-table-column>
        <el-table-column label="险种名称" align="center">
          <template #default>
            {{ coverage.coverageName }}
          </template>
        </el-table-column>
      </el-table>

      <el-divider />

      <el-form label-width="80px" :model="liabilityDetail">
        <el-form-item label="责任名称" class="w-50%">
          <el-input v-model="liabilityDetail.liabilityName" />
        </el-form-item>

        <liability-type v-model:liability="liabilityDetail" />

        <el-divider content-position="left">
          <span>适用范围</span>
          <el-button type="primary" size="small" link class="ml-2">
            查看说明
          </el-button>
        </el-divider>

        <restrict-object v-model:liability="liabilityDetail" />

        <restrict-scope v-model:liability="liabilityDetail" />

        <restric-out-insure
          ref="restricOutInsureRef"
          v-model:liability="liabilityDetail"
        />

        <el-divider content-position="left">
          <span>赔付规则</span>
          <el-button type="primary" size="small" link class="ml-2">
            查看说明
          </el-button>
        </el-divider>

        <el-form-item label="等待期">
          <el-radio-group v-model="liabilityDetail.waitingPeriodType">
            <el-radio :value="-1">无等待期</el-radio>
            <el-radio :value="1" disabled>有等待期</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="liabilityDetail.waitingPeriodType === 1"
          label="等待期天数"
          label-width="90px"
          class="ml-20"
        >
          <el-input-number
            v-model="liabilityDetail.waitingPeriod"
            :min="0"
            placeholder="请输入天数"
            controls-position="right"
          >
            <template #suffix>天</template>
          </el-input-number>
        </el-form-item>

        <pay-percent v-model:liability="liabilityDetail" />

        <liability-deduct
          ref="liabilityDeductRef"
          v-model:liability="liabilityDetail"
        />

        <times-limit ref="timesLimitRef" v-model:liability="liabilityDetail" />

        <el-divider content-position="left">
          <span>控额规则</span>
          <el-button type="primary" size="small" link class="ml-2">
            查看说明
          </el-button>
        </el-divider>

        <el-form-item label="控额方">
          <el-radio-group
            v-model="liabilityDetail.quotaController.type"
            @change="handleQuotaControllerTypeChange"
          >
            <el-radio value="TPA">普康TPA控额</el-radio>
            <el-radio value="DIRECT">普康直付控额</el-radio>
            <el-radio value="INSURER">保司三方控额</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          v-if="liabilityDetail.quotaController.type === 'DIRECT'"
          label="直付保单"
          class="ml-20 w-50%"
        >
          <el-input
            v-model="liabilityDetail.quotaController.policyNo"
            placeholder="请输入保单号"
          />
        </el-form-item>
        <el-form-item label="责任账户类型" label-width="100px">
          <el-radio-group v-model="liabilityDetail.accountType">
            <el-radio
              v-if="liabilityDetail.quotaController.type !== 'DIRECT'"
              value="NO_ACCOUNT"
            >
              无账户
            </el-radio>
            <el-radio value="PERSONAL">个账</el-radio>
            <el-radio value="PUBLIC">公账</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="保额类型">
          <el-radio-group v-model="liabilityDetail.insuranceQuotaType">
            <el-radio value="PRESET">预设保额</el-radio>
            <el-radio value="PERSONAL" disabled>个单保额</el-radio>
          </el-radio-group>
        </el-form-item>

        <liability-limit
          ref="liabilityLimitRef"
          v-model:liability="liabilityDetail"
        />

        <el-divider content-position="left">
          <span>理算信息</span>
        </el-divider>

        <adjustment-detail
          ref="adjustmentDetailRef"
          v-model:liability="liabilityDetail"
        />

        <div class="mt-5">
          <el-form-item label="责任描述">
            <el-input
              v-model="liabilityDetail.remark"
              type="textarea"
              :rows="3"
              placeholder="可选，填写保司赔付条款，方便后续验证。文字或图片。"
            />
          </el-form-item>
        </div>
      </el-form>
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确认</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 76vh;
  padding: 10px 20px 20px;
  overflow-y: auto;
}

// :deep(.el-table th.el-table__cell) {
//   background-color: $table-header-bg-color;
// }

:deep(.el-divider--horizontal) {
  margin: 40px 0;
}
</style>
