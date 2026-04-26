<script setup lang="ts">
import { getPageCodeChinese, PageCodeEnum } from "@/enums/PageCodeEnum";
import { FunctionTypeEnum } from "@/enums/rule/FunctionTypeEnum";

const emits = defineEmits(["close"]);
const drawerVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  pageCode: string;
  bizIdentityCode?: string;
}>();

const isAdding = ref(false);
const newRuleForm = ref({});
const isChange = ref(false);

const initNewRuleForm = (functionType: FunctionTypeEnum | null = null) => {
  newRuleForm.value = {
    fieldIdList: [],
    functionType: functionType,
    functionName: "",
    operator: "",
    valueType: "",
    value: "",
    pageCode: props.pageCode,
    bizIdentityCode: props.bizIdentityCode,
  };
};

const handleAddRule = () => {
  isChange.value = true;
  handleClose();
};

const handelShowAdd = (functionType: FunctionTypeEnum) => {
  isAdding.value = !isAdding.value;
  initNewRuleForm(functionType);
};

const handleClose = () => {
  isAdding.value = false;
  initNewRuleForm();
  emits("close", isChange.value);
  isChange.value = false;
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
            <strong>{{ getPageCodeChinese(pageCode) }}</strong>
          </el-descriptions-item>
        </el-descriptions>

        <div class="text-sm c-[#606266]">
          在适用页面点击按钮“提交”事件时，不仅满足页面所有的业务字段规则和字段动态规则，同时还需要满足提交校验规则方可成功。
        </div>

        <div class="mt-8">
          <p class="pl-2 mb-6 border-l-2 border-l-[#1890ff]">添加规则</p>
          <div class="flex justify-start items-center">
            <el-button
              type="primary"
              circle
              @click="handelShowAdd(FunctionTypeEnum.NUMBER)"
            >
              <template #icon>
                <i-ep-plus />
              </template>
            </el-button>
            <div class="ml-4 text-16px c-[#606266]">
              <span>
                <strong>数字函数类型：</strong>
              </span>
              <span>
                <strong>数字文本字段</strong>
                通过
                <strong>指定函数</strong>
                得出的
                <strong>值</strong>
                应当
                <strong>如何</strong>
              </span>
            </div>
          </div>

          <transition
            enter-active-class="animate__animated animate__fadeIn"
            mode="out-in"
          >
            <div class="mt-5 mb-5" v-if="isAdding === true">
              <SubmitRuleCard
                v-bind="{
                  status: 'add',
                  rule: newRuleForm,
                }"
                @add="handleAddRule"
              />
            </div>
          </transition>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped></style>
