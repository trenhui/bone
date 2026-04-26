<script lang="ts">
import type { CascaderOption } from "element-plus";

const RejectTypeList = [
  {
    label: "银行信息错误",
    children: [{ label: "银行卡号错误" }, { label: "开户行错误" }],
  },
  {
    label: "保单责任错误",
    children: [
      { label: "门诊、门规、重疾责任选错" },
      { label: "个账、公账赔错" },
    ],
  },
  {
    label: "重复赔付",
    children: [{ label: "发票重复赔付，案件重复赔付" }],
  },
  {
    label: "扣费错误",
    children: [{ label: "责免项扣费错误" }, { label: "乙类、自费扣费错误" }],
  },
  {
    label: "复制发票/赔案",
    children: [{ label: "系统点击修改信息才能通过" }],
  },
  {
    label: "赔付超一万元反洗钱缺少9要素",
    children: [{ label: "缺少身份证明、常驻地址、职业等" }],
  },
  {
    label: "发票审核错误",
    children: [
      { label: "非本人发票" },
      { label: "发票号码录入错误" },
      { label: "发票金额错误" },
      { label: "假发票" },
      { label: "发票少盖章" },
    ],
  },
  {
    label: "疾病原因错误",
    children: [{ label: "疾病原因写错" }, { label: "疾病诊断错误" }],
  },
  {
    label: "除外责任错误",
    children: [{ label: "除外责任项未扣除" }, { label: "非除外责任项扣除了" }],
  },
  {
    label: "拒赔原因错误",
    children: [{ label: "拒付原因写错、漏写" }],
  },
  {
    label: "理赔材料审核错误",
    children: [{ label: "理赔材料缺少" }],
  },
  {
    label: "账户余额不足",
    children: [{ label: "账户余额不足拒赔" }],
  },
  {
    label: "保单无责任",
    children: [
      { label: "就诊日期无对应保单" },
      { label: "提交案件无保单相关责任" },
    ],
  },
  {
    label: "非保单有效期",
    children: [{ label: "非保期内" }],
  },
  {
    label: "审核规则不清晰",
    children: [{ label: "审核规则待确认" }],
  },
  {
    label: "系统推送原因",
    children: [{ label: "校验不通过" }, { label: "推送保司失败" }],
  },
  {
    label: "外包回传",
    children: [{ label: "赔案回传信息错误" }],
  },
] as const;
</script>

<script setup lang="ts">
import ClaimAPI from "@/api/claim";
defineOptions({
  name: "ReviewRejectionDialog",
});

const emit = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  claimId: {
    type: String,
    default: "",
  },
});

const isNeedRefresh = ref<boolean>(false);

const onClose = () => {
  dialogVisible.value = false;
  emit("close", isNeedRefresh.value);
  isNeedRefresh.value = false;
};

const rejectType = ref<string>("");
const reason = ref<string>("");
const rejectLoading = ref<boolean>(false);

const handleConfirm = async () => {
  if (!rejectType.value || rejectType.value.length === 0) {
    ElMessage.warning("请选择错误类型");
    return;
  }

  if (!reason.value) {
    ElMessage.warning("请输入错误原因");
    return;
  }

  if (String(reason.value).length < 10) {
    ElMessage.warning("错误原因必须大于10个字");
    return;
  }

  try {
    rejectLoading.value = true;
    await ClaimAPI.rejectReviewClaim({
      claimId: props.claimId,
      rejectType: rejectType.value[rejectType.value.length - 1],
      reason: reason.value,
    });
    isNeedRefresh.value = true;
    ElMessage.success("复核驳回成功");
    onClose();
  } catch (error) {
    console.error(error);
  } finally {
    rejectLoading.value = false;
  }
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    title="复核驳回"
    width="30%"
    :close-on-click-modal="false"
  >
    <div class="p-2">
      <el-form label-width="auto" label-position="left">
        <el-form-item label="错误类型">
          <el-cascader
            v-model="rejectType"
            :options="RejectTypeList as unknown as CascaderOption[]"
            :props="{
              label: 'label',
              value: 'label',
              children: 'children',
            }"
            clearable
            style="width: 100%"
            :show-all-levels="false"
          />
        </el-form-item>
        <el-form-item label="错误原因">
          <el-input
            v-model="reason"
            :maxlength="100"
            :show-word-limit="true"
            :rows="3"
            type="textarea"
            placeholder="请输入错误原因，大于10个字"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="flex justify-center">
        <el-button @click="onClose">取消</el-button>
        <el-button
          type="primary"
          @click="handleConfirm"
          :loading="rejectLoading"
        >
          确定
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped></style>
