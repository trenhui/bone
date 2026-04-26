<script setup lang="ts">
import { EventTypeOptions } from "@/enums/event/EventTypeEnum";
import { TriggerOptions } from "@/enums/event/TriggerEnum";
import { EventLevelOptions } from "@/enums/event/EventLevelEnum";
import { EventOwnerOptions } from "@/enums/event/EventOwnerEnum";
import EventAPI from "@/api/event";
import { ElForm } from "element-plus";

const router = useRouter();

defineOptions({
  name: "CreateEvent",
});

const form = reactive({
  name: "",
  code: "",
  type: "",
  level: "",
  owner: "",
  triggerType: "",
  ruleDescription: "",
  remark: "",
});

const initForm = () => {
  form.name = "";
  form.code = "";
  form.type = "";
  form.level = "";
  form.owner = "";
  form.triggerType = "";
  form.ruleDescription = "";
  form.remark = "";
  formRef.value?.resetFields();
};

const rules = {
  name: [{ required: true, message: "请输入事件名称" }],
  code: [{ required: true, message: "请输入事件标识" }],
  type: [{ required: true, message: "请选择事件类型" }],
  level: [{ required: true, message: "请选择对象层级" }],
  owner: [{ required: true, message: "请选择对应位置" }],
  triggerType: [{ required: true, message: "请选择触发机制" }],
};

const handleCancel = () => {
  router.back();
};

const formRef = ref<InstanceType<typeof ElForm> | null>(null);
const handleSave = async () => {
  if (formRef.value) {
    const valid = await formRef.value.validate();
    if (!valid) return;
  }

  try {
    await EventAPI.create(form);
    initForm();
    ElMessage.success("创建成功");
  } catch (error) {
    console.error(error);
  }
};
</script>

<template>
  <div class="app-container">
    <div class="search-container">
      <div class="mb-5 ml-2 font-bold">创建事件</div>
    </div>
    <el-card class="px-12 py-5" shadow="never">
      <el-form ref="formRef" label-width="auto" :model="form" :rules="rules">
        <div class="flex gap-20">
          <el-form-item required prop="name" label="事件名称" class="w-1/3">
            <el-input v-model="form.name" />
          </el-form-item>
          <el-form-item required prop="code" label="事件标识" class="w-1/3">
            <el-input v-model="form.code" />
          </el-form-item>
        </div>

        <div class="flex gap-20">
          <el-form-item required prop="type" label="事件类型" class="w-1/3">
            <el-select v-model="form.type">
              <el-option
                v-for="item in EventTypeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item
            required
            prop="triggerType"
            label="触发机制"
            class="w-1/3"
          >
            <el-select v-model="form.triggerType">
              <el-option
                v-for="item in TriggerOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </div>

        <div class="flex gap-20">
          <el-form-item required prop="level" label="对象层级" class="w-1/3">
            <el-select v-model="form.level">
              <el-option
                v-for="item in EventLevelOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item required prop="owner" label="对应位置" class="w-1/3">
            <el-select v-model="form.owner">
              <el-option
                v-for="item in EventOwnerOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item prop="ruleDescription" label="规则说明" class="w-1/2">
          <el-input v-model="form.ruleDescription" :rows="3" type="textarea" />
        </el-form-item>
        <el-form-item prop="remark" label="事件备注" class="w-1/2">
          <el-input v-model="form.remark" :rows="3" type="textarea" />
        </el-form-item>
      </el-form>

      <div class="flex justify-center items-center gap-5 mt-10">
        <el-button class="w-30" type="default" @click="handleCancel">
          取消
        </el-button>
        <el-button class="w-30" type="primary" @click="handleSave">
          保存
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped></style>
