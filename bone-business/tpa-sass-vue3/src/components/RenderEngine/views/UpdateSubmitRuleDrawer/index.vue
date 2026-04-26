<script setup lang="ts">
import { getPageCodeChinese } from "@/enums/PageCodeEnum";
import { isEmpty } from "lodash-es";

const emits = defineEmits(["close"]);
const drawerVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  pageCode: string;
  bizIdentityCode?: string;
  rule: any;
}>();

const isChange = ref(false);
const currentRule = ref({});

watch(
  () => props.rule,
  (newVal) => {
    if (!isEmpty(newVal)) {
      currentRule.value = {
        ...newVal,
        fieldIdList: newVal.fieldList.map((item: any) => item.id),
        pageCode: props.pageCode,
        bizIdentityCode: props.bizIdentityCode,
      };
    }
  },
  { immediate: true }
);

const handleUpdateRule = () => {
  isChange.value = true;
  handleClose();
};

const handleClose = () => {
  emits("close", isChange.value);
};
</script>

<template>
  <div class="update-submit-rule-drawer">
    <el-drawer
      title="设置提交校验规则"
      size="40%"
      v-model="drawerVisible"
      :before-close="handleClose"
      destroy-on-close
    >
      <div class="pl-3 pr-5">
        <el-descriptions size="large">
          <el-descriptions-item label="流程事件">
            <strong>按钮-提交</strong>
          </el-descriptions-item>
          <el-descriptions-item label="适用页面">
            <strong>{{ getPageCodeChinese(rule.pageCode) }}</strong>
          </el-descriptions-item>
        </el-descriptions>

        <div class="text-sm c-[#606266]">
          在适用页面点击按钮“提交”事件时，不仅满足页面所有的业务字段规则和字段动态规则，同时还需要满足提交校验规则方可成功。
        </div>

        <div class="mt-8">
          <p class="pl-2 mb-6 border-l-2 border-l-[#1890ff]">修改规则</p>
          <div class="mt-5 mb-5">
            <SubmitRuleCard
              v-bind="{
                status: 'update',
                rule: currentRule,
              }"
              @update="handleUpdateRule"
            />
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped></style>
