<script setup>
import { EventOwnerEnum } from "@/enums/event/EventOwnerEnum";
import { createProps } from "./form";
import { useScopeData } from "../../hooks/useScopeData";
import { useSubmitRule } from "../../hooks/useSubmitRule";
import { isEmpty } from "lodash-es";

defineOptions({
  name: "Form",
});

const scopeData = useScopeData();
const dataManager = scopeData.getData("dataManager");
const validateManager = scopeData.getData("validateManager");
const componentManager = scopeData.getData("componentManager");
const rulesManager = scopeData.getData("rulesManager");
const data = scopeData.getData("data");

const props = defineProps(createProps());

const submitRuleList = computed(() => rulesManager.get(2, "Form"));

/** 校验表单静态规则 */
const formRef = ref(null);
const validateFields = async () => {
  if (!formRef.value) return { success: true };
  try {
    await formRef.value.validate();
    return { success: true };
  } catch (error) {
    const values = Object.values(error).flat();
    const messages = values.map((item) => item.message);
    return { success: false, messages };
  }
};

/** 校验表单静态规则以及提交规则 */
const validate = () => {
  return new Promise(async (resolve) => {
    const formValidResult = await validateFields();

    if (isEmpty(submitRuleList.value)) {
      resolve(formValidResult);
      return;
    }

    const submitValidResult = useSubmitRule(
      submitRuleList.value,
      dataManager,
      componentManager
    );
    resolve({
      success: formValidResult.success && submitValidResult.success,
      messages: isEmpty(formValidResult.messages)
        ? submitValidResult.messages
        : [...formValidResult.messages, ...submitValidResult.messages],
    });
  });
};

validateManager.push(validate);
</script>

<template>
  <div
    class="w-full bg-[var(--el-bg-color-page)]"
    :class="{ 'pt-1': body.length > 0 }"
  >
    <el-form ref="formRef" :model="data" label-position="top">
      <div class="bg-[var(--el-bg-color)] mb-2 mx-2 rounded-lg">
        <div
          :class="
            isAffix ? 'sticky top-0 z-99 bg-[var(--el-bg-color)] shadow-lg' : ''
          "
        >
          <div
            class="border-b border-[var(--el-border-color-light)]"
            v-for="item in body?.slice(0, 2)"
            :key="item.id"
          >
            <component :is="item.type" v-bind="item" />
          </div>
        </div>
        <div
          class="not-last:border-b border-[var(--el-border-color-light)]"
          v-for="item in body?.slice(2)"
          :key="item.id"
        >
          <component :is="item.type" v-bind="item" />
        </div>
      </div>

      <div class="sticky bottom-0 z-10">
        <div
          class="flex-x-center w-full bg-[var(--el-bg-color)] border-t border-[var(--el-border-color-lighter)] py-3"
        >
          <PKButtonGroup
            :buttonList="eventTriggerList"
            :ownerId="id"
            :owner="EventOwnerEnum.Form"
          />
        </div>
      </div>
    </el-form>
  </div>
</template>

<style lang="scss" scoped></style>
